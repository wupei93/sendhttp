package textProcess;

import common.FileUtils;
import common.PerformanceCounter;
import common.UrlBuilder;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static textProcess.ObjectEntriesFetcher.TargetType.CHUNK_ID;
import static textProcess.ObjectEntriesFetcher.TargetType.OBJECT_ID;

/**
 * 该工具类用来从journalcontent中抓取指定object的update
 */
public class ObjectEntriesFetcher {

    private final static String configFile = "ObjectEntriessFetcherProperties.properties";
    private final static Properties properties = new Properties();
    static {
        try {
            properties.load(new InputStreamReader(new FileInputStream(
                    System.getProperty("user.dir") + File.separator + configFile), "utf-8"));
        } catch (Exception e) {
            try {
                properties.load(new InputStreamReader(ClassLoader.getSystemResourceAsStream(configFile), "utf-8"));
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    private final static SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private final static String limit = "5";
    /*private final static boolean onlyGrepSchemaKey = Boolean.parseBoolean(properties.getProperty("onlyGrepSchemaKey"));
    private static String baseUrl = properties.getProperty("baseUrl");
    private static int majorStart = Integer.parseInt(properties.getProperty("major_start"), 16);
    private static int majorEnd = Integer.parseInt(properties.getProperty("major_end"), 16);*/
    private static long startTimestamp;
    private static long stopTimestamp;
    private static String host = properties.getProperty("host").trim();
    private static int majorStart = -1;
    private static int majorEnd = -1;
    private static String baseUrl;
    private final static String targets = properties.getProperty("targets").trim();
    private static CloseableHttpClient client = HttpClients.createDefault();
    private static RequestConfig requestConfig = RequestConfig.custom().setConnectTimeout(300000).setRedirectsEnabled(true)
            .setConnectionRequestTimeout(300000).setSocketTimeout(300000).build();
    private static String outputDir = properties.getProperty("outputDir").trim();
    private static ExecutorService requestPool = Executors.newFixedThreadPool(
            Integer.parseInt(properties.getProperty("threadPoolSize").trim()));
    private static ExecutorService parserPool = Executors.newCachedThreadPool();
    private static AtomicInteger currentTaskNum = new AtomicInteger(0);
    private static TreeSet<String> tempFileSet = new TreeSet(new FileComparator());
    private static final Object fileSetLock = new Object();

    private static PerformanceCounter requetCounter = new PerformanceCounter("requetCounter");
    private static PerformanceCounter saveOriginCounter = new PerformanceCounter("saveOriginCounter");
    private static PerformanceCounter parseCounter = new PerformanceCounter("paseCounter");
    private static PerformanceCounter mergeCounter = new PerformanceCounter("mergeCounter");

    private static String urlPrefix = "http://" + host + ":9101/";
    private static String urlSuffix = "";

    public static void main(String[] args) throws Exception{
        sdf.setTimeZone(TimeZone.getTimeZone("GMT+0:00"));
        long start = System.currentTimeMillis();
        System.out.println("===========================");
        System.out.printf("host:%s\n", host);
        prepare();
        if(majorStart == -1 || majorEnd == -1){
            System.out.println("Finished: can't find JOURNAL_REGION from " + properties.getProperty("startTime") + " to " + properties.getProperty("stopTime"));
            return;
        }
        System.out.printf("majorStart:%s\n", majorStart);
        System.out.printf("majorEnd:%s\n", majorEnd);
        System.out.printf("baseUrl:%s\n", baseUrl);
        System.out.println("===========================");
        for (int major = majorStart; major <= majorEnd; major++) {
            requestPool.execute(new RequestTask(major, "0-0"));
        }
        while(currentTaskNum.get() > 0){
            Thread.sleep(3000);
        }
        requestPool.shutdown();
        parserPool.shutdown();
        PerformanceCounter.StartTime beforeMerge = PerformanceCounter.start();
        mergeFile();
        mergeCounter.count(beforeMerge);
        PerformanceCounter.printAll();
        System.out.println("Finished with totalTime:" + (System.currentTimeMillis() - start) + "ms");
    }

    static void prepare() throws Exception {
        //prepare outputDir
        File file = new File(outputDir);
        boolean prepare = true;
        if (file.isDirectory()) {
            String newDirName = outputDir + System.currentTimeMillis();
            prepare &= file.renameTo(new File(newDirName));
            if (prepare) {
                System.out.println("rename existing dir " + outputDir + " to " + newDirName);
            } else {
                throw new IOException("Unable rename existing dir " + outputDir);
            }
        }
        prepare &= file.mkdir();
        prepare &= new File(outputDir + File.separator + "origin").mkdir();
        if (!prepare) {
            throw new RuntimeException("Unable to prepare dir" + outputDir);
        }

        // prepare param
        startTimestamp = sdf.parse(properties.getProperty("startTime").trim()).getTime();
        stopTimestamp = sdf.parse(properties.getProperty("stopTime").trim()).getTime();

        HttpGet httpGet = new HttpGet(urlPrefix);
        httpGet.setConfig(requestConfig);
        boolean needProxy = client.execute(httpGet, rep -> {
            try{
                if (rep.getStatusLine().getStatusCode() >= 300) {
                    return true;
                }
                return false;
            } catch(Exception e){
                return true;
            }
        });
        if(needProxy){
            urlPrefix = "http://10.247.99.224:8881/proxy/";
            urlSuffix = "&host="+host;
        }

        String target = targets.split(",")[0].trim();

        // get dtId
        String dtId = properties.getProperty("dtId");
        if(StringUtils.isEmpty(dtId)){
            TargetType targetType = OBJECT_ID;
            if(target.contains("-")){
                targetType = CHUNK_ID;
            }
            if(targetType.equals(CHUNK_ID)){
                httpGet = buildHttpGet("diagnostic/RR/0/DumpAllKeys/REPO_REFERENCE?showvalue=gpb&chunkId=" + target);
            } else {
                httpGet = buildHttpGet("diagnostic/OB/0/DumpAllKeys/OBJECT_TABLE_KEY?showvalue=gpb&objectId=" + target);
            }
            String url = findUrl(httpGet);
            if(url == null){
                return;
            }
            dtId = url.split(urlPrefix.split(":")[2])[1].split("/")[0];
        }
        dtId = dtId.trim();

        // get zone
        httpGet = buildHttpGet("diagnostic/getVdcFromNode?nodeId=" + host);
        String zone =  client.execute(httpGet, rep -> {
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(rep.getEntity().getContent()));
            return bufferedReader.readLine();
        });
        // get journal region major
        httpGet = buildHttpGet("diagnostic/PR/1/DumpAllKeys/DIRECTORYTABLE_RECORD?showvalue=gpb&type=JOURNAL_REGION&dtId=" + dtId + "&zone=" + zone);
        String urlContent = findUrl(httpGet).split(urlPrefix.split(":")[2])[1].split("\">")[0];
        httpGet = new HttpGet("http://10.247.99.224:8881/proxy/" + urlContent + "&host=" + host);
        httpGet.setConfig(requestConfig);
        httpGet = buildHttpGet(urlContent);
        client.execute(httpGet, new ResponseHandler(){
            @Override
            public Object handleResponse(HttpResponse response) throws ClientProtocolException, IOException {
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
                String line;
                String preMajor = null;
                while((line=bufferedReader.readLine()) != null){
                    if(line.contains("schemaType")){
                        preMajor = line.split(" major ")[1].split(" minor ")[0];
                    } else if(line.contains("timestamp")){
                        long timestamp = Long.parseLong(line.split("timestamp: ")[1]);
                        if(timestamp < startTimestamp || (majorStart == -1 && timestamp < stopTimestamp)){
                            majorStart = Integer.parseInt(preMajor, 16);
                        } else if(timestamp > startTimestamp){
                            majorEnd = Integer.parseInt(preMajor, 16);
                        }
                        if(timestamp > stopTimestamp){
                            break;
                        }
                    }
                }
                return null;
            }
        });

        // get baseUrl
        baseUrl = urlPrefix + "journalcontent/"+dtId+"?zone="+zone+"&parseTimestamp=true&filterStrs="+targets+urlSuffix;
    }

    enum TargetType{
        CHUNK_ID, OBJECT_ID
    }

    static String findUrl(HttpGet httpGet) throws IOException {
        String url = client.execute(httpGet, rep -> {
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(rep.getEntity().getContent()));
            String line;
            String preLine = null;
            while((line=bufferedReader.readLine()) != null){
                if(line.contains("schemaType")){
                    return preLine;
                }
                preLine = line;
            }
            return null;
        });
        if(url == null){
            System.out.println("can't find target for url:" + httpGet.getURI());
        }
        return url;
    }

    static HttpGet buildHttpGet(String urlContent) {
        HttpGet httpGet = new HttpGet(urlPrefix + urlContent + urlSuffix);
        httpGet.setConfig(requestConfig);
        return httpGet;
    }

    static void mergeFile() throws IOException {
        String targetLog = outputDir + File.separator + "target.log";
        FileChannel targetLogChannel = new FileOutputStream(targetLog).getChannel();
        try{
            targetLogChannel = new FileOutputStream(targetLog).getChannel();
            Iterator<String> iterator = tempFileSet.iterator();
            while(iterator.hasNext()) {
                String fileName = iterator.next();
                FileChannel originalTargetLogChannel = null;
                try{
                    File originalTargetLogFile = new File(outputDir + File.separator + "origin" + File.separator + fileName);
                    if(originalTargetLogFile.exists()){
                        originalTargetLogChannel = new FileInputStream(originalTargetLogFile).getChannel();
                        FileUtils.transferAll(originalTargetLogChannel, targetLogChannel);
                        System.out.println("merge " + originalTargetLogFile.getAbsolutePath() + " to target.log");
                        originalTargetLogFile.delete();
                    }
                } finally{
                    if(originalTargetLogChannel != null){
                        originalTargetLogChannel.force(false);
                        originalTargetLogChannel.close();
                    }
                }
            }
        } finally{
            targetLogChannel.close();
            new File(outputDir + File.separator + "origin").delete();
        }
    }

    static class RequestTask implements Runnable {
        private final String token;
        private final int major;

        RequestTask(int major, String token) {
            currentTaskNum.incrementAndGet();
            this.major = major;
            this.token = token;
        }

        @Override
        public void run() {
            boolean success = false;
            PerformanceCounter.StartTime beforeRequest =
                    PerformanceCounter.start();
            String url = UrlBuilder.getBuilder(baseUrl)
                    .appendParam("major", String.format("%016x", major))
                    .appendParam("limit", limit + "")
                    .appendParam("token", token)
                    .build();
            HttpGet httpGet = new HttpGet(url);
            httpGet.setConfig(requestConfig);
            try {
                while(!success){
                    success = client.execute(httpGet, rep -> {
                        System.out.println(url);
                        requetCounter.count(beforeRequest);
                        if (rep.getStatusLine().getStatusCode() >= 300) {
                            System.out.println(rep.getStatusLine().getStatusCode() + " - Error url:" + url);
                        } else {
                            ReadableByteChannel readableChannel = null;
                            FileChannel originChannal = null;
                            try {
                                PerformanceCounter.StartTime beforeSave =
                                        PerformanceCounter.start();
                                readableChannel = Channels.newChannel(rep.getEntity().getContent());
                                String fileName = major + "-" + token;
                                synchronized(fileSetLock){
                                    tempFileSet.add(fileName);
                                }
                                String originFile = outputDir + File.separator + "origin" + File.separator + fileName;
                                originChannal = new FileOutputStream(originFile, true).getChannel();
                                ByteBuffer buffer = ByteBuffer.allocateDirect(10240);
                                while (readableChannel.read(buffer) != -1) {
                                    buffer.flip();
                                    originChannal.write(buffer);
                                    buffer.clear();
                                }
                                originChannal.force(false);
                                saveOriginCounter.count(beforeSave);
                                BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(originFile)));
                                String line = reader.readLine();
                                if (line.contains("token to use for next set of entries: ")) {
                                    String nextToken = line.split("<")[0].split(": ")[1];
                                    requestPool.execute(new RequestTask(major, nextToken));
                                }
                                reader.close();
                                /*FileOutputStream fos = new FileOutputStream(outputDir + File.separator + fileName);
                                PrintStream ps = new PrintStream(fos);
                                parserPool.execute(new ParseTask(reader, ps));*/
                            } catch (FileNotFoundException e) {
                                e.printStackTrace();
                            } finally {
                                if(readableChannel != null){
                                    readableChannel.close();
                                }
                                if(originChannal != null){
                                    originChannal.close();
                                }
                            }
                        }
                        return true;
                    });
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally{
                currentTaskNum.decrementAndGet();
            }
        }
    }

   /* public static class ParseTask implements Runnable {
        private BufferedReader reader;
        private PrintStream ps;

        public ParseTask(BufferedReader reader, PrintStream ps) {
            currentTaskNum.incrementAndGet();
            this.reader = reader;
            this.ps = ps;
        }

        @Override
        public void run() {
            PerformanceCounter.StartTime beforeParse =
                PerformanceCounter.start();
            try {
                StAXXmlParser.parse(reader, eventReader -> {
                    boolean needRecord = false;
                    String currentQName = "";
                    StringBuilder sb = new StringBuilder();
                    while (eventReader.hasNext()) {
                        // 获得事件
                        XMLEvent event = null;
                        try {
                            event = eventReader.nextEvent();
                        } catch (XMLStreamException e) {
                            e.printStackTrace();
                        }
                        switch (event.getEventType()) {
                            // 解析事件的类型
                            case XMLStreamConstants.START_ELEMENT:
                                StartElement startElement = event.asStartElement();
                                currentQName = startElement.getName().getLocalPart();
                                if ("writeType".equals(currentQName)) {
                                    if (needRecord) {
                                        ps.print(sb.toString());
                                    }
                                    needRecord = false;
                                    sb = new StringBuilder();
                                }
                                sb.append("<");
                                sb.append(currentQName);
                                sb.append(">");
                                break;
                            case XMLStreamConstants.CHARACTERS:
                                Characters characters = event.asCharacters();
                                String text = characters.getData();
                                sb.append(text);
                                if (!needRecord) {
                                    needRecord = (!onlyGrepSchemaKey || "schemaKey".equals(currentQName)) && !text.isEmpty();
                                    if (needRecord) {
                                        boolean hasTarget = false;
                                        for (String target : targets) {
                                            if (text.contains(target.trim())) {
                                                hasTarget = true;
                                                break;
                                            }
                                        }
                                        needRecord = hasTarget;
                                    }
                                }
                                break;
                            case XMLStreamConstants.END_ELEMENT:
                                sb.append("</");
                                sb.append(event.asEndElement().getName().getLocalPart());
                                sb.append(">");
                            default:
                                break;
                        }
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                ps.flush();
                ps.close();
                try {
                    reader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                parseCounter.count(beforeParse);
                currentTaskNum.decrementAndGet();
            }
        }
    }*/

    static class FileComparator implements Comparator<String> {

        @Override
        public int compare(String thisFile, String otherFile) {
            String[] thisFileNums = thisFile.split("-");
            String[] otherFileNums = otherFile.split("-");
            for(int i = 0; i < 3; i++){
                int thisNumber = Integer.parseInt(thisFileNums[i]);
                int otherNumber = Integer.parseInt(otherFileNums[i]);
                if(thisNumber != otherNumber){
                    return thisNumber - otherNumber;
                }
            }
            return 0;
        }
    }
}
