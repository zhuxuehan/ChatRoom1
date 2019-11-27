package net.qiujuer.library.clink.impl.stealing;

import java.util.Arrays;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.IntFunction;

/**
 * @description: 窃取调度服务
 * @author: zxh
 * @create: 2019-11-24
 **/

public class StealingService {
    //  当任务队列数量地域安全值不可窃取
    private final int minSafetyThreashold;
    //线程集合
    private final StealingSelectorThread[] threads;
    //对应的任务队列
    private final LinkedBlockingQueue<IoTask>[] queues;
    //结束标志
    private volatile boolean isTerminated = false;

    public StealingService(StealingSelectorThread[] threads, int minSafetyThreashold) {
        this.minSafetyThreashold = minSafetyThreashold;
        this.threads = threads;
        this.queues = Arrays.stream(threads)
                .map(StealingSelectorThread::getReadyTaskQueue)
                .toArray((IntFunction<LinkedBlockingQueue<IoTask>[]>) LinkedBlockingQueue[]::new);
    }

    //窃取一个任务，排除自己，从他人队列窃取一个任务
    IoTask steal(final LinkedBlockingQueue<IoTask> excludeQueue) {
        final int minSafetyThreashold = this.minSafetyThreashold;
        final LinkedBlockingQueue<IoTask>[] queues = this.queues;
        for (LinkedBlockingQueue<IoTask> queue : queues) {
            if (queue == excludeQueue) {
                continue;
            }
            int size = queue.size();
            if (size > minSafetyThreashold) {
                IoTask poll = queue.poll();
                if (poll != null) {
                    return poll;
                }
            }
        }
        return null;
    }

    //获取一个不繁忙的线程
    public StealingSelectorThread getNotBusyThread() {
        StealingSelectorThread targetThread = null;
        long targetKeyCount = Long.MAX_VALUE;
        for (StealingSelectorThread thread : threads) {
            //饱和度 这个一开始 想法是 谁注册的少注册谁,但是注册的多的不一定是繁忙的
            long registerKeyCount = thread.getSaturatingCapacity();
            if (registerKeyCount != -1 && registerKeyCount < targetKeyCount) {
                targetKeyCount = registerKeyCount;
                targetThread = thread;
            }
        }
        return targetThread;
    }

    //shutdown
    public void shutDown() {
        if (isTerminated) {
            return;
        }
        isTerminated = false;
        for (StealingSelectorThread thread : threads) {
            thread.exit();
        }
    }

    //是否已结束
    public boolean isTerminated() {
        return isTerminated;
    }

    //执行一个任务
    public void execute(IoTask task) {

    }

}
