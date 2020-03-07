package textProcess.task;


import common.*;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpRequestBase;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;

public class HttpRequestTask implements Callable {


    private HttpRequestBase httpRequestBase;

    public HttpRequestTask(HttpRequestBase httpRequestBase){
        this.httpRequestBase = httpRequestBase;
    }

    @Override
    public HttpResponse call() {
        return HttpRequestExcutor.excuteHttpRequest(httpRequestBase, res -> res);
    }
}
