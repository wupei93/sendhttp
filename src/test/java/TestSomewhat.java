import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

public class TestSomewhat {
    @Test
    public void testAtomicInteger(){
        AtomicInteger atomicInteger = new AtomicInteger(1);
        System.out.println(atomicInteger);
        System.out.println(atomicInteger.getAndAdd(1));
        System.out.println(atomicInteger);
    }


    @Test
    public void testSystemOut() {
 /*       try {
            FileOutputStream fos = new FileOutputStream("C:\\Users\\wup10\\Desktop\\fos.txt");
            PrintStream ps = new PrintStream(fos);
            ps.printf("hhah%s", "22");
            int i = 1/0;
            ps.println("hhah123");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }*/

    }

    @Test
    public void testHexString(){
        int major = Integer.parseInt("00001c", 16);
        System.out.println(major);
        System.out.println(String.format("%016x",major));
    }

    @Test
    public void testFinally(){
        ReentrantLock lock = new ReentrantLock();
        Random random = new Random(5);
        ExecutorService executor = Executors.newFixedThreadPool(20);
        List<Future> futures = new ArrayList<>();
        long start = System.currentTimeMillis();
        for(int i = 0; i < 10; i++){
            futures.add(executor.submit(() ->{
                while(true){
                    boolean locked = false;
                    try {
                        locked = lock.tryLock(5, TimeUnit.MILLISECONDS);
                        if(locked){
                            Thread.sleep(20);
                            break;
                        } else {
                            TimeUnit.NANOSECONDS.sleep(random.nextInt(100000));
                        }
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    } finally{
                        if(locked){
                            lock.unlock();
                        }
                    }
                }
            }));
        }
        for (Future future : futures) {
            try {
                future.get();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (ExecutionException e) {
                e.printStackTrace();
            }
        }
        System.out.println(System.currentTimeMillis()-start);
    }

    @Test
    public void testFinally2(){
        ExecutorService executor = Executors.newFixedThreadPool(20);
        List<Future> futures = new ArrayList<>();
        long start = System.currentTimeMillis();
        for(int i = 0; i < 10; i++){
            futures.add(executor.submit(() ->{
                synchronized(this) {
                    try {
                        Thread.sleep(20);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }));
        }
        for (Future future : futures) {
            try {
                future.get();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (ExecutionException e) {
                e.printStackTrace();
            }
        }
        System.out.println(System.currentTimeMillis()-start);
    }
}
