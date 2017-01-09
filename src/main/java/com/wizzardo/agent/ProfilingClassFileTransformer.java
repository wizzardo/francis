package com.wizzardo.agent;

import java.io.File;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.nio.file.Files;
import java.security.ProtectionDomain;
import java.util.Iterator;
import java.util.List;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;

/**
 * Created by wizzardo on 13/01/17.
 */
public class ProfilingClassFileTransformer implements ClassFileTransformer {

    public byte[] transform(ClassLoader loader, String className, Class classBeingRedefined,
                            ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {

        byte[] byteCode = classfileBuffer;

        if (classBeingRedefined != null) {
            List<Francis.TransformationDefinition> list = Francis.instrumentations.get(classBeingRedefined);
            if (list != null && !list.isEmpty()) {
                long time = System.nanoTime();
                try {
                    ClassPool cp = ClassPool.getDefault();
                    CtClass cc = cp.get(classBeingRedefined.getCanonicalName());

                    new File("/tmp/classes").mkdirs();
                    Files.write(new File("/tmp/classes/" + classBeingRedefined.getCanonicalName() + ".before.class").toPath(), byteCode);

                    Iterator<Francis.TransformationDefinition> iterator = list.iterator();
                    try {
                        while (iterator.hasNext()) {
                            Francis.TransformationDefinition definition = iterator.next();
                            CtMethod m = cc.getMethod(definition.method, definition.methodDescriptor);
                            //$args[0] - method arguments
                            System.out.println("transforming " + m.getLongName());
//                        m.addLocalVariable("time", CtClass.longType);

                            if (definition.before != null && !definition.before.isEmpty()) {
                                System.out.println("adding before: " + definition.before);
                                m.insertBefore(wrapSafe(definition.before));
                            }

                            if (definition.after != null && !definition.after.isEmpty()) {
                                System.out.println("adding after: " + definition.after);
                                m.insertAfter(wrapSafe(definition.after));
                            }


//                        m.addLocalVariable("elapsedTime", CtClass.longType);
//                        m.insertBefore("elapsedTime = System.nanoTime();");
//                        m.insertAfter("{elapsedTime = System.nanoTime() - elapsedTime;"
//                                + "System.out.println(\"Method " + definition.method + " executed in ns: \" + elapsedTime);}");
                        }

                    } catch (Exception e) {
                        e.printStackTrace();
                        iterator.remove();
                    }
                    byteCode = cc.toBytecode();
                    Files.write(new File("/tmp/classes/" + classBeingRedefined.getCanonicalName() + ".after.class").toPath(), byteCode);
                    cc.detach();
                } catch (Exception ex) {
                    ex.printStackTrace();
                }

                time = System.nanoTime() - time;
                System.out.println("instrumented in " + time + " nanos");
            }
        }

        return byteCode;
    }

    private String wrapSafe(String script) {
        String s = "try {" + script + "} catch (Exception e){ " +
                Francis.class.getCanonicalName() + ".handleException(new Exception(\"Exception occurred in the injected code (" + script.replaceAll("\"", "\\\\\\\"") + ")\", e));}";
        System.out.println("wrapped: " + s);
        return s;
    }
}