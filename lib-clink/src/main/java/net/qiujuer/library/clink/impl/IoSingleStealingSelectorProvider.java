package net.qiujuer.library.clink.impl;

import net.qiujuer.library.clink.core.IoProvider;
import net.qiujuer.library.clink.impl.stealing.IoTask;
import net.qiujuer.library.clink.impl.stealing.StealingSelectorThread;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

/**
 * 可窃取任务的IoProvider
 */
public class IoSingleStealingSelectorProvider implements IoProvider {
    private final StealingSelectorThread thread;

    public IoSingleStealingSelectorProvider(int poolSize) throws IOException {
        Selector selector = Selector.open();
        thread = new StealingSelectorThread(selector) {
            @Override
            protected boolean processTask(IoTask task) {
                task.providerCallback.run();
                return false;
            }
        };
        thread.start();
    }

    @Override
    public void close() {
        thread.exit();
    }

    @Override
    public boolean registerInput(SocketChannel channel, HandleProviderCallback callback) {
        return thread.register(channel, SelectionKey.OP_READ, callback);
    }

    @Override
    public boolean registerOutput(SocketChannel channel, HandleProviderCallback callback) {
        return thread.register(channel, SelectionKey.OP_WRITE, callback);
    }

    @Override
    public void unRegisterInput(SocketChannel channel) {
        thread.unregister(channel);
    }

    @Override
    public void unRegisterOutput(SocketChannel channel) {

    }

//    static class IoStealingThread extends StealingSelectorThread {
//        IoStealingThread(String name, Selector selector) {
//            super(selector);
//            setName(name);
//        }
//
//        @Override
//        protected boolean processTask(IoTask task) {
//            return task.onProcessIo();
//        }
//    }
}
