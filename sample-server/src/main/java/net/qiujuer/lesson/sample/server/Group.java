package net.qiujuer.lesson.sample.server;

import net.qiujuer.lesson.sample.server.handle.ClientHandler;
import net.qiujuer.lesson.sample.server.handle.ConnectorStringPacketChain;
import net.qiujuer.library.clink.box.StringReceivePacket;

import java.util.ArrayList;
import java.util.List;

/**
 * @description:
 * @author: zxh
 * @create: 2019-11-11
 **/

class Group {
    private final String name;
    private final GroupMessageAdaptor adaptor;
    private final List<ClientHandler> members = new ArrayList<>();

    Group(String name, GroupMessageAdaptor adaptor) {
        this.name = name;
        this.adaptor = adaptor;
    }

    String getName() {
        return name;
    }

    boolean addMember(ClientHandler handler) {
        synchronized (members) {
            if (!members.contains(handler)) {
                members.add(handler);
                handler.getStringPacketChain().appendLast(new ForwardConnectorStringPacketChain());
                System.out.println("Group[" + name + "] add new member:" + handler.getClientInfo());
                return true;
            }
        }
        return false;
    }

    boolean removeMember(ClientHandler handler) {
        synchronized (members) {
            if (members.remove(handler)) {
                handler.getStringPacketChain().remove(ForwardConnectorStringPacketChain.class);
                System.out.println("Group[" + name + "] leave member:" + handler.getClientInfo());
                return true;
            }
        }
        return false;
    }

    private class ForwardConnectorStringPacketChain extends ConnectorStringPacketChain {
        @Override
        protected boolean consume(ClientHandler handler, StringReceivePacket stringReceivePacket) {
            synchronized (members) {
                for (ClientHandler member : members) {
                    if (member == handler) {
                        continue;
                    }
                    adaptor.sendMessageToClient(member, stringReceivePacket.entity());
                }
                return true;
            }
        }
    }

    interface GroupMessageAdaptor {
        void sendMessageToClient(ClientHandler handler, String msg);
    }

}
