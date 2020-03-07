package common;

import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class PerformanceCounterTest {
    PerformanceCounter counterShort = null;
    PerformanceCounter counterLong = null;

    @Before
    public void setUp() {
        counterShort = new PerformanceCounter("counterShort");
        counterLong = new PerformanceCounter("counterLong");
    }

    @Test
    public void count() throws Exception{
        PerformanceCounter.StartTime counterShortStartTime = counterShort.start();
        PerformanceCounter.StartTime counterLongStartTime = counterLong.start();
        TimeUnit.MILLISECONDS.sleep(1000);
        counterShort.count(counterShortStartTime);
        TimeUnit.MILLISECONDS.sleep(1020);
        counterLong.count(counterLongStartTime);
        counterShort.count(counterShortStartTime);
        counterShort.print();
        counterLong.print();
    }

    @Test
    public void printAll() {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        new Thread(() -> {
            try {
                countDownLatch.await();
                new PerformanceCounter("hh");
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
        for(PerformanceCounter performanceCounter : common.PerformanceCounter.counterMap.values()){
            performanceCounter.print();
            countDownLatch.countDown();
        }
    }

    @Test(expected = Exception.class)
    public void testNew(){
        new PerformanceCounter("\n");
    }

    @Test(expected = Exception.class)
    public void testNew2(){
        new PerformanceCounter("counterLong");
    }
}
