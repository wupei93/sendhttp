package common;

import org.junit.Test;

import java.util.concurrent.TimeUnit;

public class PerformanceCounterTest {
    final PerformanceCounter counterShort = new PerformanceCounter("counterShort");
    final PerformanceCounter counterLong = new PerformanceCounter("counterLong");

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
}
