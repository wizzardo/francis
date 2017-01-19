package com.wizzardo.agent;

import com.wizzardo.http.websocket.Message;
import com.wizzardo.http.websocket.SimpleWebSocketClient;
import com.wizzardo.tools.json.JsonObject;
import com.wizzardo.tools.json.JsonTools;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.Manifest;

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
        super(new Request(url));
        setDaemon(true);
        setDefaultUncaughtExceptionHandler((t, e) -> e.printStackTrace());

        handlers.put("hello", json -> {
            System.out.println(json);

            JsonObject jsonManifest;
            JsonObject response = new JsonObject()
                    .append("command", "setParameters")
                    .append("params", jsonManifest = new JsonObject());
            try {
                String appName = System.getenv("APP_NAME");
                if (appName != null)
                    jsonManifest.append("appName", appName);

                Manifest manifest = new Manifest(String.class.getResourceAsStream("/META-INF/MANIFEST.MF"));
                manifest.getMainAttributes().forEach((k, v) ->
                        jsonManifest.append(String.valueOf(k), String.valueOf(v))
                );
            } catch (Exception e) {
                e.printStackTrace();
            }
            try {
                send(response);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    @Override
    protected synchronized void handShake(Request request) throws IOException {
        boolean failing = true;
        while (failing)
            try {
                super.handShake(request);
                failing = false;
            } catch (IOException e) {
                System.out.println("Connection failed: " + e.getMessage());
                System.out.println("will retry in 1 second");
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignored) {
                }
            }
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

    public void send(JsonObject json) throws IOException {
        super.send(json.toString());
    }

    @Override
    public void onClose() {
        System.out.println("onClose");
    }

    protected interface CommandHandler {
        void handle(JsonObject json);
    }
}
