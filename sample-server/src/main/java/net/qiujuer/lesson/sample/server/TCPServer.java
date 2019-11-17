package net.qiujuer.lesson.sample.server;

import net.qiujuer.lesson.sample.foo.Foo;
import net.qiujuer.lesson.sample.foo.handle.ConnectorCloseChain;
import net.qiujuer.lesson.sample.foo.handle.ConnectorHandler;
import net.qiujuer.lesson.sample.foo.handle.ConnectorHandlerChain;
import net.qiujuer.lesson.sample.foo.handle.ConnectorStringPacketChain;
import net.qiujuer.library.clink.box.StringReceivePacket;
import net.qiujuer.library.clink.core.Connector;
import net.qiujuer.library.clink.core.ScheduleJob;
import net.qiujuer.library.clink.core.schedule.IdleTimeoutScheduleJob;
import net.qiujuer.library.clink.utils.CloseUtils;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class TCPServer implements ServerAcceptor.AcceptListener, Group.GroupMessageAdaptor {
    private final int port;
    private final File cachePath;
    private final List<ConnectorHandler> connectorHandlerList = new ArrayList<>();
    private ServerSocketChannel server;
    private ServerAcceptor acceptor;
    private final ServerStatistics statistics = new ServerStatistics();
    private final Map<String, Group> groups = new HashMap<>();

    private final Map<ConnectorHandler, ConnectorHandler> audioCmdToStreamMap = new HashMap<>(100);
    private final Map<ConnectorHandler, ConnectorHandler> audioStreamToCmdMap = new HashMap<>(100);


    public TCPServer(int port, File cachePath) {
        this.port = port;
        this.cachePath = cachePath;
        this.groups.put(Foo.DEFAULT_GROUP_NAME, new Group(Foo.DEFAULT_GROUP_NAME, this));
    }

    public boolean start() {
        try {
            // 启动Acceptor线程
            ServerAcceptor acceptor = new ServerAcceptor(this);
            ServerSocketChannel server = ServerSocketChannel.open();
            // 设置为非阻塞
            server.configureBlocking(false);
            // 绑定本地端口
            server.socket().bind(new InetSocketAddress(port));
            // 注册客户端连接到达监听
            server.register(acceptor.getSelector(), SelectionKey.OP_ACCEPT);

            this.server = server;
            this.acceptor = acceptor;
            // 启动客户端监听
            acceptor.start();
            if (acceptor.awaitRunning()) {
                System.out.println("服务器准备就绪～");
                System.out.println("服务器信息：" + server.getLocalAddress().toString());
                return true;
            } else {
                System.out.println("启动异常！");
                return false;
            }

        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    public void stop() {
        if (acceptor != null) {
            acceptor.exit();
        }

        ConnectorHandler[] connectorHandlers;
        synchronized (connectorHandlerList) {
            connectorHandlers = connectorHandlerList.toArray(new ConnectorHandler[0]);
        }
        for (ConnectorHandler connectorHandler : connectorHandlers) {
            connectorHandler.exit();
        }

        CloseUtils.close(server);
    }

    void broadcast(String str) {
        str = "系统通知:" + str;
        ConnectorHandler[] connectorHandlers;
        synchronized (connectorHandlerList) {
            connectorHandlers = connectorHandlerList.toArray(new ConnectorHandler[0]);
        }
        for (ConnectorHandler connectorHandler : connectorHandlers) {
            sendMessageToClient(connectorHandler, str);
        }
    }

    @Override
    public void sendMessageToClient(ConnectorHandler handler, String msg) {
        handler.send(msg);
        statistics.sendSize++;
    }

    /**
     * 获取当前的状态信息
     */
    Object[] getStatusString() {
        return new String[]{
                "客户端数量：" + connectorHandlerList.size(),
                "发送数量：" + statistics.sendSize,
                "接收数量：" + statistics.receiveSize
        };
    }

    @Override
    public void onNewSocketArrived(SocketChannel channel) {
        try {
            ConnectorHandler connectorHandler = new ConnectorHandler(channel, cachePath);
            System.out.println(connectorHandler.getClientInfo() + ":Connected");
            connectorHandler.getStringPacketChain()
                    .appendLast(statistics.statisticsChain())
                    .appendLast(new ParseCommandConnectorStringPacketChain())
                    .appendLast(new ParseAudioStreamCommandStringPacketChain());

            connectorHandler.getCloseChain()
                    .appendLast(new RemoveAudioQueueOnConnectorClosedChain())
                    .appendLast(new RemoveQueueOnConnectorClosedChain());

            ScheduleJob scheduleJob = new IdleTimeoutScheduleJob(20, TimeUnit.SECONDS, connectorHandler);
            connectorHandler.schedule(scheduleJob);

            synchronized (connectorHandlerList) {
                connectorHandlerList.add(connectorHandler);
                System.out.println("当前客户端数量" + connectorHandlerList.size());
            }
            //回送客户端在服务器的唯一标识
            sendMessageToClient(connectorHandler, Foo.COMMAND_INFO_NAME + connectorHandler.getKey().toString());
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("客户端连接异常" + e.getMessage());
        }
    }


    private class RemoveQueueOnConnectorClosedChain extends ConnectorCloseChain {
        @Override
        protected boolean consume(ConnectorHandler handler, Connector connector) {
            synchronized (connectorHandlerList) {
                connectorHandlerList.remove(handler);
                //移除群聊客户端
                Group group = groups.get(Foo.DEFAULT_GROUP_NAME);
                group.removeMember(handler);
            }
            return true;
        }
    }

    private class ParseCommandConnectorStringPacketChain extends ConnectorStringPacketChain {
        @Override
        protected boolean consume(ConnectorHandler handler, StringReceivePacket stringReceivePacket) {
            String str = stringReceivePacket.entity();
            if (str.startsWith(Foo.COMMAND_GROUP_JOIN)) {
                Group group = groups.get(Foo.DEFAULT_GROUP_NAME);
                if (group.addMember(handler)) {
                    sendMessageToClient(handler, "Join Group:" + group.getName());
                }
                return true;
            } else if (str.startsWith(Foo.COMMAND_GROUP_LEAVE)) {
                Group group = groups.get(Foo.DEFAULT_GROUP_NAME);
                if (group.removeMember(handler)) {
                    sendMessageToClient(handler, "Leave Group:" + group.getName());
                }
                return true;
            }
            return false;
        }

        @Override
        protected boolean consumeAgain(ConnectorHandler handler, StringReceivePacket stringReceivePacket) {
            //捡漏的模式，第一遍未消费，又没有加入群，没有后续节点消费
            sendMessageToClient(handler, stringReceivePacket.entity());
            return true;
        }
    }

    //音频链接-房间映射表
    private final Map<ConnectorHandler, AudioRoom> audioStreamRoomMap = new HashMap<>();
    //房间表  房间号-房间
    private final Map<String, AudioRoom> audioRoomMap = new HashMap<>(50);

    private class ParseAudioStreamCommandStringPacketChain extends ConnectorStringPacketChain {
        @Override
        protected boolean consume(ConnectorHandler handler, StringReceivePacket stringReceivePacket) {
            String str = stringReceivePacket.entity();
            if (str.startsWith(Foo.COMMAND_CONNECTOR_BIND)) {
                String key = str.substring(Foo.COMMAND_CONNECTOR_BIND.length());
                ConnectorHandler audioStreamConnector = findConnectorFromKey(key);
                if (audioStreamConnector != null) {
                    //添加绑定关系
                    audioCmdToStreamMap.put(handler, audioStreamConnector);
                    audioStreamToCmdMap.put(audioStreamConnector, handler);
                    //转化为桥接模式
                    audioStreamConnector.changeToBridge();
                }
            } else if (str.startsWith(Foo.COMMAND_AUDIO_CREATE_ROOM)) {
                ConnectorHandler audioStreamConnector = findAudioStreamConnector(handler);
                if (audioStreamConnector != null) {
                    AudioRoom room = createNewRoom();
                    joinRoom(room, audioStreamConnector);
                    sendMessageToClient(handler, Foo.COMMAND_INFO_AUDIO_ROOM + room.getRoomCode());
                }
            } else if (str.startsWith(Foo.COMMAND_AUDIO_LEAVE_ROOM)) {
                //离开房间
                ConnectorHandler audioStreamConnector = findAudioStreamConnector(handler);
                if (audioStreamConnector != null) {
                    dissolveRoom(audioStreamConnector);
                    sendMessageToClient(handler, Foo.COMMAND_INFO_AUDIO_STOP);
                }
            } else if (str.startsWith(Foo.COMMAND_AUDIO_JOIN_ROOM)) {
                ConnectorHandler audioStreamConnector = findAudioStreamConnector(handler);
                if (audioStreamConnector != null) {
                    String roomCode = str.substring(Foo.COMMAND_AUDIO_JOIN_ROOM.length());
                    AudioRoom audioRoom = audioRoomMap.get(roomCode);
                    if (audioRoom != null && joinRoom(audioRoom, audioStreamConnector)) {
                        ConnectorHandler theOtherHandler = audioRoom.getTheOtherHandler(audioStreamConnector);
                        theOtherHandler.bindToBridge(audioStreamConnector.getSender());
                        audioStreamConnector.bindToBridge(theOtherHandler.getSender());

                        // 成功加入房间
                        sendMessageToClient(handler, Foo.COMMAND_INFO_AUDIO_START);
                        // 给对方发送可开始聊天的消息
                        sendStreamConnectorMessage(theOtherHandler, Foo.COMMAND_INFO_AUDIO_START);
                    } else {
                        sendMessageToClient(handler, Foo.COMMAND_INFO_AUDIO_ERROR);
                    }
                }
            } else {
                return false;
            }
            return true;
        }
    }

    private ConnectorHandler findConnectorFromKey(String key) {
        for (ConnectorHandler connectorHandler : connectorHandlerList) {
            if (connectorHandler.getKey().toString().equalsIgnoreCase(key)) {
                return connectorHandler;
            }
        }
        return null;
    }

    //通过命令链接找数据传输流链接，未找到返回错误
    private ConnectorHandler findAudioStreamConnector(ConnectorHandler handler) {
        ConnectorHandler connectorHandler = audioCmdToStreamMap.get(handler);
        if (connectorHandler == null) {
            sendMessageToClient(handler, Foo.COMMAND_INFO_AUDIO_ERROR);
            return null;
        }
        return connectorHandler;
    }

    private AudioRoom createNewRoom() {
        AudioRoom audioRoom;
        do {
            audioRoom = new AudioRoom();
        } while (audioRoomMap.containsKey(audioRoom.getRoomCode()));
        audioRoomMap.put(audioRoom.getRoomCode(), audioRoom);
        return audioRoom;
    }

    private boolean joinRoom(AudioRoom room, ConnectorHandler audioStreamConnector) {
        if (room.enterRoom(audioStreamConnector)) {
            audioStreamRoomMap.put(audioStreamConnector, room);
            return true;
        }
        return false;
    }

    private void dissolveRoom(ConnectorHandler streamConnector) {
        AudioRoom audioRoom = audioStreamRoomMap.get(streamConnector);
        if (audioRoom == null) {
            return;
        }
        List<ConnectorHandler> connectors = audioRoom.getConnectors();
        for (ConnectorHandler connector : connectors) {
            //解除桥接
            connector.unBindToBridge();
            //移除缓存
            audioStreamRoomMap.remove(connector);
            if (connector != streamConnector) {
                // 退出房间 并 获取对方
                sendStreamConnectorMessage(connector, Foo.COMMAND_INFO_AUDIO_STOP);
            }
        }
        audioRoomMap.remove(audioRoom.getRoomCode());
    }

    //音频断开时
    private class RemoveAudioQueueOnConnectorClosedChain extends ConnectorHandlerChain<Connector> {
        @Override
        protected boolean consume(ConnectorHandler handler, Connector connector) {
            //命令链接断开
            if (audioCmdToStreamMap.containsKey(handler)) {
                audioCmdToStreamMap.remove(handler);
            } else if (audioStreamToCmdMap.containsKey(handler)) {
                //流断开
                audioStreamToCmdMap.remove(handler);
                //解散房间
                dissolveRoom(handler);
            }
            return false;
        }
    }

    /**
     * 给链接流对应的命令控制链接发送信息
     */
    private void sendStreamConnectorMessage(ConnectorHandler streamConnector, String msg) {
        if (streamConnector != null) {
            ConnectorHandler audioCmdConnector = findAudioCmdConnector(streamConnector);
            sendMessageToClient(audioCmdConnector, msg);
        }
    }

    /**
     * 通过音频数据传输流链接寻找命令控制链接
     */
    private ConnectorHandler findAudioCmdConnector(ConnectorHandler handler) {
        return audioStreamToCmdMap.get(handler);
    }


}
