package textProcess;

import common.FileUtils;
import common.PerformanceCounter;
import common.StAXXmlParser;
import common.UrlBuilder;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Characters;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Properties;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

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

    private final static String limit = properties.getProperty("limit");
    private static int majorStart = Integer.parseInt(properties.getProperty("major_start"), 16);
    private static int majorEnd = Integer.parseInt(properties.getProperty("major_end"), 16);
    private final static String[] targets = properties.getProperty("targets").split(",");
    private static String baseUrl = properties.getProperty("baseUrl");
    private static CloseableHttpClient client = HttpClients.createDefault();
    private static RequestConfig requestConfig = RequestConfig.custom().setConnectTimeout(300000).setRedirectsEnabled(true)
            .setConnectionRequestTimeout(300000).setSocketTimeout(300000).build();
    private static String outputDir = properties.getProperty("outputDir");
    private static ExecutorService requestPool = Executors.newFixedThreadPool(
            Integer.parseInt(properties.getProperty("threadPoolSize")));
    private static ExecutorService parserPool = Executors.newCachedThreadPool();
    private static AtomicInteger currentTaskNum = new AtomicInteger(0);
    private static TreeSet<String> tempFileSet = new TreeSet(new FileComparator());
    private static final Object fileSetLock = new Object();

    private static PerformanceCounter requetCounter = new PerformanceCounter("requetCounter");
    private static PerformanceCounter saveOriginCounter = new PerformanceCounter("saveOriginCounter");
    private static PerformanceCounter parseCounter = new PerformanceCounter("paseCounter");
    private static PerformanceCounter mergeCounter = new PerformanceCounter("mergeCounter");

    public static void main(String[] args) throws Exception{
        long start = System.currentTimeMillis();
        prepare();
        for (int major = majorStart; major <= majorEnd; major++) {
            requestPool.execute(new RequestTask(major, "0-0"));
        }
        while(currentTaskNum.get() > 0){
            Thread.sleep(10000);
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
        System.out.println("===========================");
        System.out.printf("majorStart:%s\n", majorStart);
        System.out.printf("majorEnd:%s\n", majorEnd);
        System.out.printf("targets:%s\n", targets);
        System.out.println("===========================");
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
                    File originalTargetLogFile = new File(outputDir + File.separator + fileName);
                    if(originalTargetLogFile.exists()){
                        originalTargetLogChannel = new FileInputStream(originalTargetLogFile).getChannel();
                        FileUtils.transferAll(originalTargetLogChannel, targetLogChannel);
                        System.out.println("merge " + outputDir + File.separator + fileName + " to target.log");
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
                                FileOutputStream fos = new FileOutputStream(outputDir + File.separator + fileName);
                                PrintStream ps = new PrintStream(fos);
                                parserPool.execute(new ParseTask(reader, ps));
                            } catch (FileNotFoundException e) {
                                e.printStackTrace();
                            } finally {
                                readableChannel.close();
                                originChannal.close();
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

    static class ParseTask implements Runnable {
        private BufferedReader reader;
        private PrintStream ps;

        ParseTask(BufferedReader reader, PrintStream ps) {
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
                                    needRecord = "schemaKey".equals(currentQName) && !text.isEmpty();
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
    }

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
