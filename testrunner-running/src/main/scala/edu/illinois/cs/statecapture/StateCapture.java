package edu.illinois.cs.statecapture;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.reflection.FieldDictionary;
import com.thoughtworks.xstream.converters.reflection.FieldKey;
import com.thoughtworks.xstream.converters.reflection.FieldKeySorter;
import com.thoughtworks.xstream.core.JVM;
import com.thoughtworks.xstream.io.xml.DomDriver;
import com.thoughtworks.xstream.mapper.Mapper;
import com.thoughtworks.xstream.security.AnyTypePermission;

import edu.illinois.cs.statecapture.agent.MainAgent;
import edu.illinois.cs.testrunner.configuration.Configuration;
import edu.illinois.cs.xstream.CustomElementIgnoringMapper;
import edu.illinois.cs.xstream.EnumMapConverter;
import edu.illinois.cs.xstream.LambdaConverter;
import edu.illinois.cs.xstream.LookAndFeelConverter;
import edu.illinois.cs.xstream.MapConverter;
import edu.illinois.cs.xstream.ReflectionConverter;
import edu.illinois.cs.xstream.SerializableConverter;
import edu.illinois.cs.xstream.TreeMapConverter;
import edu.illinois.cs.xstream.UnmarshalChain;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;

import java.lang.Object;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.mockito.Mockito;

import static com.thoughtworks.xstream.XStream.PRIORITY_LOW;
import static com.thoughtworks.xstream.XStream.PRIORITY_NORMAL;
import static com.thoughtworks.xstream.XStream.PRIORITY_VERY_LOW;

public class StateCapture implements IStateCapture {

    protected final String testName;
    private boolean dirty;

    // The State, field name of static root to object pointed to
    private static final LinkedHashMap<String, Object> nameToInstance = new LinkedHashMap<String, Object>();

    //for reflection and deserialization
    private String xmlFold;
    private String rootFile;
    private String reflectionFile;

    public StateCapture(String testName) {
        this.testName = testName;
        setup();
    }

    static Set<String> readFileContentsAsSet(String path) {
        File file = new File(path);
        Set<String> keys = new HashSet<>();
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                keys.add(line);
            }
        } catch (FileNotFoundException fnfe) {
            fnfe.printStackTrace();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
        return keys;
    }

    private void setup() {
        xmlFold = Configuration.config().getProperty("statecapture.subxmlFold");
        rootFile = Configuration.config().getProperty("statecapture.rootFile");
        reflectionFile = Configuration.config().getProperty("statecapture.reflectionFile");
    }

    public void load(String fieldName) throws IOException {
        if (xmlFold.isEmpty() || reflectionFile.isEmpty()) {
            System.out.println("WARNING: The subxml folder or reflection file are not provided, thus it will not do loading.");
            return;
        }
        try {
            String path0 = xmlFold + "/" + fieldName + ".xml";
            String state0 = readFile(path0);
            String className = fieldName.substring(0, fieldName.lastIndexOf("."));
            String subFieldName = fieldName.substring(fieldName.lastIndexOf(".") + 1, fieldName.length());

            Object ob_0;
            XStream xstream = getXStreamInstance();

            try {
                Class c = Class.forName(className);
                Field[] fieldList = c.getDeclaredFields();
                for (int i = 0; i < fieldList.length; i++) {
                    if (!fieldList[i].getName().equals(subFieldName)) {
                        continue;
                    }
                    try {
                        fieldList[i].setAccessible(true);
                        Field modifiersField = Field.class.getDeclaredField("modifiers");
                        modifiersField.setAccessible(true);
                        modifiersField.setInt(fieldList[i], fieldList[i].getModifiers() & ~Modifier.FINAL);
                    } catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e) {
                        String outputPrivateError = fieldName + " reflectionError: " + e + "\n";
                        Files.write(Paths.get(reflectionFile), outputPrivateError.getBytes(),
                                StandardOpenOption.APPEND);
                    }
                    try {
                        ob_0 = fieldList[i].get(null);
                        boolean threadLocal = false;
                        if (ob_0 instanceof ThreadLocal) {
                            threadLocal = true;
                        }
                        try {
                            // directly invoke reset when dealing with a Mockito mock object
                            if (Mockito.mockingDetails(ob_0).isMock()) {
                                Method m = Mockito.class.getDeclaredMethod("reset", Object[].class);
                                m.invoke(null, new Object[]{new Object[]{ob_0}});
                            } else {
                                UnmarshalChain.reset();
                                UnmarshalChain.initializeChain(fieldList[i].getDeclaringClass().getName(), fieldList[i].getName());
                                ob_0 = xstream.fromXML(state0);
                            }
                        } catch (NoSuchMethodError nsme) {
                            UnmarshalChain.reset();
                            UnmarshalChain.initializeChain(fieldList[i].getDeclaringClass().getName(), fieldList[i].getName());
                            ob_0 = xstream.fromXML(state0);
                        }
                        try {
                            if (AccessibleObject.class.isAssignableFrom(ob_0.getClass())) {
                                ((AccessibleObject) ob_0).setAccessible(true);
                            }
                        } catch (NullPointerException npex) {
                            npex.printStackTrace();
                        }
                        if (threadLocal) {
                            Object tmp = fieldList[i].get(null);
                            ((ThreadLocal)tmp).set(ob_0);
                            ob_0 = tmp;
                        }

                        FieldUtils.writeField(fieldList[i], (Object)null, ob_0, true);

                        String output = fieldName + " set\n";
                        Files.write(Paths.get(reflectionFile), output.getBytes(),
                                StandardOpenOption.APPEND);
                    } catch (IllegalArgumentException | IllegalAccessException | NoSuchMethodException
                            | SecurityException | InvocationTargetException e) {
                        e.printStackTrace();
                        String outputNormalError = fieldName + " reflectionError: " + e + "\n";
                        Files.write(Paths.get(reflectionFile), outputNormalError.getBytes(),
                                StandardOpenOption.APPEND);
                    }
                }
            } catch (ClassNotFoundException | SecurityException e) {
                e.printStackTrace();
            }
        } catch (IOException e) {
            String output = fieldName + " deserializeError: " + e + "\n";
            Files.write(Paths.get(reflectionFile), output.getBytes(),
                    StandardOpenOption.APPEND);
        }
    }

    @Override
    public void capture() {
        try {
            if (xmlFold.isEmpty() || rootFile.isEmpty()) {
                System.out.println("WARNING: The subxml folder or root folder are not provided, thus it will not do capturing.");
                return;
            }
            String state = Configuration.config().getProperty("statecapture.state", "");
            if (!state.equals("eagerload")) {
                capture_real();
            } else {
                capture_class();
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Adds the current serialized reachable state to the currentTestStates list
     * and the current roots to the currentRoots list.
     * @throws IOException
     */
    private void capture_real() throws IOException {
        PrintWriter writer;

        String xmlDir = createXmlFold();

        Set<String> allFieldName = new HashSet<String>();
        Class[] loadedClasses = MainAgent.getInstrumentation().getAllLoadedClasses();
        String rootFold = rootFile.substring(0, rootFile.lastIndexOf("/"));
        File file = new File(rootFold + "/eagerLoadingFields.txt");
        if (file.exists()) {
            List<Class> list = new ArrayList(Arrays.asList(loadedClasses));
            String tmp2Path = rootFold + "/eagerLoadingFields.txt";
            Set<String> tmp2Classes = readFileContentsAsSet(tmp2Path);
            for (String key : tmp2Classes) {
                try {
                    Class tmp = Class.forName(key);
                    list.add(0, tmp);
                } catch (ClassNotFoundException cnfe) {
                    cnfe.printStackTrace();
                    continue;
                } catch (NoClassDefFoundError ncdfe) {
                    ncdfe.printStackTrace();
                    continue;
                }
            }
            Class[] arrayClasses = new Class[list.size()];
            list.toArray(arrayClasses);
            loadedClasses = arrayClasses;
        }

        for (Class c : loadedClasses) {
            // Ignore classes in standard java to get top-level
            // TODO(gyori): make this read from file or config option
            String clz = c.getName();
            if ((clz.contains("java.") && !clz.startsWith("java.lang.System"))
                || (clz.contains("javax.") && !clz.startsWith("javax.cache.Caching"))
                || clz.contains("javafx.")
                || clz.contains("jdk.")
                || clz.contains("scala.")
                || clz.contains("sun.")
                || clz.contains("edu.illinois.cs")
                || clz.contains("org.custommonkey.xmlunit")
                || clz.contains("org.junit")
                || clz.contains("statecapture.com.")
                || clz.contains("statecapture.org.")
                || clz.equals("com.openpojo.reflection.impl.AClassWithBadMethod__Generated_OpenPojo")) {
                continue;
            }

            Set<Field> allFields = new HashSet<Field>();
            try {
                Field[] declaredFields = c.getDeclaredFields();
                Field[] fields = c.getFields();
                allFields.addAll(Arrays.asList(declaredFields));
                allFields.addAll(Arrays.asList(fields));
            } catch (NoClassDefFoundError e) {
                e.printStackTrace();
                continue;
            }
            // prepare for the subxml fold

            for (Field f : allFields) {
                String fieldName = getFieldFQN(f);

                // if a field is final and has a primitive type there's no point to capture it.
                if (Modifier.isStatic(f.getModifiers())
                    && !(Modifier.isFinal(f.getModifiers()) &&  f.getType().isPrimitive())) {
                    try {
                        if (shouldCapture(f)) {
                            allFieldName.add(fieldName);
                            f.setAccessible(true);

                            Object instance;
                            try {
                                instance = f.get(null);
                            } catch (NoClassDefFoundError ncdfe) {
                                instance = null;
                                ncdfe.printStackTrace();
                            }
                            if (instance instanceof ThreadLocal) {
                                instance = ((ThreadLocal)instance).get();
                            }
                            LinkedHashMap<String, Object> nameToInstance_temp = new LinkedHashMap<String, Object>();
                            nameToInstance_temp.put(fieldName, instance);

                            dirty = false;
                            serializeRoots(nameToInstance_temp);
                            if (!dirty) {
                                nameToInstance.put(fieldName, instance);

                                String ob4field = serializeObj(instance);
                                writer = new PrintWriter(xmlDir + "/" + fieldName + ".xml", "UTF-8");
                                writer.println(ob4field);
                                writer.close();
                            }
                        }
                    } catch (OutOfMemoryError ofme) {
                        ofme.printStackTrace();
                        continue;
                    } catch (NoClassDefFoundError ncdfe) {
                        ncdfe.printStackTrace();
                        continue;
                    } catch (IllegalAccessException iae) {
                        iae.printStackTrace();
                        continue;
                    }
                }
            }
        }

        if (Configuration.config().getProperty("statecapture.allFieldsFile").isEmpty()) {
            System.out.println("WARNING: The allFieldsFile file are not provided, thus it can not create a writer to " +
                    "write all fields to this field.");
            return;
        }
        writer = new PrintWriter(Configuration.config().getProperty("statecapture.allFieldsFile"), "UTF-8");
        for (String ff : allFieldName) {
            writer.println(ff);
        }
        writer.close();

        writer = new PrintWriter(rootFile, "UTF-8");
        for (String key : nameToInstance.keySet()) {
            writer.println(key);
        }
        writer.close();
    }

    /**
     * Adds the current loadable classes to the current class list in eagerLoadingFields.txt file in the phase 2tmp.
     * @throws IOException
     */
    private void capture_class() throws IOException {
        // get all loadable classes
        Class[] loadedClasses = MainAgent.getInstrumentation().getAllLoadedClasses();
        Set<String> classes = new HashSet<>();
        for (Class c : loadedClasses) {
            String clz = c.getName();
            if ((clz.contains("java.") && !clz.startsWith("java.lang.System"))
                    || (clz.contains("javax.") && !clz.startsWith("javax.cache.Caching"))
                    || clz.contains("javafx.")
                    || clz.contains("jdk.")
                    || clz.contains("scala.")
                    || clz.contains("sun.")
                    || clz.contains("edu.illinois.cs")
                    || clz.contains("org.custommonkey.xmlunit")
                    || clz.contains("org.junit")
                    || clz.contains("statecapture.com.")
                    || clz.contains("statecapture.org.")) {
                continue;
            }
            classes.add(clz);
        }

        // write to the eagerLoadingFields.txt file
        String rootFold = rootFile.substring(0, rootFile.lastIndexOf("/"));
        PrintWriter writer = new PrintWriter(rootFold + "/" + "eagerLoadingFields.txt", "UTF-8");
        for (String str : classes) {
            writer.println(str);
        }
        writer.close();

    }

    protected boolean shouldCapture(Field f) {
        return true;
    }

    String createXmlFold() {
        String xmlDir = "";
        xmlDir = xmlFold;
        File theDir = new File(xmlDir);
        if (!theDir.exists()) {
            theDir.mkdirs();
        }
        return xmlDir;
    }

    /**
     * Takes in a string and removes problematic characters.
     *
     * @param  in  the input string to be filtered
     * @return     the input string with the unparsable characters removed
     */
    private static String sanitizeXmlChars(String in) {
        in = in.replaceAll("&#", "&amp;#");
        StringBuilder out = new StringBuilder();
        char current;

        if (in == null || ("".equals(in)))
            return "";
        for (int i = 0; i < in.length(); i++) {
            current = in.charAt(i);
            if ((current == 0x9) ||
                (current == 0xA) ||
                (current == 0xD) ||
                ((current >= 0x20) && (current <= 0xD7FF)) ||
                ((current >= 0xE000) && (current <= 0xFFFD)) ||
                ((current >= 0x10000) && (current <= 0x10FFFF)))
                out.append(current);
        }
        return out.toString();
    }

    /**
     * This is the method that calls XStream to serialize the state map into a string.
     *
     * @param  state  the string to object map representing the roots of the state
     * @return        string representing the serialized input state
     */
    private String serializeRoots(Map<String, Object> state) {
        XStream xstream = getXStreamInstance();
        String s = "";

        try {
            s = xstream.toXML(state);
        } catch (Exception exception) {
            // In case serialization fails, mark the StateCapture for this test
            // as dirty, meaning it should be ignored
            exception.printStackTrace();
            dirty = true;
        } catch (OutOfMemoryError error) {
            error.printStackTrace();
            dirty = true;
        }
        try {
            s = sanitizeXmlChars(s);
        } catch (Exception exception) {
            exception.printStackTrace();
            dirty = true;
        } catch (OutOfMemoryError error) {
            error.printStackTrace();
            dirty = true;
        }
        return s;
    }

    private String serializeObj(Object ob) {
        XStream xstream = getXStreamInstance();
        String s = "";

        try {
            s = xstream.toXML(ob);
            s = sanitizeXmlChars(s);
        } catch (Exception e) {
            // In case serialization fails, mark the StateCapture for this test
            // as dirty, meaning it should be ignored
            dirty = true;
            throw e;
        }
        return s;
    }

    private static class AlphabeticalFieldkeySorter implements FieldKeySorter {
        @Override
        public Map sort(Class type, Map keyedByFieldKey) {
            final Map<FieldKey, Field> map = new TreeMap<>(new Comparator<FieldKey>() {

                @Override
                public int compare(final FieldKey fieldKey1, final FieldKey fieldKey2) {
                    return fieldKey1.getFieldName().compareTo(fieldKey2.getFieldName());
                }
            });
            map.putAll(keyedByFieldKey);
            return map;
        }
    }

    private XStream getXStreamInstance() {
        XStream xstream = new XStream(JVM.newReflectionProvider(new FieldDictionary(
                new AlphabeticalFieldkeySorter())),new DomDriver());
        Mapper mapper = xstream.getMapper();
        mapper = new CustomElementIgnoringMapper(mapper);
        XStream newXStream = new XStream(JVM.newReflectionProvider(new FieldDictionary(
                new AlphabeticalFieldkeySorter())),new DomDriver(),xstream.getClassLoader(),mapper);
        // Set fields to be omitted during serialization

        xstream = newXStream;
        xstream.setMode(XStream.XPATH_ABSOLUTE_REFERENCES);
        xstream.addPermission(AnyTypePermission.ANY);
        xstream.omitField(Thread.class, "contextClassLoader");
        xstream.omitField(java.security.ProtectionDomain.class, "classloader");
        xstream.omitField(java.security.ProtectionDomain.class, "codesource");
        xstream.omitField(ClassLoader.class, "defaultDomain");
        xstream.omitField(ClassLoader.class, "classes");

        xstream.omitField(java.lang.ref.SoftReference.class, "timestamp");
        xstream.omitField(java.lang.ref.SoftReference.class, "referent");
        xstream.omitField(java.lang.ref.Reference.class, "referent");

        xstream.registerConverter(new MapConverter(xstream.getMapper()), PRIORITY_NORMAL + 1);
        xstream.registerConverter(new TreeMapConverter(xstream.getMapper()), PRIORITY_NORMAL + 1);
        xstream.registerConverter(new EnumMapConverter(xstream.getMapper()), PRIORITY_NORMAL + 1);

        xstream.registerConverter(new ReflectionConverter(xstream.getMapper(), xstream.getReflectionProvider()), PRIORITY_VERY_LOW + 1);
        xstream.registerConverter(new SerializableConverter(xstream.getMapper(), xstream.getReflectionProvider(), xstream.getClassLoaderReference()), PRIORITY_LOW + 1);
        xstream.registerConverter(new LambdaConverter(xstream.getMapper(), xstream.getReflectionProvider(), xstream.getClassLoaderReference()), PRIORITY_NORMAL + 1);

        if (JVM.isSwingAvailable()) {
            xstream.registerConverter(new LookAndFeelConverter(xstream.getMapper(), xstream.getReflectionProvider()), PRIORITY_NORMAL + 1);
        }

        return xstream;
    }
    
    protected String getFieldFQN(Field f) {
        String clz = f.getDeclaringClass().getName();
        String fld = f.getName();
        return clz + "." + fld;
    }

    private String readFile(String path) throws IOException {
        File file = new File(path);
        return FileUtils.readFileToString(file, "UTF-8");
    }
}
