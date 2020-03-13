import org.junit.Ignore;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

public class TestSomewhat {
    @Test
    public void testRemove(){
        Map<String, List<String>> candidates = new HashMap<>();
        List<String> lines = new ArrayList<String>();
        lines.add("hello");
        lines.add("world");
        candidates.put("key", lines);
        System.out.println(candidates.remove("key"));
    }

    @Test
    @Ignore
    public void testFuture(){
        ExecutorService executorService = Executors.newCachedThreadPool();
        List<Future> futureList = new ArrayList<>(10);
        CountDownLatch latch = new CountDownLatch(10);
        for(int i = 0; i < 10; i++){
            futureList.add(executorService.submit(()-> testVoid(latch)));
        }
        for (Future future : futureList) {
            try {
                future.get();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (ExecutionException e) {
                e.printStackTrace();
            }
        }
        System.out.println("all clear");
    }

    private void testVoid(CountDownLatch latch) {
        latch.countDown();
        try {
            latch.await();
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        System.out.println("finish one");
    }
}
