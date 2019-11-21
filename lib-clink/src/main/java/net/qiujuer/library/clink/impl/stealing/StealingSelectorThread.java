package net.qiujuer.library.clink.impl.stealing;

import net.qiujuer.library.clink.core.IoProvider;
import net.qiujuer.library.clink.utils.CloseUtils;

import java.io.IOException;
import java.nio.channels.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 可窃取任务线程
 */
public abstract class StealingSelectorThread extends Thread {
    //允许的操作
    private static final int VALID_OPS = SelectionKey.OP_READ | SelectionKey.OP_WRITE;
    private final Selector selector;
    // 是否处于运行中
    private volatile boolean running = true;
    //已就绪任务队列
    private final LinkedBlockingQueue<IoTask> readyTaskQueue = new LinkedBlockingQueue<>();
    //待注册任务队列
    private final LinkedBlockingQueue<IoTask> registerTaskQueue = new LinkedBlockingQueue<>();
    //单次就绪的任务缓存，随后一次性加入到就绪队列中
    private final List<IoTask> onceReadyTaskCache = new ArrayList<>(200);

    private final AtomicLong saturatingCapacity = new AtomicLong();

//    private volatile StealingService stealingService;

    protected StealingSelectorThread(Selector selector) {
        super("Stealing_Selector_Thread-");
        this.selector = selector;
    }

//    public void setStealingService(StealingService stealingService) {
//        this.stealingService = stealingService;
//    }

    //channel注册到selector
    public boolean register(SocketChannel channel, int ops, IoProvider.HandleProviderCallback callback) {
        if (channel.isOpen()) {
            IoTask ioTask = new IoTask(channel, ops, callback);
            registerTaskQueue.offer(ioTask);
            return true;
        }
        return false;
    }

    //取消注册,在队列中添加一份取消注册的任务,并将副本置空
    public void unregister(SocketChannel channel) {
        SelectionKey selectionKey = channel.keyFor(selector);
        if (selectionKey != null && selectionKey.attachment() != null) {
            //关闭前使用attach简单判断是否处于队列中
            selectionKey.attach(null);
            //添加取消操作
            IoTask ioTask = new IoTask(channel, 0, null);
            registerTaskQueue.offer(ioTask);
        }
    }

    //消费待注册通道
    private void consumeRegisterTodoTasks(final LinkedBlockingQueue<IoTask> registerTaskQueue) {
        final Selector selector = this.selector;

        IoTask registerTask = registerTaskQueue.poll();
        while (registerTask != null) {
            try {
                final SocketChannel channel = registerTask.channel;
                int ops = registerTask.ops;
                if (ops == 0) {
                    //unregister
                    SelectionKey key = channel.keyFor(selector);
                    if (key != null) {
                        key.cancel();
                    }
                } else if ((ops & ~VALID_OPS) == 0) {
                    //register
                    SelectionKey key = channel.keyFor(selector);
                    if (key != null) {
                        // Already registered other ops. just use | add interest event
                        key.interestOps(key.interestOps() | ops);
                    } else {
                        // Not registered. call register method add interest event
                        key = channel.register(selector, ops, new KeyAttachment());
                    }
                    Object attachment = key.attachment();
                    if (attachment != null) {
                        ((KeyAttachment) attachment).attach(ops, registerTask);
                    } else {
                        key.cancel();
                    }
                }
            } catch (ClosedChannelException
                    | CancelledKeyException
                    | ClosedSelectorException ignored) {
            } finally {
                registerTask = registerTaskQueue.poll();
            }
        }
    }

    //将单次就绪任务缓存加入总队列中
    private void joinTaskQueue(final LinkedBlockingQueue<IoTask> readyTaskQueue, final List<IoTask> onceReadyTaskCache) {
        //todo This can be optimized
        readyTaskQueue.addAll(onceReadyTaskCache);
    }

    private void consumeTodoTasks(final LinkedBlockingQueue<IoTask> readyTaskQueue, LinkedBlockingQueue<IoTask> registerTaskQueue) {
        IoTask task = readyTaskQueue.poll();
        while (task != null) {
            //做任务
            if (processTask(task)) {
                // 做完工作后添加待注册的列表
                registerTaskQueue.offer(task);
            }
            //下个任务
            task = readyTaskQueue.poll();
        }
    }

    @Override
    public final void run() {
        super.run();

        final Selector selector = this.selector;
        final LinkedBlockingQueue<IoTask> registerTaskQueue = this.registerTaskQueue;
        final LinkedBlockingQueue<IoTask> readyTaskQueue = this.readyTaskQueue;
        final List<IoTask> onceReadyTaskCache = this.onceReadyTaskCache;

        try {
            while (running) {
                //iterate registerTaskQueue to register interest event
                consumeRegisterTodoTasks(registerTaskQueue);

                //检查一次
                if ((selector.selectNow()) == 0) {
                    Thread.yield();
                    continue;
                }

                //处理已就绪通道
                Set<SelectionKey> selectionKeys = selector.selectedKeys();
                Iterator<SelectionKey> iterator = selectionKeys.iterator();

                //iterator ready task
                while (iterator.hasNext()) {
                    SelectionKey selectionKey = iterator.next();
                    Object attachment = selectionKey.attachment();
                    //check key & attachment
                    if (selectionKey.isValid() && attachment instanceof KeyAttachment) {
                        KeyAttachment keyAttachment = (KeyAttachment) attachment;
                        //key might be closed
                        try {
                            //ready event
                            final int readyOps = selectionKey.readyOps();
                            //interest event. ps:interest event maybe not ready
                            int interestOps = selectionKey.interestOps();
                            //if readable
                            if ((readyOps & SelectionKey.OP_READ) != 0) {
                                onceReadyTaskCache.add(keyAttachment.taskForReadable);
                                interestOps = interestOps & ~SelectionKey.OP_READ;
                            }

                            //if writable
                            if ((readyOps & SelectionKey.OP_WRITE) != 0) {
                                onceReadyTaskCache.add(keyAttachment.taskForWritable);
                                interestOps = interestOps & ~SelectionKey.OP_WRITE;
                            }
                            //uninterest ready ops
                            selectionKey.interestOps(interestOps);
                        } catch (CancelledKeyException ignored) {
                            // key is cancled,  remove task
                            onceReadyTaskCache.remove(keyAttachment.taskForReadable);
                            onceReadyTaskCache.remove(keyAttachment.taskForWritable);
                        }
                    }
                    iterator.remove();
                }

                //判断本次是否有dai待执行的任务
                if (!onceReadyTaskCache.isEmpty()) {
                    //加入到总队列中
                    joinTaskQueue(readyTaskQueue, onceReadyTaskCache);
                    onceReadyTaskCache.clear();
                }
                //消费总队列的任务
                consumeTodoTasks(readyTaskQueue, registerTaskQueue);
            }
        } catch (ClosedSelectorException ignored) {
        } catch (IOException e) {
            CloseUtils.close(selector);
        } finally {
            readyTaskQueue.clear();
            registerTaskQueue.clear();
            onceReadyTaskCache.clear();
        }
    }

    //线程退出操作
    public void exit() {
        running = false;
        CloseUtils.close(selector);
        interrupt();
    }

    //调用子类执行任务操作
    protected abstract boolean processTask(IoTask task);

    public LinkedBlockingQueue<IoTask> getReadyTaskQueue() {
        return readyTaskQueue;
    }

    public long getSaturatingCapacity() {
        if (selector.isOpen()) {
            return saturatingCapacity.get();
        } else {
            return -1;
        }
    }

    //用以注册时添加的附件
    static class KeyAttachment {
        //可读时执行的任务
        IoTask taskForReadable;
        //可写时执行的任务
        IoTask taskForWritable;

        //附加任务
        void attach(int ops, IoTask task) {
            if (SelectionKey.OP_READ == ops) {
                taskForReadable = task;
            } else {
                taskForWritable = task;
            }
        }
    }
}
