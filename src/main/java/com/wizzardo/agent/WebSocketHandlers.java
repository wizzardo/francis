package com.wizzardo.agent;

import com.wizzardo.tools.json.JsonArray;
import com.wizzardo.tools.json.JsonItem;
import com.wizzardo.tools.json.JsonObject;
import com.wizzardo.tools.json.JsonTools;
import com.wizzardo.tools.misc.Unchecked;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;

import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.jar.Manifest;
import java.util.stream.Collectors;

/**
 * Created by wizzardo on 23/01/17.
 */
public class WebSocketHandlers {
    public static void register(WebSocketClient client) {
        client.registerHandler("hello", new HelloHandler());
        client.registerHandler("listClasses", new ListClassesHandler());
        client.registerHandler("listMethods", new ListMethodsHandler());
        client.registerHandler("addTransformation", new AddTransformationHandler());
        client.registerHandler("getTransformationsResponse", (c, json) -> {
            for (JsonItem item : json.getAsJsonArray("list")) {
                TransformationDefinition transformation = read(item.asJsonObject());
                Francis.instrument(transformation);
            }
        });
    }

    public static JsonObject exceptionToJson(Throwable t) {
        JsonObject json = new JsonObject()
                .append("message", t.getMessage())
                .append("class", t.getClass().getCanonicalName())
                .append("stacktrace", new JsonArray()
                        .appendAll(Arrays.stream(t.getStackTrace())
                                .map(it -> it.getClassName() + "." + it.getMethodName() + ":" + it.getLineNumber())
                                .collect(Collectors.toList()))
                );

        if (t.getCause() != null)
            json.append("cause", exceptionToJson(t.getCause()));

        return json;
    }

    private static class HelloHandler implements WebSocketClient.CommandHandler {
        @Override
        public void handle(WebSocketClient client, JsonObject json) throws Exception {
            System.out.println(json);

            JsonObject params;
            JsonObject response = new JsonObject()
                    .append("command", "setParameters")
                    .append("params", params = new JsonObject());

            String appName = System.getenv("APP_NAME");
            if (appName != null)
                params.append("appName", appName);

            Manifest manifest = new Manifest(String.class.getResourceAsStream("/META-INF/MANIFEST.MF"));
            manifest.getMainAttributes().forEach((k, v) ->
                    params.append(String.valueOf(k), String.valueOf(v))
            );

            NetworkInterface networkInterface = NetworkTools.getNetworkInterface(client.getHost(), client.getPort());
            if (networkInterface != null) {
                params.append("mac", NetworkTools.formatMacAddress(networkInterface.getHardwareAddress()));
                params.append("ip", NetworkTools.getIPv4Address(networkInterface));
                params.append("hostname", NetworkTools.getHostname());
            }
            client.send(response);

            client.send(new JsonObject()
                    .append("command", "getTransformations")
            );
        }
    }

    private static class ListClassesHandler implements WebSocketClient.CommandHandler {
        @Override
        public void handle(WebSocketClient client, JsonObject json) throws Exception {
            Class[] classes = Francis.instrumentation.getAllLoadedClasses();
            client.send(new JsonObject()
                    .append("command", "listClasses")
                    .append("callbackId", json.getAsInteger("callbackId"))
                    .append("list", new JsonArray().appendAll(
                            Arrays.stream(classes).map(Class::getCanonicalName).collect(Collectors.toList())
                    ))
            );
        }
    }

    private static class ListMethodsHandler implements WebSocketClient.CommandHandler {
        @Override
        public void handle(WebSocketClient client, JsonObject json) throws Exception {
            String classname = json.getAsString("class");
            ClassPool cp = ClassPool.getDefault();
            CtClass cc = cp.get(classname);
            CtMethod[] declaredMethods = cc.getDeclaredMethods();
            client.send(new JsonObject()
                    .append("command", "listMethods")
                    .append("callbackId", json.getAsInteger("callbackId"))
                    .append("list", new JsonArray().appendAll(
                            Arrays.stream(declaredMethods).map(it ->
                                    Unchecked.call(() -> new JsonObject()
                                            .append("name", it.getName())
                                            .append("descriptor", it.getSignature())
                                            .append("returnType", it.getReturnType().getName()))
                                            .append("args", new JsonArray().appendAll(Unchecked.call(() ->
                                                    Arrays.stream(it.getParameterTypes()).map(CtClass::getName).collect(Collectors.toList()))))
                            ).collect(Collectors.toList())
                    ))
            );
        }
    }

    private static class AddTransformationHandler implements WebSocketClient.CommandHandler {
        @Override
        public void handle(WebSocketClient client, JsonObject json) throws Exception {
            TransformationDefinition definition = read(json);
            try {
                Francis.instrument(definition);
            } catch (Throwable t) {
                client.send(new JsonObject()
                        .append("command", "transformationError")
                        .append("error", WebSocketHandlers.exceptionToJson(t))
                        .append("transformationId", definition.id)
                        .append("before", definition.before)
                        .append("after", definition.after)
                        .append("class", definition.clazz)
                        .append("method", definition.method)
                        .append("methodDescriptor", definition.methodDescriptor)
                        .append("localVariables", new JsonArray().appendAll(definition.localVariables.stream()
                                .map(it -> new JsonObject().append("name", it.name).append("type", it.type)).collect(Collectors.toList())
                        ))
                );
            }
        }
    }

    static TransformationDefinition read(JsonObject json) {
        TransformationDefinition definition = new TransformationDefinition();
        definition.id = json.getAsLong("id");
        definition.clazz = json.getAsString("className");
        definition.method = json.getAsString("method");
        definition.methodDescriptor = json.getAsString("methodDescriptor");
        definition.before = json.getAsString("before");
        definition.after = json.getAsString("after");

        JsonArray variables = JsonTools.parse(json.getAsString("variables", "[]")).asJsonArray();
        if (variables.isEmpty()) {
            definition.localVariables = Collections.emptyList();
        } else {
            definition.localVariables = new ArrayList<>(variables.size());
            for (JsonItem item : variables) {
                for (Map.Entry<String, JsonItem> entry : item.asJsonObject().entrySet()) {
                    TransformationDefinition.Variable variable = new TransformationDefinition.Variable();
                    variable.name = entry.getKey();
                    variable.type = entry.getValue().asString();
                    definition.localVariables.add(variable);
                }
            }
        }
        return definition;
    }
}
