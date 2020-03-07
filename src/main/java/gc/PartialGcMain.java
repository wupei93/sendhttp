package gc;

import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClients;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 批量删除partial gc task
 */
public class PartialGcMain {

    static ExecutorService executorService = Executors.newFixedThreadPool(100);
    static HttpClient client = HttpClients.createDefault();

    public static void main(String[] args){
        removePartialGcTask();
        executorService.shutdown();
        while(!executorService.isTerminated()){
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private static void removePartialGcTask(){
        String path = "C:/wupei/partial-gc.txt";
        String baseUrl = "http://10.243.85.65:9101/gc/partialgctaskcomplex/remove/" +
                "urn:storageos:VirtualArray:a9c0e244-ba80-488e-b0c8-c55d1496106c/";
        try{
            BufferedReader reader = new BufferedReader(new FileReader(new File(path)));
            String line = null;
            while ((line = reader.readLine()) != null){
                final String url = baseUrl + line;
                executorService.execute(new Runnable() {
                    public void run() {
                        try {
                            client.execute(new HttpGet(url));
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                });
            }
        } catch (Exception e){
            e.printStackTrace();
        }
    }

}


