package net.qiujuer.lesson.sample.client;

import net.qiujuer.lesson.sample.client.bean.ServerInfo;
import net.qiujuer.lesson.sample.foo.Foo;
import net.qiujuer.lesson.sample.foo.handle.ConnectorCloseHandlerChain;
import net.qiujuer.lesson.sample.foo.handle.ConnectorHandler;
import net.qiujuer.library.clink.core.Connector;
import net.qiujuer.library.clink.core.IoContext;
import net.qiujuer.library.clink.impl.IoSelectorProvider;
import net.qiujuer.library.clink.impl.IoStealingSelectorProvider;
import net.qiujuer.library.clink.impl.SchedulerImpl;
import net.qiujuer.library.clink.utils.CloseUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ClientTest {
    public static volatile boolean done = false;
    public static final int CLIENT_SIZE = 1200;
    public static final int SEND_THREAD_DELAY = 200;
    public static final int SEND_THREAD_SIZE = 4;


    public static void main(String[] args) throws IOException, InterruptedException {
        File cachePath = Foo.getCacheDir("client/test");
        ServerInfo info = UDPSearcher.searchServer(100000);
        System.out.println("Server:" + info);
        if (info == null) {
            return;
        }

        IoContext.setup()
                .ioProvider(new IoSelectorProvider())
                .ioProvider(new IoStealingSelectorProvider(2))
                .scheduler(new SchedulerImpl(1))
                .start();


        // 当前连接数量
        int size = 0;
        final List<TCPClient> tcpClients = new ArrayList<>(CLIENT_SIZE);

        final ConnectorCloseHandlerChain closeChain = new ConnectorCloseHandlerChain() {
            @Override
            protected boolean consume(ConnectorHandler connectorHandler, Connector connector) {
                tcpClients.remove(connectorHandler);
                if (tcpClients.size() == 0) {
                    CloseUtils.close(System.in);
                }
                return false;
            }
        };

        for (int i = 0; i < CLIENT_SIZE; i++) {
            try {
                TCPClient tcpClient = TCPClient.startWith(info, cachePath, false);
                if (tcpClient == null) {
                    throw new NullPointerException();
                }
                tcpClient.getCloseChain().appendLast(closeChain);
                tcpClients.add(tcpClient);
                System.out.println("连接成功：" + (++size));
            } catch (IOException | NullPointerException e) {
                System.out.println("连接异常");
                break;
            }

        }
//        Thread.sleep(10);

        System.in.read();

        Runnable runnable = () -> {
            int i = 3;
            while (!done) {
                TCPClient[] copyClients = tcpClients.toArray(new TCPClient[0]);
                for (TCPClient tcpClient : copyClients) {
                    tcpClient.send("Hello~~");
                }
                try {
                    Thread.sleep(SEND_THREAD_DELAY);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                i--;
            }
        };

        List<Thread> threads = new ArrayList<>(SEND_THREAD_SIZE);
        for (int i = 0; i < SEND_THREAD_SIZE; i++) {
            Thread thread = new Thread(runnable, "send_hello_thread-" + i);
            thread.start();
            threads.add(thread);
        }

        System.in.read();

        // 等待线程完成
        done = true;
        for (TCPClient tcpClient : tcpClients) {
            tcpClient.exit();
        }
//        thread.sleep(10000);
        // 客户端结束操作

        IoContext.close();

        for (Thread thread : threads) {
            thread.interrupt();
        }

    }


}
