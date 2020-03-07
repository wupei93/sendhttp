package task;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.impl.client.CloseableHttpClient;
import java.util.concurrent.Callable;

public class GenerateRepoUsage implements Callable {

    private static String prefix = "http://10.247.99.221:9101/gc/repousage" +
            "/update/urn:storageos:VirtualArray:2144345a-3b9f-45c8-8770-9e19bd84d324/";


    private String chunk;
    private CloseableHttpClient client;
    private RequestConfig requestConfig;

    public GenerateRepoUsage(String chunk, CloseableHttpClient client, RequestConfig requestConfig) {
        this.chunk = chunk;
        this.client = client;
        this.requestConfig = requestConfig;
    }


    @Override
    public Object call() throws Exception {
        String url = prefix + chunk;
        try {
            HttpPut httpPut = new HttpPut(url);
            httpPut.setConfig(requestConfig);
            client.execute(httpPut, rep -> {
                if (rep.getStatusLine().getStatusCode() == 500) {
                    throw new RuntimeException();
                }
                return null;
            });
            return null;
        } catch (Exception e){
            System.out.println("Error Url:" + url);
            e.printStackTrace();
            throw new RuntimeException("执行失败:" + chunk);
        }
    }
}
