package net.qiujuer.lesson.sample.server;

import net.qiujuer.library.clink.utils.CloseUtils;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.concurrent.CountDownLatch;

/**
 * @description:
 * @author: zxh
 * @create: 2019-11-09
 **/

public class ServerAcceptor extends Thread {
    private boolean done = false;
    private Selector selector;
    private CountDownLatch latch = new CountDownLatch(1);
    private final AcceptListener listener;

    public ServerAcceptor(AcceptListener listener) throws IOException {
        super("server-accept-thread");
        this.selector = Selector.open();
        this.listener = listener;
    }

    boolean awaitRunning() {
        try {
            latch.await();
            return true;
        } catch (InterruptedException e) {
            return false;
        }
    }

    @Override
    public void run() {
        super.run();
        latch.countDown();

        Selector selector = this.selector;
        do {
            try {
                if (selector.select() == 0) {
                    if (done) {
                        break;
                    }
                    continue;
                }

                Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();
                while (iterator.hasNext()) {
                    if (done) {
                        break;
                    }

                    SelectionKey key = iterator.next();
                    iterator.remove();

                    // 检查当前Key的状态是否是我们关注的
                    // 客户端到达状态
                    if (key.isAcceptable()) {
                        ServerSocketChannel serverSocketChannel = (ServerSocketChannel) key.channel();
                        // 非阻塞状态拿到客户端连接
                        SocketChannel socketChannel = serverSocketChannel.accept();
                        listener.onNewSocketArrived(socketChannel);

                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

        } while (!done);

        System.out.println("ServerAcceptor Finished！");

    }

    void exit() {
        done = true;
        // 唤醒当前的阻塞
        CloseUtils.close(selector);
    }

    Selector getSelector() {
        return selector;
    }

    interface AcceptListener {
        void onNewSocketArrived(SocketChannel channel);
    }
}
