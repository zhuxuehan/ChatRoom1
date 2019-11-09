package net.qiujuer.lesson.sample.client;

import net.qiujuer.lesson.sample.client.bean.ServerInfo;
import net.qiujuer.lesson.sample.foo.Foo;
import net.qiujuer.library.clink.core.IoContext;
import net.qiujuer.library.clink.impl.IoSelectorProvider;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ClientTest {
    private static boolean done;

    public static void main(String[] args) throws IOException, InterruptedException {
        File cachePath = Foo.getCacheDir("client/test");

        IoContext.setup()
                .ioProvider(new IoSelectorProvider())
                .start();

        ServerInfo info = UDPSearcher.searchServer(100000);
        System.out.println("Server:" + info);
        if (info == null) {
            return;
        }

        // 当前连接数量
        int size = 0;
        final List<TCPClient> tcpClients = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            try {
                TCPClient tcpClient = TCPClient.startWith(info, cachePath);
                if (tcpClient == null) {
                    throw new NullPointerException();
                }

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
                for (TCPClient tcpClient : tcpClients) {
                    tcpClient.send("Hello~~");
                }
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                i--;
            }
        };

        Thread thread = new Thread(runnable);
        thread.start();

        System.in.read();

        // 等待线程完成
        done = true;
        try {
            thread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
//        thread.sleep(10000);
        // 客户端结束操作
//        for (TCPClient tcpClient : tcpClients) {
//            tcpClient.exit();
//        }
//        for (int i = 0; i < tcpClients.size() / 2; i++) {
//            tcpClients.get(i).exit();
//        }
        IoContext.close();

    }


}
