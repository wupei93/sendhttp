package textProcess;

import com.google.common.collect.Lists;
import common.*;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpRequestBase;

import java.io.BufferedReader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

public class CTAndReferenceInfo {
    static List<String> masterNode = Lists.newLinkedList(
                                        Arrays.asList("10.243.20.15",
                                                "10.243.20.55",
                                                "10.243.20.95"));

    static String chunkId = "107080f3-2c02-4901-ad69-d2cc76296643";

    public static void main(String[] args){
        final Map<HttpRequestBase, Future<HttpResponse>> ctResultMap = new HashMap<>();
        for(String ip : masterNode){
            String chunkQueryUrl = Urls.queryInAllTable(DtKey.CHUNK,ip,
                    new Pair<String,String>(ParamKey.CHUNK_ID, chunkId));
            HttpRequestBase request = HttpRequestFactory.newHttpRequest(HttpMethod.GET, chunkQueryUrl);
            //第一步dump all key,找到tableUrl
            HttpRequestExcutor.excuteHttpRequest(request, response -> {
                DtQueryResultHandler.findUsefulUrl(response).ifPresent(tableUrl -> {
                    String url = UrlBuilder.getBuilder(tableUrl).appendShowvalue().build();
                    //第二步请求tableUrl，dump chunkInfo
                    Pair<HttpRequestBase, Future<HttpResponse>> resPair =
                            HttpRequestExcutor.submitHttpRequest(HttpRequestFactory.newHttpRequest(HttpMethod.GET, url));
                    ctResultMap.put(resPair.getLeft(), resPair.getRight());
                });
                return null;
            });
        }

        for(Map.Entry<HttpRequestBase, Future<HttpResponse>> ctResult : ctResultMap.entrySet()){
            try {
                HttpResponse response = ctResult.getValue().get();
                if(DtQueryResultHandler.contains(response, "type: LOCAL")){ // 这里会抛异常，java.io.IOException: Attempted read from closed stream. 可能是response已经关闭了，或者重复消费了
                    String nodeIp = ctResult.getKey().getURI().getHost();
                    String rrQueryUrl = Urls.queryInAllTable(DtKey.REPO_REFERENCE, nodeIp,
                            new Pair<String,String>(ParamKey.CHUNK_ID, chunkId));
                    HttpRequestBase request = HttpRequestFactory.newHttpRequest(HttpMethod.GET, rrQueryUrl);
                    //第一步dump all key,找到tableUrl
                    HttpRequestExcutor.excuteHttpRequest(request, resp -> {
                        DtQueryResultHandler.findUsefulUrl(resp).ifPresent(tableUrl -> {
                            String url = UrlBuilder.getBuilder(tableUrl).build();
                            //第二步请求tableUrl，dump rr
                            HttpRequestExcutor.excuteHttpRequest(HttpRequestFactory.newHttpRequest(HttpMethod.GET, url),
                                    rrResp -> {
                                        BufferedReader reader = DtQueryResultHandler.handleErrorAndGetReader(rrResp);
                                        DtQueryResultHandler.print(rrResp);
                                        //todo 找到有问题的object id， 并查看它的cleanup job
                                        return null;
                                    }
                                );
                        });
                        return null;
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
