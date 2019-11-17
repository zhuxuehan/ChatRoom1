package net.qiujuer.library.clink.box;

import net.qiujuer.library.clink.core.Packet;
import net.qiujuer.library.clink.core.SendPacket;

import java.io.InputStream;

/**
 * @description: 直流发送packet
 * @author: zxh
 * @create: 2019-11-16
 **/

public class StreamDirectSendPacket extends SendPacket<InputStream> {

    private InputStream inputStream;

    public StreamDirectSendPacket(InputStream inputStream) {
        //读取数据输出的输入流
        this.inputStream = inputStream;
        this.length = MAX_PACKET_SIZE;
    }

    @Override
    public byte type() {
        return Packet.TYPE_STREAM_DIRECT;
    }

    @Override
    protected InputStream createStream() {
        return inputStream;
    }
}
