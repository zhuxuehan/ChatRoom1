package net.qiujuer.library.clink.box;

import net.qiujuer.library.clink.core.Packet;
import net.qiujuer.library.clink.core.ReceivePacket;

import java.io.OutputStream;

/**
 * @description:
 * @author: zxh
 * @create: 2019-11-16
 **/

public class StreamDirectReceivePacket extends ReceivePacket<OutputStream, OutputStream> {

    private OutputStream outputStream;

    public StreamDirectReceivePacket(OutputStream outputStream, long len) {
        super(len);
        //用以读取数据进行输出
        this.outputStream = outputStream;
    }

    @Override
    protected OutputStream buildEntity(OutputStream stream) {
        return outputStream;
    }

    @Override
    public byte type() {
        return Packet.TYPE_STREAM_DIRECT;
    }

    @Override
    protected OutputStream createStream() {
        return outputStream;
    }
}
