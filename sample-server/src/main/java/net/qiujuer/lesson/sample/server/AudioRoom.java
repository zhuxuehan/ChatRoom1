package net.qiujuer.lesson.sample.server;

import net.qiujuer.lesson.sample.foo.handle.ConnectorHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class AudioRoom {
    private final String roomCode;
    private ConnectorHandler handler1;
    private ConnectorHandler handler2;


    public AudioRoom() {
        this.roomCode = getRandomString(5);
    }

    public String getRoomCode() {
        return roomCode;

    }

    public synchronized List<ConnectorHandler> getConnectors() {
        List<ConnectorHandler> handlers = new ArrayList<>(2);
        if (handler1 != null) {
            handlers.add(handler1);
        }
        if (handler2 != null) {
            handlers.add(handler2);
        }
        return handlers;
    }

    public synchronized ConnectorHandler getTheOtherHandler(ConnectorHandler handler) {
        return (handler1 == handler || handler1 == null) ? handler2 : handler1;
    }

    public synchronized boolean isEnable() {
        return handler1 != null && handler2 != null;
    }

    public synchronized boolean enterRoom(ConnectorHandler handler) {
        if (handler1 == null) {
            handler1 = handler;
        } else if (handler2 == null) {
            handler2 = handler;
        } else {
            return false;
        }
        return true;
    }

    public synchronized ConnectorHandler exitRoom(ConnectorHandler handler) {
        if (handler1 == handler) {
            handler1 = null;
        } else if (handler2 == handler) {
            handler2 = null;
        }
        return handler1 == null ? handler2 : handler1;
    }

    private String getRandomString(int len) {
        final String str = "123456789";
        final Random random = new Random();
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) {
            int num = random.nextInt(str.length());
            sb.append(str.charAt(num));
        }
        return sb.toString();
    }
}
