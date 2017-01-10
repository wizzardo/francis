package com.wizzardo.agent;

import java.io.File;
import java.lang.instrument.Instrumentation;
import java.net.URL;
import java.net.URLClassLoader;

/**
 * Created by wizzardo on 10/01/17.
 */
public class Runner {
    public static void premain(String args, Instrumentation instrumentation) throws Exception {
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();

        File jarFile = new File(Runner.class.getProtectionDomain().getCodeSource().getLocation().getPath());

        URLClassLoader classloader = new URLClassLoader(new URL[]{jarFile.toURI().toURL()}, null) {
            @Override
            public String toString() {
                return "my custom classloader";
            }
        };
        Thread.currentThread().setContextClassLoader(classloader);
        Class<?> aClass = classloader.loadClass("com.wizzardo.agent.Francis");
        aClass.getMethod("premain", String.class, Instrumentation.class).invoke(null, args, instrumentation);

        Thread.currentThread().setContextClassLoader(contextClassLoader);
    }
}
