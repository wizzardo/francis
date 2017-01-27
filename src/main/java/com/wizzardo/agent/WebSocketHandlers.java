package com.wizzardo.agent;

import com.wizzardo.tools.json.JsonArray;
import com.wizzardo.tools.json.JsonObject;
import com.wizzardo.tools.misc.Unchecked;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import java.util.Arrays;
import java.util.jar.Manifest;
import java.util.stream.Collectors;

/**
 * Created by wizzardo on 23/01/17.
 */
public class WebSocketHandlers {
    public static void register(WebSocketClient client) {
        client.registerHandler("hello", json -> {
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
                client.send(response);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        client.registerHandler("listClasses", json -> {
            Class[] classes = Francis.instrumentation.getAllLoadedClasses();
            client.send(new JsonObject()
                    .append("command", "listClasses")
                    .append("callbackId", json.getAsInteger("callbackId"))
                    .append("list", new JsonArray().appendAll(
                            Arrays.stream(classes).map(Class::getCanonicalName).collect(Collectors.toList())
                    ))
            );
        });

        client.registerHandler("listMethods", json -> {
            String classname = json.getAsString("class");
            try {
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
            } catch (Exception e) {
                client.send(new JsonObject()
                        .append("command", "listMethods")
                        .append("callbackId", json.getAsInteger("callbackId"))
                        .append("error", "Cannot get methods from '" + classname + "'")
                        .append("message", e.getMessage())
                        .append("exceptionClass", e.getClass().getCanonicalName())
                        .append("stacktrace", new JsonArray()
                                .appendAll(Arrays.stream(e.getStackTrace())
                                        .map(it -> it.getClassName() + "." + it.getMethodName() + ":" + it.getLineNumber())
                                        .collect(Collectors.toList()))
                        )
                );
            }
        });
    }
}
