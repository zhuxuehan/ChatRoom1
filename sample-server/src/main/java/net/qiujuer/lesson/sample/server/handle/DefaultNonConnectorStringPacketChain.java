package net.qiujuer.lesson.sample.server.handle;

import net.qiujuer.library.clink.box.StringReceivePacket;

/**
 * 默认String接收节点，不做任何事情
 */
class DefaultNonConnectorStringPacketChain extends ConnectorStringPacketChain {
    @Override
    protected boolean consume(ClientHandler handler, StringReceivePacket packet) {
        return false;
    }
}
