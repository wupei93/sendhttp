package textProcess;

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
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 该工具类用来从journalcontent中抓取指定object的update
 */
public class ObjectEntriesFetcher {

    private final static String configFile = "ObjectEntriessFetcherProperties.properties";
    private final static Properties properties = new Properties();
    static{
        try {
            properties.load(new InputStreamReader(new FileInputStream(
                    System.getProperty("user.dir")+File.separator+configFile),"utf-8"));
        } catch (Exception e) {
            try {
                properties.load(new InputStreamReader(ClassLoader.getSystemResourceAsStream(configFile),"utf-8"));
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }
    private final static String limit = properties.getProperty("limit");
    /**
     * 根据journal_region和object的创建的时间确定起始major（remote zone上保留了所有journal）,
     * http://10.243.20.15:9101/diagnostic/PR/1/DumpAllKeys/DIRECTORYTABLE_RECORD?type=JOURNAL_REGION&
     * dtId=urn:storageos:OwnershipInfo:14b16fde-87fb-49af-9b32-b1e9b9cb4ac6_9f42ace9-30ba-455e-af4c-1b4c4ed86584_OB_53_128_0:&
     * zone=urn:storageos:VirtualDataCenterData:0bdc3dc6-4af9-4578-a89f-48b8e1e9d2bd&showvalue=gpb
     */
    private static int major = Integer.parseInt(properties.getProperty("major_start"), 16);
    /**
     * 允许major自增几次
     */
    private static int majorCount = Integer.parseInt(properties.getProperty("major_end"), 16) - major;
    private static String token = "0-0";
    private final static String target = properties.getProperty("target");
    private static String baseUrl = properties.getProperty("baseUrl");
    static CloseableHttpClient client = HttpClients.createDefault();
    static RequestConfig requestConfig = RequestConfig.custom().setConnectTimeout(300000).setRedirectsEnabled(true)
            .setConnectionRequestTimeout(300000).setSocketTimeout(300000).build();
    static private String outputDir = properties.getProperty("outputDir");
    static private ExecutorService executor = Executors.newCachedThreadPool();
    static private PerformanceCounter requetCounter = new PerformanceCounter("requetCounter");
    static private PerformanceCounter saveOriginCounter = new PerformanceCounter("saveOriginCounter");
    static private PerformanceCounter parseCounter = new PerformanceCounter("paseCounter");

    public static void main(String[] args) throws InterruptedException, IOException {
        System.out.println("===========================");
        System.out.printf("major:%s\n", major);
        System.out.printf("majorCount:%s\n", majorCount);
        System.out.printf("target:%s\n", target);
        System.out.println("===========================");
        File file = new File(outputDir);
        boolean prepare = true;
        if(file.isDirectory()){
            String newDirName = outputDir + System.currentTimeMillis();
            prepare &= file.renameTo(new File(newDirName));
            if(prepare){
                System.out.println("rename existing dir " + outputDir + " to " + newDirName);
            }else{
                throw new IOException("Unable rename existing dir " + outputDir);
            }
        }
        prepare &= file.mkdir();
        prepare &= new File(outputDir+File.separator+"origin").mkdir();
        if(!prepare){
            throw new RuntimeException("Unable to prepare dir" + outputDir);
        }
        // TODO 改为任务队列实现并发执行，将执行结果按major-token 来排序
        long start = System.currentTimeMillis();
        while(token != null){
            PerformanceCounter.StartTime beforeRequest =
                    PerformanceCounter.start();
            String url = UrlBuilder.getBuilder(baseUrl)
                    .appendParam("major", String.format("%016x",major))
                    .appendParam("limit", limit + "")
                    .appendParam("token", token)
                    .build();
            HttpGet httpGet = new HttpGet(url);
            httpGet.setConfig(requestConfig);
            try {
                client.execute(httpGet, rep -> {
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
                            String originFile = outputDir+File.separator+"origin"+File.separator
                                    +major+"-"+token+".log";
                            originChannal  = new FileOutputStream(originFile).getChannel();
                            originChannal.transferFrom(readableChannel, 0, Long.MAX_VALUE);
                            originChannal.force(true);
                            saveOriginCounter.count(beforeSave);
                            BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(originFile)));
                            String line = reader.readLine();
                            if (line.contains("token to use for next set of entries: ")) {
                                token = line.split("<")[0].split(": ")[1];
                            } else if (majorCount-- > 0) {
                                major++;
                                token = "0-0";
                            } else {
                                token = null;
                            }
                            FileOutputStream fos = new FileOutputStream(outputDir+File.separator+
                                    +major+"-"+token+".log");
                            PrintStream ps = new PrintStream(fos);
                            executor.execute(new ParseTask(reader, ps));
                        } catch (FileNotFoundException e) {
                            e.printStackTrace();
                        }finally{
                            readableChannel.close();
                            originChannal.close();
                        }
                    }
                    return null;
                });
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        System.out.println("total:" + (System.currentTimeMillis() - start));
        PerformanceCounter.printAll();
    }

    static class ParseTask implements Runnable {
        private BufferedReader reader;
        private PrintStream ps;

        ParseTask(BufferedReader reader, PrintStream ps) {
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
                                if("writeType".equals(currentQName)){
                                    if(needRecord){
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
                                if(!needRecord){
                                    needRecord = "schemaKey".equals(currentQName) &&
                                            !text.isEmpty() && text.contains(target);
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
            } catch(Exception e){
                e.printStackTrace();
            } finally{
                parseCounter.count(beforeParse);
            }
        }
    }
}
