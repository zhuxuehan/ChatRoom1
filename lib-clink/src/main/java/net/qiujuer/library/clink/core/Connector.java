package net.qiujuer.library.clink.core;

import net.qiujuer.library.clink.box.*;
import net.qiujuer.library.clink.impl.SocketChannelAdapter;
import net.qiujuer.library.clink.impl.async.AsyncReceiveDispatcher;
import net.qiujuer.library.clink.impl.async.AsyncSendDispatcher;
import net.qiujuer.library.clink.impl.bridge.BridgeSocketDispatcher;
import net.qiujuer.library.clink.utils.CloseUtils;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public abstract class Connector implements Closeable, SocketChannelAdapter.OnChannelStatusChangedListener {
    protected UUID key = UUID.randomUUID();
    private SocketChannel channel;
    private Sender sender;
    private Receiver receiver;
    private SendDispatcher sendDispatcher;
    private ReceiveDispatcher receiveDispatcher;
    private final List<ScheduleJob> scheduleJobs = new ArrayList<>(4);


    public void setup(SocketChannel socketChannel) throws IOException {
        this.channel = socketChannel;

        IoContext context = IoContext.get();
        SocketChannelAdapter adapter = new SocketChannelAdapter(channel, context.getIoProvider(), this);

        this.sender = adapter;
        this.receiver = adapter;

        sendDispatcher = new AsyncSendDispatcher(sender);
        receiveDispatcher = new AsyncReceiveDispatcher(receiver, receivePacketCallback);

        // 启动接收
        receiveDispatcher.start();
    }


    public void send(String msg) {
        SendPacket packet = new StringSendPacket(msg);
        sendDispatcher.send(packet);
    }

    public void send(SendPacket packet) {
        sendDispatcher.send(packet);
    }

    public void changeToBridge(){
        if(receiveDispatcher instanceof BridgeSocketDispatcher){
            return;
        }
        receiveDispatcher.stop();
        BridgeSocketDispatcher dispatcher = new BridgeSocketDispatcher(receiver);
        receiveDispatcher = dispatcher;
        dispatcher.start();
    }

    public void bindToBridge(Sender sender){
        if(this.sender == sender){
            throw new UnsupportedOperationException("Can not set current connector sender");
        }
        if(!(receiveDispatcher instanceof BridgeSocketDispatcher)){
            throw new IllegalStateException("receiveDispatcher isn't bridgeSocketDispatcher");
        }
        ((BridgeSocketDispatcher) receiveDispatcher).bindSender(sender);
    }

    public void unBindToBridge(){
        if(!(receiveDispatcher instanceof BridgeSocketDispatcher)){
            throw new IllegalStateException("receiveDispatcher isn't bridgeSocketDispatcher");
        }
        ((BridgeSocketDispatcher) receiveDispatcher).bindSender(null);
    }

    public Sender getSender() {
        return sender;
    }

    public long getLastActiveTime() {
        return Math.max(sender.getLastWriteTime(), receiver.getLastReadTime());
    }

    @Override
    public void close() throws IOException {
        receiveDispatcher.close();
        sendDispatcher.close();
        sender.close();
        receiver.close();
        channel.close();
    }

    @Override
    public void onChannelClosed(SocketChannel channel) {
        synchronized (scheduleJobs) {
            scheduleJobs.forEach(job -> job.unSchedule());
            scheduleJobs.clear();
        }
        CloseUtils.close(this);
    }

    /**
     * 当一个包完全接收完成的时候回调
     *
     * @param packet Packet
     */
    protected void onReceivedPacket(ReceivePacket packet) {
    }

    /**
     * 当接收包是文件时，需要得到一份空的文件用以数据存储
     *
     * @param length     长度
     * @param headerInfo 额外信息
     * @return 新的文件
     */
    protected abstract File createNewReceiveFile(long length, byte[] headerInfo);

    /**
     * 当接收包是直流数据包时，需要得到一个用以存储当前直流数据的输出流，
     * 所有接收到的数据都将通过输出流输出
     *
     * @param length     长度
     * @param headerInfo 额外信息
     * @return 输出流
     */
    protected abstract OutputStream createNewReceiveDirectOutputStream(long length, byte[] headerInfo);

    /**
     * 当收到一个新的包Packet时会进行回调的内部类
     */
    private ReceiveDispatcher.ReceivePacketCallback receivePacketCallback = new ReceiveDispatcher.ReceivePacketCallback() {
        @Override
        public ReceivePacket<?, ?> onArrivedNewPacket(byte type, long length, byte[] headerInfo) {
            switch (type) {
                case Packet.TYPE_MEMORY_BYTES:
                    return new BytesReceivePacket(length);
                case Packet.TYPE_MEMORY_STRING:
                    return new StringReceivePacket(length);
                case Packet.TYPE_STREAM_FILE:
                    return new FileReceivePacket(length, createNewReceiveFile(length, headerInfo));
                case Packet.TYPE_STREAM_DIRECT:
                    return new StreamDirectReceivePacket(createNewReceiveDirectOutputStream(length, headerInfo), length);
                default:
                    throw new UnsupportedOperationException("Unsupported packet type:" + type);
            }
        }

        @Override
        public void onReceivePacketCompleted(ReceivePacket packet) {
            onReceivedPacket(packet);
        }

        @Override
        public void onReceiveHeartbeat() {
            System.out.println(key.toString() + ":[Heartbeat]");
        }
    };


    public UUID getKey() {
        return key;
    }

    public void fireIdleTimeoutEvent() {
        sendDispatcher.sendHeartbeat();
    }

    public void fireExceptionCaught(Throwable throwable) {
    }

    public void schedule(ScheduleJob job) {
        synchronized (scheduleJobs) {
            if (scheduleJobs.contains(job)) {
                return;
            }
            Scheduler scheduler = IoContext.get().getScheduler();
            job.schedule(scheduler);
            scheduleJobs.add(job);
        }
    }
}
