package common;

import com.google.common.base.Preconditions;
import org.apache.commons.lang3.StringUtils;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class PerformanceCounter {
    static final ConcurrentHashMap<String, PerformanceCounter> counterMap = new ConcurrentHashMap<>();
    private AtomicLong total = new AtomicLong(0);
    private AtomicLong count = new AtomicLong(0);
    private AtomicLong min = new AtomicLong(Long.MAX_VALUE);
    private AtomicLong max = new AtomicLong(0);
    private String name;

    public PerformanceCounter(String name) {
        Preconditions.checkArgument(StringUtils.isNotBlank(name), "name can't be blank");
        this.name = name;
        if(null != counterMap.putIfAbsent(name,this)){
         throw new RuntimeException("Counter name has existed");
        }
    }

    public void count(StartTime start){
        long delta = System.currentTimeMillis() - start.start;
        total.getAndAdd(delta);
        count.incrementAndGet();
        min.updateAndGet(pre -> Math.min(pre, delta));
        max.updateAndGet(pre -> Math.max(pre, delta));
    }

    public static void printAll(){
        for(PerformanceCounter performanceCounter : counterMap.values()){
            performanceCounter.print();
        }
    }

    public synchronized void print(){
        System.out.printf("%s [avg:%sms, min:%sms, max:%sms, total:%sms, count:%s]\n",
                name, count.get() == 0 ? 0 : total.get()/count.get(),
                min.get(), max.get(), total.get(), count.get());
    }

    public static StartTime start(){
        return new StartTime();
    }

    public static class StartTime {
        long start;
        StartTime(){
            start = System.currentTimeMillis();
        }
    }
}
