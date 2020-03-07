import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import task.CurlTaskExcutor;
import task.TaskExcutor;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


/**
 * 使用线程池执行http请求任务
 */
public class Main {

    static ExecutorService executorService = Executors.newFixedThreadPool(10);
    static CloseableHttpClient client = HttpClients.createDefault();
    static RequestConfig requestConfig = RequestConfig.custom().setConnectTimeout(5000)
            .setConnectionRequestTimeout(5000).setSocketTimeout(5000).build();

    public static void main(String[] args) throws IOException {
        // 创建任务执行器,指定文件作为任务源
        TaskExcutor taskExcutor = new CurlTaskExcutor.CurlTaskExcutorBuilder()
                .client(client)
                .requestConfig(requestConfig)
                .inputFilePath("C:\\wupei\\chunks.txt")
                .build();
        // 执行任务, 并返回失败任务
        Queue<String> failLines = taskExcutor.start(executorService);
        // 尝试关闭线程池
        executorService.shutdown();
        while (!executorService.isTerminated()) {
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        // 线程池关闭后开始处理失败结果
        if(failLines.size() == 0){
            System.out.println("finished");
        } else {
            System.out.println("FailUrl size:" + failLines.size());
            FileWriter fileWriter = new FileWriter("C:/wupei/failChunk.txt");
            for(String line : failLines){
                fileWriter.write(line+"\n");
            }
            fileWriter.close();
        }
    }
}
