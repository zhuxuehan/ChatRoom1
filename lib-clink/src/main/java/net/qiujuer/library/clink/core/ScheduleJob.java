package net.qiujuer.library.clink.core;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * @description:
 * @author: zxh
 * @create: 2019-11-14
 **/

public abstract class ScheduleJob implements Runnable {
    protected final long idleTimeoutMilliseconds;
    protected final Connector connector;

    private volatile Scheduler scheduler;
    private volatile ScheduledFuture<?> scheduledFuture;

    public ScheduleJob(long idleTime, TimeUnit timeUnit, Connector connector) {
        this.idleTimeoutMilliseconds = timeUnit.toMillis(idleTime);
        this.connector = connector;
    }

    synchronized void schedule(Scheduler scheduler) {
        this.scheduler = scheduler;
        schedule(idleTimeoutMilliseconds);
    }

    synchronized void unSchedule() {
        if (scheduler != null) {
            scheduler = null;
        }
        if (scheduledFuture != null) {
            scheduledFuture.cancel(true);
            scheduledFuture = null;
        }
    }

    synchronized protected void schedule(long timeoutMilliseconds) {
        if (scheduler != null) {
            scheduledFuture = scheduler.schedule(this, timeoutMilliseconds, TimeUnit.MILLISECONDS);
        }
    }
}
