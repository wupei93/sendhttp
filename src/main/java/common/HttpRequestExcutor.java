package common;

import org.apache.http.HttpResponse;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import textProcess.task.HttpRequestTask;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class HttpRequestExcutor {
    private static CloseableHttpClient client = HttpClients.createDefault();
    private static ExecutorService executorService = Executors.newFixedThreadPool(10);

    public static <T> T excuteHttpRequest(HttpRequestBase httpRequestBase, ResponseHandler<? extends T> responseHandler){
        try {
            return client.execute(httpRequestBase, responseHandler);
        } catch (Exception e){
            System.out.println("Error Url:" + httpRequestBase.getURI().toString());
            e.printStackTrace();
        }
        return null;
    }


    public static Pair<HttpRequestBase, Future<HttpResponse>> submitHttpRequest(HttpRequestBase httpRequestBase) {
        Future<HttpResponse> future = executorService.submit(new HttpRequestTask(httpRequestBase));
        return new Pair<>(httpRequestBase, future);
    }
}
