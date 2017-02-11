package com.wizzardo.agent;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.NotFoundException;

import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by wizzardo on 13/01/17.
 */
public class Francis {
    static {
        System.out.println("loaded by " + Francis.class.getClassLoader());
    }

    static ClassLoader mainClassLoader;
    static Instrumentation instrumentation;
    static Map<String, Map<Long,TransformationDefinition>> instrumentations = new ConcurrentHashMap<>();
    static ExceptionHandler exceptionHandler = e -> {
        e.printStackTrace();
    };

    public static void premain(String args, Instrumentation instrumentation) {
        Francis.instrumentation = instrumentation;
        WebSocketClient client = null;

        try {
            String francisWsUrl = System.getenv("FRANCIS_WS_URL");
            if (francisWsUrl == null || francisWsUrl.isEmpty())
                francisWsUrl = "ws://localhost:8082/ws/client";

            client = new WebSocketClient(francisWsUrl);
            WebSocketHandlers.register(client);
            client.start();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        instrumentation.addTransformer(new ProfilingClassFileTransformer(client), true);
    }

    public static void handleException(Exception e) {
        try {
            exceptionHandler.handle(e);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public static void setExceptionHandler(ExceptionHandler exceptionHandler) {
        Objects.requireNonNull(exceptionHandler, "ExceptionHandler can not be null");
        Francis.exceptionHandler = exceptionHandler;
    }

    public synchronized static void deleteTransformation(Long id, String className) {
        Map<Long,TransformationDefinition> transformations = instrumentations.get(className);
        if (transformations == null)
            return;

        transformations.remove(id);

        try {
            Class<?> aClass = Class.forName(className, true, mainClassLoader);
            System.out.println("trigger retransform for " + aClass.getCanonicalName());
            instrumentation.retransformClasses(aClass);
        } catch (UnmodifiableClassException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    public synchronized static void instrument(TransformationDefinition definition) {
        Map<Long,TransformationDefinition> transformations = instrumentations.get(definition.clazz);
        if (transformations == null)
            instrumentations.put(definition.clazz, transformations = new HashMap<>());

        transformations.put(definition.id, definition);

        try {
            Class<?> aClass = Class.forName(definition.clazz, true, mainClassLoader);
            System.out.println("trigger retransform for " + aClass.getCanonicalName());
            instrumentation.retransformClasses(aClass);
        } catch (UnmodifiableClassException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    public static List<TransformationDefinition> getPossibleTransformations(Class cl) {
        ClassPool cp = ClassPool.getDefault();
        try {
            CtClass cc = cp.get(cl.getCanonicalName());
            CtMethod[] declaredMethods = cc.getDeclaredMethods();
            List<TransformationDefinition> list = new ArrayList<>(declaredMethods.length);
            for (CtMethod method : declaredMethods) {
                TransformationDefinition definition = new TransformationDefinition();
                definition.clazz = cl.getCanonicalName();
                definition.method = method.getName();
                definition.methodDescriptor = method.getSignature();

                list.add(definition);
            }
            return list;
        } catch (NotFoundException e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    public static void setMainClassLoader(ClassLoader classLoader) {
        mainClassLoader = classLoader;
    }
}
