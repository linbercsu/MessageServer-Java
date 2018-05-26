package com.mx.lib;

import com.corundumstudio.socketio.AckCallback;
import com.corundumstudio.socketio.AckRequest;
import com.corundumstudio.socketio.Configuration;
import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;
import com.corundumstudio.socketio.listener.ConnectListener;
import com.corundumstudio.socketio.listener.DataListener;
import com.corundumstudio.socketio.listener.DisconnectListener;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class Launcher {
    static Map<String, Client> clientMap = Collections.synchronizedMap(new HashMap<String, Client>());
    static Map<Long, String> ids = Collections.synchronizedMap(new HashMap<Long, String>());

    static volatile long id;

    private static class Client {
        SocketIOClient client;
        long id;

        public Client(SocketIOClient client, long id) {
            this.client = client;
            this.id = id;
        }
    }

//    private static SocketIOClient findOtherClient(SocketIOClient thisClient) {
//        Collection<SocketIOClient> values = clientMap.values();
//        for (SocketIOClient client : values) {
//            if (!client.getSessionId().toString().equals(thisClient.getSessionId().toString())) {
//                return client;
//            }
//        }
//
//        return null;
//    }
//    private static SocketIOClient findOtherClient(String id) {
//        return clientMap.get(id);
//    }

    private static List<User> allUsers(SocketIOClient except) {
        List<User> result = new LinkedList<>();
        Collection<Client> values = clientMap.values();
        for (Client client : values) {
            if (!client.client.getSessionId().toString().equals(except.getSessionId().toString())) {
                result.add(new User(client.client.getSessionId().toString()));
            }
        }

        return result;
    }

    public static void main(String[] args) throws InterruptedException {

        Configuration config = new Configuration();
//        config.setHostname("192.168.8.51");
        config.setPort(3000);

        final SocketIOServer server = new SocketIOServer(config);

        server.addConnectListener(new ConnectListener() {
            @Override
            public void onConnect(final SocketIOClient client) {
                System.out.println("-- " + client.getSessionId().toString() + " joined --");

                final List<User> users = allUsers(client);

                Client c = new Client(client, id++);
                clientMap.put(client.getSessionId().toString(), c);
                ids.put(c.id, c.client.getSessionId().toString());

                client.sendEvent("id", new AckCallback<Object>(Object.class) {
                    @Override
                    public void onSuccess(Object result) {
                        System.out.println("-- success " + result.toString());
                    }
                }, client.getSessionId().toString());

                client.sendEvent("message", new MessageUserList(users));
            }
        });

        server.addDisconnectListener(new DisconnectListener() {
            @Override
            public void onDisconnect(SocketIOClient client) {
                System.out.println("-- " + client.getSessionId().toString() + " left --");
                clientMap.remove(client.getSessionId().toString());
            }
        });

        server.addEventListener("init", InitRequestMessage.class, new DataListener<InitRequestMessage>() {
            @Override
            public void onData(SocketIOClient client, InitRequestMessage data, AckRequest ackSender) throws Exception {
                System.out.println("-- " + client.getSessionId().toString() + " init to " + data.to);

                String sessionId = ids.get(Long.valueOf(data.to));
                Client otherClient = clientMap.get(sessionId);

//                SocketIOClient otherClient = findOtherClient(client);
                if (otherClient != null) {
                    otherClient.client.sendEvent("message", new InitMessage(String.valueOf(clientMap.get(client.getSessionId().toString()).id)));
                }
            }
        });

        server.addEventListener("message", Message.class, new DataListener<Message>() {

            @Override
            public void onData(SocketIOClient client, Message data, AckRequest ackSender) throws Exception {
                System.out.println("-- " + client.getSessionId().toString() + " message --" + gson().toJson(data));

//                SocketIOClient toClient = clientMap.get(data.to);
                String sessionId = ids.get(Long.valueOf(data.to));
                Client toClient = clientMap.get(sessionId);
                if (toClient == null) {
                    System.out.println("to client not found.");
                    return;
                }

                System.out.println("to " + toClient.client.getSessionId().toString());

                data.to = null;
                data.from = String.valueOf(clientMap.get(client.getSessionId().toString()).id);
                toClient.client.sendEvent("message", data);
            }
        });

        server.start();
        System.out.println("-- server started.");
        Thread.sleep(Integer.MAX_VALUE);

        server.stop();
    }

    public static Gson gson() {
        return new GsonBuilder().create();
    }

    public static class Message {
        public String to;
        public String type;
        public String from;
        public Object payload;
    }

    public static class InitRequestMessage {
        public String to;
        public String from;
    }

    public static class InitMessage {

        public String type = "init";
        public String from;

        public InitMessage() {
        }

        public InitMessage(String from) {
            this.from = from;
        }
    }

    public static class MessageUserList {
        public String type = "userList";
        public List<User> userList;

        public MessageUserList() {
        }

        public MessageUserList(List<User> userList) {
            this.userList = userList;
        }
    }

    public static class User {
        public String id;

        public User() {
        }

        public User(String id) {
            this.id = id;
        }
    }
}
