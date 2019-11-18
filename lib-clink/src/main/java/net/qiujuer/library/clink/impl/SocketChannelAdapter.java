package net.qiujuer.library.clink.impl;

import net.qiujuer.library.clink.core.IoArgs;
import net.qiujuer.library.clink.core.IoProvider;
import net.qiujuer.library.clink.core.Receiver;
import net.qiujuer.library.clink.core.Sender;
import net.qiujuer.library.clink.utils.CloseUtils;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.concurrent.atomic.AtomicBoolean;

public class SocketChannelAdapter implements Sender, Receiver, Cloneable {
    private final AtomicBoolean isClosed = new AtomicBoolean(false);
    private final SocketChannel channel;
    private final IoProvider ioProvider;
    private final OnChannelStatusChangedListener listener;

    private IoArgs.IoArgsEventProcessor receiveIoEventProcessor;
    private IoArgs.IoArgsEventProcessor sendIoEventProcessor;

    private volatile long lastReadTime = System.currentTimeMillis();
    private volatile long lastWriteTime = System.currentTimeMillis();

    public SocketChannelAdapter(SocketChannel channel, IoProvider ioProvider,
                                OnChannelStatusChangedListener listener) throws IOException {
        this.channel = channel;
        this.ioProvider = ioProvider;
        this.listener = listener;

        channel.configureBlocking(false);
    }

    @Override
    public void setReceiveListener(IoArgs.IoArgsEventProcessor processor) {
        receiveIoEventProcessor = processor;
    }

    @Override
    public void setSendListener(IoArgs.IoArgsEventProcessor processor) {
        sendIoEventProcessor = processor;
    }

    @Override
    public boolean postReceiveAsync() throws IOException {
        if (isClosed.get()) {
            throw new IOException("Current channel is closed!");
        }
        //进行callback状态检查
        inputCallback.checkAttachNull();
        return ioProvider.registerInput(channel, inputCallback);
    }

    @Override
    public long getLastReadTime() {
        return lastReadTime;
    }


    @Override
    public boolean postSendAsync() throws IOException {
        if (isClosed.get()) {
            throw new IOException("Current channel is closed!");
        }
        outputCallback.checkAttachNull();
        // 当前发送的数据附加到回调中
//        return ioProvider.registerOutput(channel, outputCallback);
        outputCallback.run();
        return true;

    }

    @Override
    public long getLastWriteTime() {
        return lastWriteTime;
    }

    @Override
    public void close() {
        if (isClosed.compareAndSet(false, true)) {
            // 解除注册回调
            ioProvider.unRegisterInput(channel);
            ioProvider.unRegisterOutput(channel);
            // 关闭
            CloseUtils.close(channel);
            // 回调当前Channel已关闭
            listener.onChannelClosed(channel);
        }
    }

    private final IoProvider.HandleProviderCallback inputCallback = new IoProvider.HandleProviderCallback() {
        @Override
        protected void onProviderIo(IoArgs args) {
            if (isClosed.get()) {
                return;
            }

            lastReadTime = System.currentTimeMillis();

            IoArgs.IoArgsEventProcessor processor = receiveIoEventProcessor;
            if (processor == null) {
                return;
            }
            if (args == null) {
                args = processor.provideIoArgs();
            }

            try {
                if (args == null) {
                    processor.onConsumeFailed(new IOException("ProvideIoArgs is null."));
                } else {
                    int count = args.readFrom(channel);
                    if (count == 0) {
                        System.out.println("Current read zero data!");
                    }
                    if (args.remained() && args.isNeedConsumeRemaining()) {
                        //再次注册数据发送
                        attach = args;
                        ioProvider.registerInput(channel, this);
                    } else {
                        //完成发送
                        attach = null;
                        processor.onConsumeCompleted(args);
                    }
                }
            } catch (IOException ignored) {
                CloseUtils.close(SocketChannelAdapter.this);
            }
        }
    };


    private final IoProvider.HandleProviderCallback outputCallback = new IoProvider.HandleProviderCallback() {
        @Override
        protected void onProviderIo(IoArgs args) {
            if (isClosed.get()) {
                return;
            }

            lastWriteTime = System.currentTimeMillis();

            IoArgs.IoArgsEventProcessor processor = sendIoEventProcessor;
            if (processor == null) {
                return;
            }
            if (args == null) {
                //拿到一份新的args
                args = processor.provideIoArgs();
            }

            try {
                if (args == null) {
                    processor.onConsumeFailed(new IOException("ProvideIoArgs is null."));
                } else {
                    int count = args.writeTo(channel);
                    if (count == 0) {
                        System.out.println("Current write zero data!");
                    }
                    if (args.remained() && args.isNeedConsumeRemaining()) {
                        //再次注册数据发送
                        attach = args;
                        ioProvider.registerOutput(channel, this);
                    } else {
                        //完成发送
                        attach = null;
                        processor.onConsumeCompleted(args);
                    }
                }
            } catch (IOException ignored) {
                CloseUtils.close(SocketChannelAdapter.this);
            }
        }
    };


    public interface OnChannelStatusChangedListener {
        void onChannelClosed(SocketChannel channel);
    }
}
