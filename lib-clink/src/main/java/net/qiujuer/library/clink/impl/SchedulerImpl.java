package net.qiujuer.library.clink.impl;

import net.qiujuer.library.clink.core.Scheduler;

import java.io.IOException;
import java.util.concurrent.*;

/**
 * @description:
 * @author: zxh
 * @create: 2019-11-13
 **/

public class SchedulerImpl implements Scheduler {
    private final ScheduledExecutorService scheduledExecutorService;
    private final ExecutorService deliveryPool;

    public SchedulerImpl(int poolSize) {
        this.scheduledExecutorService = Executors.newScheduledThreadPool(poolSize,
                new NameableThreadFactory("Scheduler-Thread-"));
        this.deliveryPool = Executors.newFixedThreadPool(4, new NameableThreadFactory("Delivery-Thread-"));
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable runnable, long delay, TimeUnit unit) {
        scheduledExecutorService.schedule(runnable, delay, unit);
        return null;
    }

    @Override
    public void delivery(Runnable runnable) {
        deliveryPool.execute(runnable);
    }

    @Override
    public void close() throws IOException {
        scheduledExecutorService.shutdownNow();
        deliveryPool.shutdownNow();
    }
}
