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
    private String xmlDir;
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
        xmlDir = Configuration.config().getProperty("statecapture.xmlDir");
        rootFile = Configuration.config().getProperty("statecapture.rootFile");
        reflectionFile = Configuration.config().getProperty("statecapture.reflectionFile");
    }

    public void load(String fieldName) throws IOException {
        if (xmlDir.isEmpty() || reflectionFile.isEmpty()) {
            System.out.println("WARNING: The subxml folder or reflection file are not provided, thus it will not do loading.");
            return;
        }
        try {
            String path0 = xmlDir + File.separator + fieldName + ".xml";
            String state0 = readFile(path0);
            String className = fieldName.substring(0, fieldName.lastIndexOf("."));
            String subFieldName = fieldName.substring(fieldName.lastIndexOf(".") + 1, fieldName.length());

            Object obj;
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
                        obj = fieldList[i].get(null);
                        boolean threadLocal = false;
                        if (obj instanceof ThreadLocal) {
                            threadLocal = true;
                        }
                        try {
                            // directly invoke reset when dealing with a Mockito mock object
                            if (Mockito.mockingDetails(obj).isMock()) {
                                Method m = Mockito.class.getDeclaredMethod("reset", Object[].class);
                                m.invoke(null, new Object[]{new Object[]{obj}});
                            } else {
                                UnmarshalChain.reset();
                                UnmarshalChain.initializeChain(fieldList[i].getDeclaringClass().getName(), fieldList[i].getName());
                                obj = xstream.fromXML(state0);
                            }
                        } catch (NoSuchMethodError nsme) { // In case the Mockito reset does not apply, still try to load from XML
                            UnmarshalChain.reset();
                            UnmarshalChain.initializeChain(fieldList[i].getDeclaringClass().getName(), fieldList[i].getName());
                            obj = xstream.fromXML(state0);
                        }
                        if (obj == null) {
                            System.out.println("Unable to construct the relevant object during load");
                            return;
                        }
                        if (AccessibleObject.class.isAssignableFrom(obj.getClass())) {
                            ((AccessibleObject) obj).setAccessible(true);
                        }
                        // the purpose is to serialize/deserialize the object wrapped within a ThreadLocal.
                        if (threadLocal) {
                            Object tmp = fieldList[i].get(null);
                            ((ThreadLocal)tmp).set(obj);
                            obj = tmp;
                        }

                        if (!Mockito.mockingDetails(obj).isMock()) {
                            FieldUtils.writeField(fieldList[i], (Object) null, obj, true);
                        }

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
                String output = fieldName + " deserializeError: " + e + "\n";
                Files.write(Paths.get(reflectionFile), output.getBytes(),
                        StandardOpenOption.APPEND);
            }
        } catch (IOException e) {
            e.printStackTrace();
            String output = fieldName + " deserializeError: " + e + "\n";
            Files.write(Paths.get(reflectionFile), output.getBytes(),
                    StandardOpenOption.APPEND);
        }
    }

    @Override
    public void capture() {
        try {
            if (xmlDir.isEmpty() || rootFile.isEmpty()) {
                System.out.println("WARNING: The xml directory or root file are not provided, thus it will not do capturing.");
                return;
            }
            String eagerload = Configuration.config().getProperty("statecapture.eagerload", "");
            if (!eagerload.equals("true")) {
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

        createXmlDir();

        Set<String> allFieldName = new HashSet<String>();
        Class[] loadedClasses = MainAgent.getInstrumentation().getAllLoadedClasses();
        File eagerLoadFile = new File(Configuration.config().getProperty("statecapture.eagerloadfile", ""));
        if (eagerLoadFile.exists()) {
            List<Class> loadedClassesList = new ArrayList(Arrays.asList(loadedClasses));
            Set<String> eagerLoadedClasses = readFileContentsAsSet(eagerLoadFile.toPath().toString());
            for (String clz : eagerLoadedClasses) {
                try {
                    Class tmp = Class.forName(clz);
                    loadedClassesList.add(tmp);
                } catch (ClassNotFoundException cnfe) {
                    cnfe.printStackTrace();
                    continue;
                } catch (NoClassDefFoundError ncdfe) {
                    ncdfe.printStackTrace();
                    continue;
                }
            }
            Class[] arrayClasses = new Class[loadedClassesList.size()];
            loadedClassesList.toArray(arrayClasses);
            loadedClasses = arrayClasses;
        }

        for (Class c : loadedClasses) {
            // Ignore classes in standard java to get top-level
            // TODO(gyori): make this read from file or config option
            String clz = c.getName();
            if (!shouldCaptureClass(clz)) {
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

            for (Field f : allFields) {
                String fieldName = getFieldFQN(f);

                // if a field is final and has a primitive type there's no point to capture it.
                if (Modifier.isStatic(f.getModifiers())
                    && !(Modifier.isFinal(f.getModifiers()) &&  f.getType().isPrimitive())) {
                    try {
                        allFieldName.add(fieldName);
                        f.setAccessible(true);

                        Object instance;
                        try {
                            instance = f.get(null);
                        } catch (NoClassDefFoundError ncdfe) {
                            instance = null;
                            ncdfe.printStackTrace();
                        }
                        // If it is actually of ThreadLocal type, we want the contents inside
                        if (instance instanceof ThreadLocal) {
                            instance = ((ThreadLocal)instance).get();
                        }
                        dirty = false;
                        String obj4field = serializeObj(instance);
                        if (!dirty) {
                            nameToInstance.put(fieldName, instance);
                            writer = new PrintWriter(xmlDir + File.separator + fieldName + ".xml", "UTF-8");
                            writer.println(obj4field);
                            writer.close();
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

        String allFieldsFile = Configuration.config().getProperty("statecapture.allFieldsFile");
        if (allFieldsFile.isEmpty()) {
            System.out.println("WARNING: The allFieldsFile file are not provided, thus it can not create a writer to " +
                    "write all fields to this field.");
            return;
        }
        writer = new PrintWriter(allFieldsFile, "UTF-8");
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
        String eagerLoadFileName = Configuration.config().getProperty("statecapture.eagerloadfile", "");
        if (eagerLoadFileName.isEmpty()) {
            System.out.println("Need to provide name of file to write what classes are loaded");
            return;
        }
        // get all loadable classes
        Class[] loadedClasses = MainAgent.getInstrumentation().getAllLoadedClasses();
        Set<String> classes = new HashSet<>();
        for (Class c : loadedClasses) {
            if (shouldCaptureClass(c.getName())) {
                classes.add(c.getName());
            }
        }

        File eagerLoadFile = new File(eagerLoadFileName);
        PrintWriter writer = new PrintWriter(eagerLoadFile, "UTF-8");
        for (String str : classes) {
            writer.println(str);
        }
        writer.close();
    }

    private boolean shouldCaptureClass(String clz) {
        if ((clz.contains("java.") && !clz.startsWith("java.lang.System"))
                || (clz.contains("javax.") && !clz.startsWith("javax.cache.Caching"))
                || clz.contains("javafx.")
                || clz.contains("jdk.")
                || clz.contains("scala.")
                || clz.contains("sun.")
                || clz.contains("edu.illinois.cs")
                || clz.contains("org.custommonkey.xmlunit")
                || clz.contains("org.junit")
                || clz.contains("statecapture.com.")) {
            return false;
        }
        return true;
    }

    private void createXmlDir() {
        File theDir = new File(xmlDir);
        if (!theDir.exists()) {
            theDir.mkdirs();
        }
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
     * This is the method that calls XStream to serialize the object into a string.
     *
     * @param  ob  the object that need to be serialized
     * @return        string representing the serialized input object
     */
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
            // throw e;
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

        // Register all our custom converters that override the defaults, similar to how XStream registers its default converters
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
