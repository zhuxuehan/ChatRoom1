package net.qiujuer.library.clink.core;

import java.io.Closeable;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * @description: 任务调度者
 * @author: zxh
 * @create: 2019-11-13
 **/

public interface Scheduler extends Closeable {
    /**
     * 延迟任务调度
     * @param runnable
     * @param delay
     * @param unit
     * @return
     */
    ScheduledFuture<?> schedule(Runnable runnable, long delay, TimeUnit unit);

    void delivery(Runnable runnable);
}
