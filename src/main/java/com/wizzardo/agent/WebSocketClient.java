package com.wizzardo.agent;

import com.wizzardo.http.websocket.Message;
import com.wizzardo.http.websocket.SimpleWebSocketClient;
import com.wizzardo.tools.json.JsonObject;
import com.wizzardo.tools.json.JsonTools;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by wizzardo on 25/01/17.
 */
public class WebSocketClient extends SimpleWebSocketClient {

    Map<String, CommandHandler> handlers = new HashMap<>();

    public static void main(String[] args) throws IOException, URISyntaxException, InterruptedException {
        new WebSocketClient("ws://localhost:8082/ws/client").start();
        Thread.sleep(60 * 60 * 1000);
    }

    public WebSocketClient(String url) throws URISyntaxException, IOException {
        super(url);
        setDaemon(true);

        handlers.put("hello", json -> System.out.println(json));
    }

    @Override
    public void onMessage(Message message) {
        System.out.println("onMessage: " + message.asString());
        JsonObject data = JsonTools.parse(message.asString()).asJsonObject();

        String command = data.getAsString("command");
        CommandHandler handler = handlers.get(command);
        if (handler != null)
            handler.handle(data);
        else
            System.out.println("unknown command: " + message.asString());

    }

    @Override
    public void onClose() {
        System.out.println("onClose");
    }

    protected interface CommandHandler {
        void handle(JsonObject json);
    }
}
