package com.sojourners.chess.util;

import java.util.concurrent.*;

public class ExecutorsUtils {

    private static volatile ExecutorsUtils instance;

    private ExecutorService threadPoolExecutor;

    private ExecutorService threadPoolExecutorTwo;

    private ExecutorsUtils() {
        threadPoolExecutor = Executors.newSingleThreadExecutor();


        BlockingQueue<Runnable> workQueue = new ArrayBlockingQueue<>(1);
        threadPoolExecutorTwo = new ThreadPoolExecutor(1, 1,
                0L, TimeUnit.MILLISECONDS,
                workQueue,new ThreadPoolExecutor.DiscardOldestPolicy());
    }

    public static ExecutorsUtils getInstance() {
        if (instance == null) {
            synchronized (ExecutorsUtils.class) {
                if (instance == null) {
                    instance = new ExecutorsUtils();
                }
            }
        }
        return instance;
    }

    public void exec(Runnable task) {
        threadPoolExecutor.execute(task);
    }
    public void execTwo(Runnable task) {
        threadPoolExecutorTwo.execute(task);
    }

    public void close() {
        threadPoolExecutor.shutdownNow();
        threadPoolExecutorTwo.shutdownNow();
    }

}
