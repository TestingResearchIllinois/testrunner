package edu.illinois.cs.statecapture;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedList;
import java.util.Map;

public class UnmarshalChain {

    private static LinkedList<UnmarshalNode> chain = new LinkedList<>();

    public static boolean isInitialized() {
        return !chain.isEmpty();
    }

    public static void reset() {
        chain = new LinkedList<>();
    }

    // Initialize with the static root
    public static void initializeChain(String className, String fieldName) {
        chain = new LinkedList<>();
        chain.add(new UnmarshalStaticFieldNode(className, fieldName));
    }

    // Getting some string representation of the currently saved chain
    public static String getChainToString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < chain.size() - 1; i++) {
            sb.append(chain.get(i).toString());
            sb.append(" -> ");
        }
        sb.append(chain.getLast().toString());
        return sb.toString();
    }

    public static void pushNode(UnmarshalNode node) {
        chain.add(node);
    }

    // Only ever remove last node on "stack"
    public static void popNode() {
        chain.removeLast();
    }

    public static UnmarshalNode makeUnmarshalMapEntryNode(Object key) {
        return new UnmarshalMapEntryNode(key);
    }

    public static UnmarshalNode makeUnmarshalFieldNode(String className, String fieldName) {
        return new UnmarshalFieldNode(className, fieldName);
    }

    // Gets current object represented by the collected chain from the XML unmarshalling process
    public static Object getCurrObject() throws ClassNotFoundException, NoSuchFieldException, IllegalAccessException {
        Object curr = null;
        for (UnmarshalNode node : chain) {
            // Static field
            if (node instanceof UnmarshalStaticFieldNode) {
                UnmarshalStaticFieldNode staticFieldNode = (UnmarshalStaticFieldNode)node;
                Class clz = Class.forName(staticFieldNode.className);
                Field field = clz.getDeclaredField(staticFieldNode.fieldName);
                Field modifiersField = Field.class.getDeclaredField("modifiers");
                modifiersField.setAccessible(true);
                modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);
                field.setAccessible(true);
                if (!Modifier.isStatic(field.getModifiers())) { // Is not static, so problem!!!
                    throw new NoSuchFieldException("Somehow root is not static: " + staticFieldNode.className + ":" + staticFieldNode.fieldName);
                }
                curr = field.get(null);
                // Instance field
            } else if (node instanceof UnmarshalFieldNode) {
                UnmarshalFieldNode fieldNode = (UnmarshalFieldNode)node;
                Class clz = Class.forName(fieldNode.className);
                Field field = clz.getDeclaredField(fieldNode.fieldName);
                Field modifiersField = Field.class.getDeclaredField("modifiers");
                modifiersField.setAccessible(true);
                modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);
                field.setAccessible(true);
                if (Modifier.isStatic(field.getModifiers())) {  // Is static, so problem!!!
                    throw new NoSuchFieldException("Somehow current field is static: " + fieldNode.className + ":" + fieldNode.fieldName);
                }
                curr = field.get(curr);
                // Map entry with key (assume current is a map)
            } else if (node instanceof UnmarshalMapEntryNode) {
                UnmarshalMapEntryNode mapEntryNode = (UnmarshalMapEntryNode)node;
                try {
                    Map map = (Map)curr;
                    if (!map.containsKey(mapEntryNode.key)) {
                        throw new UnmarshalChain.MapEntryMissingException();
                    }
                    curr = map.get(mapEntryNode.key);
                } catch (Throwable t) {
                    throw t;
                }
            }
        }

        return curr;
    }

    public static class MapEntryMissingException extends RuntimeException {
    }
}

abstract class UnmarshalNode {
}

class UnmarshalStaticFieldNode extends UnmarshalNode {
    String className;
    String fieldName;

    UnmarshalStaticFieldNode(String className, String fieldName) {
        this.className = className;
        this.fieldName = fieldName;
    }

    @Override
    public String toString() {
        return "[" + this.className + "::" + this.fieldName + "]";
    }
}

class UnmarshalFieldNode extends UnmarshalNode {
    String className;
    String fieldName;

    UnmarshalFieldNode(String className, String fieldName) {
        this.className = className;
        this.fieldName = fieldName;
    }

    @Override
    public String toString() {
        return "[" + this.className + "::" + this.fieldName + "]";
    }
}

class UnmarshalMapEntryNode extends UnmarshalNode {
    Object key;

    UnmarshalMapEntryNode(Object key) {
        this.key = key;
    }

    @Override
    public String toString() {
        return "{key=" + this.key + "}";
    }
}

