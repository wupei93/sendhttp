package gc;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import java.io.*;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.*;

public class GetChunkInfoById {

    static ExecutorService executorService = Executors.newFixedThreadPool(10);
    static CloseableHttpClient client = HttpClients.createDefault();
    static RequestConfig requestConfig = RequestConfig.custom().setConnectTimeout(5000)
            .setConnectionRequestTimeout(5000).setSocketTimeout(5000).build();
    static Queue<String> failChunks = new LinkedList<>();

    public static void main(String[] args) throws IOException {
        excute();
        executorService.shutdown();
        while (!executorService.isTerminated()) {
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        if(failChunks.size() == 0){
            System.out.println("finished");
        } else {
            System.out.println("FailUrl size:" + failChunks.size());
            FileWriter fileWriter = new FileWriter("C:/wupei/failChunk.txt");
            for(String url : failChunks){
                fileWriter.write(url+"\n");
            }
            fileWriter.close();
        }
    }

    private static void excute() throws IOException {
        String path = "C:/wupei/failChunk.txt";
        String baseUrl = "http://10.245.130.44:9101/diagnostic/1/ShowChunkInfo?cos=" +
                "urn:storageos:VirtualArray:c0c6a075-1772-4ec0-be36-1e31e8c263d2&chunkid=";

        BufferedReader reader = null;
        FileWriter errorChunkWriter = new FileWriter("C:/wupei/errorChunk.txt");
        try {
            reader = new BufferedReader(new FileReader(new File(path)));
            int count = 0;
            int totalCount = 0;
            String line;
            while ((line = reader.readLine()) != null || failChunks.size() != 0) {
                if(totalCount++ > 40000){
                    break;
                }
                if(count++ > 20000){
                    Thread.sleep(10000);
                    count -= 600;
                }
                if(count % 1000 == 0){
                    System.out.println("count:" + count);
                }
                String tempUrl = baseUrl + line;
                if(line == null){
                    tempUrl = failChunks.poll();
                }
                final String url = tempUrl;
                executorService.submit(() -> {
                        HttpGet httpGet = new HttpGet(url);
                        httpGet.setConfig(requestConfig);
                    try {
                        client.execute(httpGet, rep -> {
                            if(rep.getStatusLine().getStatusCode() == 500){
                                System.out.println("Error chunk:" + url);
                                errorChunkWriter.write(url);
                            }
                            return null;
                        });
                    } catch (IOException e) {
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException ex) {
                            ex.printStackTrace();
                        }
                        failChunks.offer(url);
                        System.out.println("FailUrl:" + url);
                    }
                });
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            reader.close();
            errorChunkWriter.close();
        }
    }

}


