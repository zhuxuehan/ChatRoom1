package net.qiujuer.library.clink.impl.bridge;

import net.qiujuer.library.clink.core.*;
import net.qiujuer.library.clink.utils.plugin.CircularByteBuffer;

import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 桥接调度器实现
 * 当前调度器同时实现了发送者与接受者调度逻辑
 * 核心思想为：把接受者接收到的数据全部转发给发送者
 */
public class BridgeSocketDispatcher implements ReceiveDispatcher, SendDispatcher {
    private final CircularByteBuffer mBuffer = new CircularByteBuffer(512, true);
    private final ReadableByteChannel readableByteChannel = Channels.newChannel(mBuffer.getInputStream());
    private final WritableByteChannel writableByteChannel = Channels.newChannel(mBuffer.getOutputStream());


    private final IoArgs receiveIoArgs = new IoArgs(256, false);
    private final Receiver receiver;
    private final AtomicBoolean isSending = new AtomicBoolean();

    private final IoArgs sendIoArgs = new IoArgs();
    private volatile Sender sender;

    public BridgeSocketDispatcher(Receiver receiver) {
        this.receiver = receiver;
    }

    public void bindSender(Sender sender) {
        final Sender oldSender = this.sender;
        if (oldSender != null) {
            oldSender.setSendListener(null);
        }

        synchronized (isSending) {
            isSending.set(false);
        }
        mBuffer.clear();

        this.sender = sender;
        if (sender != null) {
            sender.setSendListener(senderEventProcessor);
            requestSend();
        }

    }

    /**
     * 外部初始化好了桥接调度器后需要调用start方法开始
     */
    @Override
    public void start() {
        // nothing
        receiver.setReceiveListener(receiverEventProcessor);
        registerReceive();
    }

    @Override
    public void stop() {
        // nothing
    }

    @Override
    public void send(SendPacket packet) {
        // nothing
    }

    @Override
    public void sendHeartbeat() {
        // nothing
    }

    @Override
    public void cancel(SendPacket packet) {
        // nothing
    }

    @Override
    public void close() {
        // nothing
    }

    private void requestSend() {
        synchronized (isSending) {
            Sender sender = this.sender;
            if (isSending.get() || sender == null) {
                return;
            }
            if (mBuffer.getAvailable() > 0) {
                try {
                    boolean succeed = sender.postSendAsync();
                    if (succeed) {
                        isSending.set(true);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void registerReceive() {
        try {
            receiver.postReceiveAsync();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private final IoArgs.IoArgsEventProcessor senderEventProcessor = new IoArgs.IoArgsEventProcessor() {
        @Override
        public IoArgs provideIoArgs() {
            try {
                int available = mBuffer.getAvailable();
                if (available > 0) {
                    sendIoArgs.limit(available);
                    sendIoArgs.startWriting();
                    sendIoArgs.readFrom(readableByteChannel);
                    sendIoArgs.finishWriting();
                    return sendIoArgs;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            return null;
        }

        @Override
        public void onConsumeFailed(Exception e) {
            e.printStackTrace();
            synchronized (isSending) {
                isSending.set(false);
            }
            requestSend();
        }

        @Override
        public void onConsumeCompleted(IoArgs ioArgs) {
            synchronized (isSending) {
                isSending.set(false);
            }
            requestSend();
        }
    };

    private final IoArgs.IoArgsEventProcessor receiverEventProcessor = new IoArgs.IoArgsEventProcessor() {
        @Override
        public IoArgs provideIoArgs() {
            receiveIoArgs.resetLimit();
            receiveIoArgs.startWriting();
            return receiveIoArgs;
        }

        @Override
        public void onConsumeFailed(Exception e) {
            e.printStackTrace();
        }

        @Override
        public void onConsumeCompleted(IoArgs ioArgs) {
            ioArgs.finishWriting();
            try {
                ioArgs.writeTo(writableByteChannel);
            } catch (IOException e) {
                e.printStackTrace();
            }
            registerReceive();
            requestSend();
        }
    };


}
