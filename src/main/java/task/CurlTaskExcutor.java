package task;

import common.Builder;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.CloseableHttpClient;

import java.io.*;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

public class CurlTaskExcutor implements TaskExcutor {

    private CloseableHttpClient client;
    private RequestConfig requestConfig;
    private String inputFilePath;
    // 保存异步执行结果
    private List<Future> futureList = new LinkedList<>();

    @Override
    public Queue start(ExecutorService executorService) {
        Queue<String> failLines = new LinkedList<>();
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(new File(inputFilePath)));
            int totalCount = 0;
            int fileLineCount = 0;
            String line;
            while ((line = reader.readLine()) != null || failLines.size() != 0 || futureList.size() != 0) {
                if(line != null){
                    fileLineCount++;
                } else {
                    // 所有任务执行完之后获取异步执行结果
                    Iterator<Future> iterator = futureList.iterator();
                    while (iterator.hasNext()) {
                        try {
                            // future.get()可以将线程池中的线程执行时保存的异常重新抛出
                            iterator.next().get();
                        } catch (Exception e){
                            e.printStackTrace();
                            // 获取执行失败的任务，保存
                            String errorLine = e.getMessage().split("执行失败:")[1];
                            failLines.add(errorLine);
                        } finally {
                            iterator.remove();
                        }
                    }
                }
                if(totalCount++ > fileLineCount * 2){
                    break;
                }
                if(totalCount % 100 == 0){
                    System.out.println("totalCount:" + totalCount);
                    Thread.sleep(1000);
                }
                if(line == null){
                    // 若现有任务执行完毕后有失败任务，则重新执行失败任务
                    if(failLines.size() == 0){
                        continue;
                    }
                    line = failLines.poll();
                }
                futureList.add(executorService.submit(new GenerateRepoUsage(line, client, requestConfig)));
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            try {
                reader.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        
        return failLines;
    }

    public static class CurlTaskExcutorBuilder implements Builder<CurlTaskExcutor>{

        private CloseableHttpClient client;
        private RequestConfig requestConfig;
        private String inputFilePath;

        @Override
        public CurlTaskExcutor build() {
            CurlTaskExcutor curlTaskExcutor = new CurlTaskExcutor();
            curlTaskExcutor.client = client;
            curlTaskExcutor.requestConfig = requestConfig;
            curlTaskExcutor.inputFilePath = inputFilePath;
            return curlTaskExcutor;
        }

        public CurlTaskExcutorBuilder client(CloseableHttpClient client) {
            this.client = client;
            return this;
        }

        public CurlTaskExcutorBuilder requestConfig(RequestConfig requestConfig){
            this.requestConfig = requestConfig;
            return this;
        }

        public CurlTaskExcutorBuilder inputFilePath(String inputFilePath){
            this.inputFilePath = inputFilePath;
            return this;
        }

    }
}
