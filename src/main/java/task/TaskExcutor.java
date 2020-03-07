package task;

import java.util.Queue;
import java.util.concurrent.ExecutorService;

public interface TaskExcutor {

    public Queue start(ExecutorService executorService);
}
