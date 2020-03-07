package gc;

import com.sun.org.apache.xerces.internal.impl.xs.SchemaSymbols;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClients;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class GetTaskBySrcChunk {

    static ExecutorService executorService = Executors.newFixedThreadPool(10);
    static HttpClient client = HttpClients.createDefault();
    static List<Future<HttpResponse>> repList = new ArrayList<Future<HttpResponse>>();
    static BufferedOutputStream outputStream;

    public static void main(String[] args) throws FileNotFoundException {
        outputStream = new BufferedOutputStream(new FileOutputStream("C:/wupei/curlResult.txt"));
        getTaskId();
        for(Future<HttpResponse> future : repList){
            try {
                HttpResponse response = future.get();
                BufferedInputStream is = new BufferedInputStream(response.getEntity().getContent());
                int ri;
                while((ri = is.read()) != -1){
                    outputStream.write(ri);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (ExecutionException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        executorService.shutdown();
        while (!executorService.isTerminated()) {
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        System.out.println("finished");
    }

    private static void getTaskId() {
        String path = "C:/wupei/rs.txt";
        String baseTail = "?showvalue=gpb";
        try {
            BufferedReader reader = new BufferedReader(new FileReader(new File(path)));
            String line = null;
            while ((line = reader.readLine()) != null) {
                final String url = line + baseTail;
                repList.add(executorService.submit(new Callable<HttpResponse>() {
                    public HttpResponse call() {
                        try {
                            return client.execute(new HttpGet(url));
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        return null;
                    }
                }));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}


