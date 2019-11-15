package net.qiujuer.library.clink.core.schedule;

import net.qiujuer.library.clink.core.Connector;
import net.qiujuer.library.clink.core.ScheduleJob;

import java.util.concurrent.TimeUnit;

/**
 * @description:
 * @author: zxh
 * @create: 2019-11-14
 **/

public class IdleTimeoutScheduleJob extends ScheduleJob {
    public IdleTimeoutScheduleJob(long idleTime, TimeUnit timeUnit, Connector connector) {
        super(idleTime, timeUnit, connector);
    }

    @Override
    public void run() {
        long lastActiveTime = connector.getLastActiveTime();
        long idleTimeoutMilliseconds = this.idleTimeoutMilliseconds;
        long nextDelay = idleTimeoutMilliseconds - (System.currentTimeMillis() - lastActiveTime);
        if (nextDelay <= 0) {
            schedule(idleTimeoutMilliseconds);
            try {
                connector.fireIdleTimeoutEvent();
            } catch (Throwable throwable) {
                connector.fireExceptionCaught(throwable);
            }
        } else {
            schedule(nextDelay);
        }
    }
}
