package common;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

public class HttpRequestFactory {

    static CloseableHttpClient client = HttpClients.createDefault();
    /**
     * 连接目标url的连接超时时间
     */
    private static final int defaultConnectTimeout = 2000;
    /**
     * 从连接池中获取连接的超时时间
     */
    private static final int defaultConnectRequestTimeout = 2000;
    /**
     * 默认设置为 连接超时时间2s、从连接池中获取连接的超时时间2s、等待响应超时时间60s
     */
    static RequestConfig defaultRequestConfig = RequestConfig.custom()
                                        .setConnectTimeout(defaultConnectTimeout)
                                        .setConnectionRequestTimeout(defaultConnectRequestTimeout)
                                        .setSocketTimeout(60000).build();

    public static HttpRequestBase newHttpRequest(HttpMethod httpMethod, String url){
        return newHttpRequest(httpMethod, url, -1);
    }

    public static HttpRequestBase newHttpRequest(HttpMethod httpMethod, String url, int timeout){
        HttpRequestBase httpRequestBase = httpMethod.newHttpRequestBase(url);
        if(timeout != -1){
            httpRequestBase.setConfig(RequestConfig.custom()
                    .setConnectTimeout(defaultConnectTimeout)
                    .setConnectionRequestTimeout(defaultConnectRequestTimeout)
                    .setSocketTimeout(timeout).build());
        } else {
            //使用默认超时配置，如果不配置超时时间容易死锁
            httpRequestBase.setConfig(defaultRequestConfig);
        }

        return httpRequestBase;
    }

    /**
     * 重新设置默认超时时间， 后面的请求可以使用默认配置，不用重新指定比较方便
     * @param connectTimeout 连接目标url的连接超时时间
     * @param ConnectRequestTimeout 从连接池中获取连接的超时时间
     * @param socketTimeout 等待响应超时时间
     */
    public static void resetDefaultTimeOut(int connectTimeout, int ConnectRequestTimeout, int socketTimeout){
        defaultRequestConfig = RequestConfig.custom()
                .setConnectTimeout(connectTimeout)
                .setConnectionRequestTimeout(ConnectRequestTimeout)
                .setSocketTimeout(socketTimeout).build();
    }
}
