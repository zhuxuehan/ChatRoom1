package net.qiujuer.lesson.sample.foo.handle;


import net.qiujuer.lesson.sample.foo.Foo;
import net.qiujuer.library.clink.box.StringReceivePacket;
import net.qiujuer.library.clink.core.Connector;
import net.qiujuer.library.clink.core.IoContext;
import net.qiujuer.library.clink.core.Packet;
import net.qiujuer.library.clink.core.ReceivePacket;
import net.qiujuer.library.clink.utils.CloseUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.SocketChannel;

public class ConnectorHandler extends Connector {
    private final File cachePath;
    private final String clientInfo;
    private final ConnectorCloseChain closeChain = new DefaultPrintConnectorCloseChain();
    private final ConnectorStringPacketChain stringPacketChain = new DefaultNonConnectorStringPacketChain();

    public ConnectorHandler(SocketChannel socketChannel, File cachePath) throws IOException {
        this.clientInfo = socketChannel.getRemoteAddress().toString();
        this.cachePath = cachePath;
        setup(socketChannel);
    }

    public String getClientInfo() {
        return clientInfo;
    }

    public void exit() {
        CloseUtils.close(this);
    }

    @Override
    public void onChannelClosed(SocketChannel channel) {
        super.onChannelClosed(channel);
        closeChain.handle(this, this);
    }

    @Override
    protected File createNewReceiveFile(long length, byte[] headerInfo) {
        return Foo.createRandomTemp(cachePath);
    }

    @Override
    protected OutputStream createNewReceiveDirectOutputStream(long length, byte[] headerInfo) {
        //服务器默认创建一个内存存储
        return new ByteArrayOutputStream();
    }

    @Override
    protected void onReceivedPacket(ReceivePacket packet) {
        super.onReceivedPacket(packet);
        switch (packet.type()) {
            case Packet.TYPE_MEMORY_STRING: {
                deliveryStringPacket((StringReceivePacket) packet);
                break;
            }
            default: {
                System.out.println("New packet" + packet.type() + "-" + packet.length());
            }
        }
    }


    private void deliveryStringPacket(StringReceivePacket packet) {
        IoContext.get().getScheduler().delivery(() -> stringPacketChain.handle(this, packet));
    }

    public ConnectorStringPacketChain getStringPacketChain() {
        return stringPacketChain;
    }

    public ConnectorCloseChain getCloseChain() {
        return closeChain;
    }
}
