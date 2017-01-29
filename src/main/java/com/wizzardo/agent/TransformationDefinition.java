package com.wizzardo.agent;

import java.util.List;

/**
 * Created by wizzardo on 25/01/17.
 */
public class TransformationDefinition {
    public long id;
    public String clazz;
    public String method;
    public String methodDescriptor;
    public String before;
    public String after;
    public List<Variable> localVariables;

    public static class Variable {
        public String name;
        public String type;
    }
}
