package common;

import org.apache.http.HttpResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Optional;

public class DtQueryResultHandler {

    public static List<String> findReferenceObjectList(HttpResponse response) {
        BufferedReader bufferedReader = handleErrorAndGetReader(response);
        // todo 看哪些 positive rr 不能被 negative rr 抵消
        return null;
    }


    public static boolean contains(HttpResponse response, String queryStr){
        BufferedReader bufferedReader = handleErrorAndGetReader(response);
        try {
            bufferedReader.readLine();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return bufferedReader.lines()
                .anyMatch(line -> line.contains(queryStr));
    }

    public static Void print(HttpResponse response){
        BufferedReader bufferedReader = handleErrorAndGetReader(response);
        bufferedReader.lines().forEach(System.out::println);
        return null;
    }

    /**
     * 用于dump all key找到正确的table url
     *
     * 对于服务器返回重定向的情况
     * if(response.getStatusLine().getStatusCode() == 303){
     *    String url = response.getHeaders("Location")[0].getValue();
     * }
     * @return 返回找到的tableUrl的Optional， 可能是空值
     */
    public static Optional<String> findUsefulUrl(HttpResponse response){
        BufferedReader bufferedReader = handleErrorAndGetReader(response);
        String line;
        String preciousLine = null;
        try{
            while ((line = bufferedReader.readLine()) != null){
                 if(line.contains("schemaType ")){
                     break;
                 }
                 preciousLine = line;
            }
        } catch (Exception e){
            e.printStackTrace();
        }
        return HttpUtils.fetchHttpUrl(preciousLine);
    }

    /**
     * 本工具类的其他方法都应先调用此方法
     * @param response
     * @return
     */
    public static BufferedReader handleErrorAndGetReader(HttpResponse response){
        try{
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
            if(response.getStatusLine().getStatusCode() >= 400){
                System.out.println("Error:");
                bufferedReader.lines().forEach(System.out::println);
            }
            return bufferedReader;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
}
