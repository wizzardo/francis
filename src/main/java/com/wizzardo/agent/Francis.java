package com.wizzardo.agent;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.NotFoundException;

import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by wizzardo on 13/01/17.
 */
public class Francis {

    public static class TransformationDefinition {
        public Class clazz;
        public String method;
        public String methodDescriptor;
        public String before;
        public String after;
    }

    public interface ExceptionHandler {
        void handle(Exception e);
    }


    static Instrumentation instrumentation;
    static Map<Class, List<TransformationDefinition>> instrumentations = new ConcurrentHashMap<>();
    static ExceptionHandler exceptionHandler = e -> {
        e.printStackTrace();
    };

    public static void premain(String args, Instrumentation instrumentation) {
        instrumentation.addTransformer(new ProfilingClassFileTransformer(), true);
        Francis.instrumentation = instrumentation;
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

    public synchronized static void instrument(TransformationDefinition definition) {
        List<TransformationDefinition> list = instrumentations.get(definition.clazz);
        if (list == null)
            instrumentations.put(definition.clazz, list = new ArrayList<>());

        list.add(definition);

        try {
            instrumentation.retransformClasses(definition.clazz);
        } catch (UnmodifiableClassException e) {
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
                definition.clazz = cl;
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
}
