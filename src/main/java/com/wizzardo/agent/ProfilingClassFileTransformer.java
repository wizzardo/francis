package com.wizzardo.agent;

import com.wizzardo.tools.json.JsonArray;
import com.wizzardo.tools.json.JsonObject;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.NotFoundException;

import java.io.File;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.nio.file.Files;
import java.security.ProtectionDomain;
import java.util.Iterator;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Created by wizzardo on 13/01/17.
 */
public class ProfilingClassFileTransformer implements ClassFileTransformer {

    private final WebSocketClient client;

    public ProfilingClassFileTransformer(WebSocketClient client) {
        this.client = client;
    }

    public byte[] transform(ClassLoader loader, String className, Class classBeingRedefined,
                            ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {

        byte[] byteCode = classfileBuffer;

        if (classBeingRedefined != null) {
            Map<Long, TransformationDefinition> transformations = Francis.instrumentations.get(classBeingRedefined.getCanonicalName());
            if (transformations != null && !transformations.isEmpty()) {
                long time = System.nanoTime();
                try {
                    ClassPool cp = ClassPool.getDefault();
                    CtClass cc = cp.get(classBeingRedefined.getCanonicalName());

                    new File("/tmp/classes").mkdirs();
                    Files.write(new File("/tmp/classes/" + classBeingRedefined.getCanonicalName() + ".before.class").toPath(), byteCode);

                    Iterator<TransformationDefinition> iterator = transformations.values().iterator();
                    while (iterator.hasNext()) {
                        TransformationDefinition definition = iterator.next();
                        try {
                            CtMethod m = cc.getMethod(definition.method, definition.methodDescriptor);
                            //$args[0] - method arguments
                            System.out.println("transforming " + m.getLongName());
                            for (TransformationDefinition.Variable variable : definition.localVariables) {
                                CtClass type = readClass(variable.type, cp);
                                System.out.println("adding local variable: " + type + " " + variable.name);
                                m.addLocalVariable(variable.name, type);
                            }

                            if (definition.before != null && !definition.before.isEmpty()) {
                                System.out.println("adding before: " + definition.before);
                                m.insertBefore(wrapSafe(definition.before));
//                                m.insertBefore(definition.before);
                            }
                            for (TransformationDefinition.Variable variable : definition.localVariables) {
                                CtClass type = readClass(variable.type, cp);
                                System.out.println("initialize local variable: " + type + " " + variable.name);
                                if (type == CtClass.intType)
                                    m.insertBefore(variable.name + " = 0;");
                                else if (type == CtClass.longType)
                                    m.insertBefore(variable.name + " = 0L;");
                                else if (type == CtClass.byteType)
                                    m.insertBefore(variable.name + " = 0;");
                                else if (type == CtClass.shortType)
                                    m.insertBefore(variable.name + " = 0;");
                                else if (type == CtClass.floatType)
                                    m.insertBefore(variable.name + " = 0F;");
                                else if (type == CtClass.doubleType)
                                    m.insertBefore(variable.name + " = 0D;");
                                else if (type == CtClass.charType)
                                    m.insertBefore(variable.name + " = 0;");
                                else if (type == CtClass.booleanType)
                                    m.insertBefore(variable.name + " = false;");
                                else
                                    m.insertBefore(variable.name + " = null;");
                            }

                            if (definition.after != null && !definition.after.isEmpty()) {
                                System.out.println("adding after: " + definition.after);
                                m.insertAfter(wrapSafe(definition.after));
//                                m.insertAfter(definition.after);
                            }


//                        m.addLocalVariable("elapsedTime", CtClass.longType);
//                        m.insertBefore("elapsedTime = System.nanoTime();");
//                        m.insertAfter("{elapsedTime = System.nanoTime() - elapsedTime;"
//                                + "System.out.println(\"Method " + definition.method + " executed in ns: \" + elapsedTime);}");


                        } catch (Throwable e) {
                            e.printStackTrace();
                            client.send(new JsonObject()
                                    .append("command", "transformationError")
                                    .append("error", WebSocketHandlers.exceptionToJson(e))
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
                            iterator.remove();
                        }
                    }
                    byteCode = cc.toBytecode();
                    Files.write(new File("/tmp/classes/" + classBeingRedefined.getCanonicalName() + ".after.class").toPath(), byteCode);
                    cc.detach();

                    for (TransformationDefinition definition : transformations.values()) {
                        client.send(new JsonObject()
                                .append("command", "transformationApplied")
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
                } catch (Throwable ex) {
                    ex.printStackTrace();
                }

                time = System.nanoTime() - time;
                System.out.println("instrumented in " + time + " nanos");
            }
        }

        return byteCode;
    }

    protected CtClass readClass(String clazz, ClassPool cp) throws NotFoundException {
        if (clazz.equals("int"))
            return CtClass.intType;
        if (clazz.equals("byte"))
            return CtClass.byteType;
        if (clazz.equals("long"))
            return CtClass.longType;
        if (clazz.equals("short"))
            return CtClass.shortType;
        if (clazz.equals("boolean"))
            return CtClass.booleanType;
        if (clazz.equals("float"))
            return CtClass.floatType;
        if (clazz.equals("double"))
            return CtClass.doubleType;
        if (clazz.equals("char"))
            return CtClass.charType;

        return cp.get(clazz);
    }

    private String wrapSafe(String script) {
        String s = "try {" + script + "} catch (Exception e){ " +
                Francis.class.getCanonicalName() + ".handleException(new Exception(\"Exception occurred in the injected code (" + script.replaceAll("\"", "\\\\\\\"") + ")\", e));" +
//                "System.out.println(\"Exception occurred in the injected code (" + script.replaceAll("\"", "\\\\\\\"") + ")\");" +
//                "e.printStackTrace();" +
                "}";
        System.out.println("wrapped: " + s);
        return s;
    }
}