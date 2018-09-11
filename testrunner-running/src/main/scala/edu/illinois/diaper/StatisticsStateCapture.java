package edu.illinois.diaper;

import edu.illinois.diaper.agent.MainAgent;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class StatisticsStateCapture extends StateCapture {

    private static StatisticsStateCapture instance = null;

    private Set<String> included;
    private Set<String> excluded;

    public StatisticsStateCapture() {
        super("");
        included = new HashSet<String>();
        excluded = new HashSet<String>();
        JVMShutdownHook jvmShutdownHook = new JVMShutdownHook();
        Runtime.getRuntime().addShutdownHook(jvmShutdownHook);
    }

    public static IStateCapture instanceFor(String entityName) {
        if (instance == null) {
            synchronized (StatisticsStateCapture.class) {
                if (instance == null) {
                    instance = new StatisticsStateCapture();
                }
            }
        }
        return instance;
    }

    @Override
    public void runCapture() {
        captureStats();
    }

    private void captureStats() {
        Class[] loadedClasses = MainAgent.getInstrumentation().getAllLoadedClasses();
        for (Class c : loadedClasses) {
            // Ignore classes in standard java to get top-level
            // TODO(gyori): make this read from file or config option
            String clz = c.getName();
            if (clz.contains("java.") 
                || clz.contains("sun.") 
                || clz.contains("edu.edu.illinois.diaper")
                || clz.contains("diaper.com.") 
                || clz.contains("diaper.org.")) {
                continue;
            }

            Set<Field> allFields = new HashSet<Field>();
            try {
                Field[] declaredFields = c.getDeclaredFields();
                Field[] fields = c.getFields();
                allFields.addAll(Arrays.asList(declaredFields));
                allFields.addAll(Arrays.asList(fields));
            } catch (NoClassDefFoundError e) {
                continue;
            }

            for (Field f : allFields) {
                // if a field is final and has a primitive type there's no point to capture it.
                if (Modifier.isStatic(f.getModifiers())
                    && !(Modifier.isFinal(f.getModifiers()) &&  f.getType().isPrimitive() )  ) {
                    try {
                        String fieldName = getFieldFQN(f);
                        if (shouldCapture(f)) {
                            included.add(fieldName);
                        }
                        else {
                            excluded.add(fieldName);
                        }
                    } catch (NoClassDefFoundError e) {
                        // Case of reflection not being able to find class, is in external library?
                        // Technically, still need to write to excluded fields
                        //writeToFile("excluded_fields", fieldName, true);
                        continue;
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    private Set<String> readFromFile(String fileName) {
        Set<String> s = new HashSet<String>();
        try {
            BufferedReader br = new BufferedReader(new FileReader(fileName));
            try {
                String line = br.readLine();
                while (line != null) {
                    s.add(line);
                }
            } finally {
                br.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return s;
    }

    private class JVMShutdownHook extends Thread {
        public void run() {
            // First check just if file to write to exists just in case
            File f = new File("included_fields");
            // Exists means should add contents to the set
            if (f.exists()) {
                included.addAll(readFromFile("included_fields"));
            }
            StringBuilder sb = new StringBuilder();
            for (String field : included) {
                sb.append(field);
                sb.append('\n');
            }
            writeToFile("included_fields", sb.toString(), false);

            // First check just if file to write to exists just in case
            f = new File("excluded_fields");
            // Exists means should add contents to the set
            if (f.exists()) {
                excluded.addAll(readFromFile("excluded_fields"));
            }
            sb.setLength(0);    // Clear buffer
            for (String field : excluded) {
                sb.append(field);
                sb.append('\n');
            }
            writeToFile("excluded_fields", sb.toString(), false);
        }
    }
}
