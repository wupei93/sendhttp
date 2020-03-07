package common;

import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;

public enum HttpMethod {
    PUT, GET;

    public HttpRequestBase newHttpRequestBase(String url){
        HttpRequestBase requestBase = null;
        switch (this){
            case GET:
                requestBase = new HttpGet(url);
                break;
            case PUT:
                requestBase = new HttpPut(url);
                break;
            default:
                break;
        }
        return requestBase;
    }
}
