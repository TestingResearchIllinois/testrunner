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

import java.io.File;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.PrintWriter;
import java.io.IOException;

import java.lang.Object;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Modifier;

import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.Arrays;
import java.util.Collections;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.TreeMap;
import java.util.Properties;
import java.util.Comparator;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.Files;

import edu.illinois.cs.testrunner.configuration.Configuration;
import edu.illinois.cs.xstream.UnmarshalChain;
import edu.illinois.cs.xstream.CustomElementIgnoringMapper;
import edu.illinois.cs.xstream.EnumMapConverter;
import edu.illinois.cs.xstream.LambdaConverter;
import edu.illinois.cs.xstream.LookAndFeelConverter;
import edu.illinois.cs.xstream.MapConverter;
import edu.illinois.cs.xstream.ReflectionConverter;
import edu.illinois.cs.xstream.SerializableConverter;
import edu.illinois.cs.xstream.TreeMapConverter;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.mockito.Mockito;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.thoughtworks.xstream.XStream.PRIORITY_LOW;
import static com.thoughtworks.xstream.XStream.PRIORITY_NORMAL;
import static com.thoughtworks.xstream.XStream.PRIORITY_VERY_LOW;

public class StateCapture implements IStateCapture {


    protected final String testName;
    protected final List<String> currentTestStates = new ArrayList<String>();
    protected final List<Set<String>> currentRoots = new ArrayList<Set<String>>();

    private DocumentBuilder dBuilder = null;
    public boolean dirty;
    private boolean classLevel;

    // The State, field name of static root to object pointed to
    private static final LinkedHashMap<String, Object> nameToInstance = new LinkedHashMap<String, Object>();
    protected static final int MAX_NUM = 4;
    private static final boolean verbose;
    private static final String logFilePath;
    private static final String projectName;
    private static final boolean enableLogging;
    private static final StateCaptureLogger logger;
    private static final int execSize = Runtime.getRuntime().availableProcessors();
    private static ExecutorService executor = Executors.newFixedThreadPool(execSize);
    private static final Set<String> whiteList;
    private static final Set<String> ignores;

    //for reflection and deserialization
    public String subxmlFold;
    public String rootFold;
    public String diffFold;
    public String slug;
    public String reflectionFile;

    static {
        Properties p = System.getProperties();
        // Default if missing is false
        if (p.getProperty("verbose") == null) {
            verbose = false;
        } else {
            verbose = ((p.getProperty("verbose").equals("1")) ? true : false);
        }

        // Check if time logging is requested
        if (p.getProperty("logFile") == null) {
            enableLogging = false;
            logFilePath = "LOG_logfile";
        } else {
            enableLogging = true;
            logFilePath = p.getProperty("logFile");
        }
        if (p.getProperty("projName") == null) {
            projectName = "NO_NAME";
        } else {
            projectName = p.getProperty("projName");
        }

        logger = new StateCaptureLogger(projectName, logFilePath);

        Runnable r = new Runnable() {
            public void run() {
                try {
                    executor.shutdown();
                    executor.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        };
        Thread t = new Thread(r);
        Runtime.getRuntime().addShutdownHook(t);

        whiteList = fileToSet(p, "whitelist");
        ignores = fileToSet(p, "ignores");
    }

    private static Set<String> fileToSet(Properties p, String name) {
        String fn = p.getProperty(name);
        Set<String> wl = new HashSet<>();
        if (fn == null) {
            return wl;
        } else {
            try (BufferedReader br = new BufferedReader(new FileReader(fn))) {
                    for(String line; (line = br.readLine()) != null; ) {
                        // Support for comments (line starts with #)
                        // Essentially only add if it does not start with #
                        if (!line.startsWith("#")) {
                            wl.add(line);
                        }
                    }
                    return Collections.unmodifiableSet(wl);
                } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    public StateCapture(String testName) {
        this.testName = testName;
        try {
            dBuilder = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder();
        } catch (ParserConfigurationException ex) {
            ex.printStackTrace();
        }
        setup();
    }

    // Constructor to control if it's a class-level state capture
    public StateCapture(String testName, boolean classLevel) {
        this(testName);
        this.classLevel = classLevel;
    }

    /**
     * Call this method to capture the state, save it, and do the diffing if all required
     * states for the current test have been captured.
     * @throws IOException 
     */
    public void runCapture() {
        // if we already captured 4 states for the current test, then we are ready to diff
        if (currentTestStates.size() == MAX_NUM) {
            if (enableLogging) {
                logger.startTimer();
            }
            if (enableLogging) {
                logger.stopTimeAndUpdate(StateCaptureLogger.Task.DIFF);
                logger.saveToFileAndReset();
            }
        }
    }

    Set<String> File2SetString(String path) {
        File file = new File(path);
        Set<String> keys = new HashSet<>();
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                keys.add(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return keys;
    }

    public void setup() {
        subxmlFold = Configuration.config().getProperty("replay.subxmlFold");
        rootFold = Configuration.config().getProperty("replay.rootFold");
        diffFold = Configuration.config().getProperty("replay.diffFold");
        slug = Configuration.config().getProperty("replay.slug");
        reflectionFile = Configuration.config().getProperty("replay.reflectionFold") + "/reflection.txt";
    }

    public void fixing(String fieldName) throws IOException {
        String subxml0 = subxmlFold + "/passing_order_xml";
        try {
            String path0 = subxml0 + "/" + fieldName + ".xml";
            String state0 = readFile(path0);
            String className = fieldName.substring(0, fieldName.lastIndexOf("."));
            String subFieldName = fieldName.substring(fieldName.lastIndexOf(".")+1, fieldName.length());

            Object ob_0;
            XStream xstream = getXStreamInstance();

            try {
                Class c = Class.forName(className);
                Field[] fieldList = c.getDeclaredFields();
                for (int i=0; i< fieldList.length; i++) {
                    if (!fieldList[i].getName().equals(subFieldName)) {
                        continue;
                    }
                    try {
                        fieldList[i].setAccessible(true);
                        Field modifiersField = Field.class.getDeclaredField("modifiers");
                        modifiersField.setAccessible(true);
                        modifiersField.setInt(fieldList[i], fieldList[i].getModifiers() & ~Modifier.FINAL);
                    }
                    catch (Exception e) {
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
                                System.setProperty("currentClassInXStream", fieldList[i].getDeclaringClass().getName());
                                System.setProperty("currentFieldInXStream", fieldList[i].getName());
                                UnmarshalChain.reset();
                                UnmarshalChain.initializeChain(fieldList[i].getDeclaringClass().getName(), fieldList[i].getName());
                                ob_0 = xstream.fromXML(state0);
                            }
                        } catch (NoSuchMethodError nsme) {
                            System.setProperty("currentClassInXStream", fieldList[i].getDeclaringClass().getName());
                            System.setProperty("currentFieldInXStream", fieldList[i].getName());
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
                    } catch (Exception e) {
                        e.printStackTrace();
                        String outputNormalError = fieldName + " reflectionError: " + e + "\n";
                        Files.write(Paths.get(reflectionFile), outputNormalError.getBytes(),
                                StandardOpenOption.APPEND);
                    }
                }
            } catch (Exception e) {
            }
        } catch (Exception e) {
            String output = fieldName + " deserializeError: " + e + "\n";
            Files.write(Paths.get(reflectionFile), output.getBytes(),
                    StandardOpenOption.APPEND);
        }
    }

    public void fixingFieldList(List<String> fields) throws IOException {
        for (int index = 0; index < fields.size(); index++) {
            String fieldName = fields.get(index);
            fixing(fieldName);
        }
    }

    public void capture() {
        try {
            String state = Configuration.config().getProperty("statecapture.state");
            if (!state.equals("double_victim")) {
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
    public void capture_real() throws IOException {
        String phase = Configuration.config().getProperty("statecapture.phase");
        PrintWriter writer;

        // read whitelist;
        try (BufferedReader br = new BufferedReader(new FileReader(Configuration.config().getProperty("replay.pkgFile")))) {
            String line;
            while ((line = br.readLine()) != null) {
                whiteList.add(line);
            }
        }
        catch (Exception e) {
            return;
        }

        String subxmlDir = createSubxmlFold();

        Set<String> allFieldName = new HashSet<String>();
        Class[] loadedClasses = MainAgent.getInstrumentation().getAllLoadedClasses();
        File file = new File(rootFold + "/eagerLoadingFields.txt");
        if (file.exists()) {
            try {
                List<Class> list = new ArrayList(Arrays.asList(loadedClasses));
                String tmp2Path = rootFold + "/eagerLoadingFields.txt";
                Set<String> tmp2Classes = File2SetString(tmp2Path);
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
            } catch (Exception e) {
                e.printStackTrace();
            }
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
            } catch (Exception exception) {
                exception.printStackTrace();
                continue;
            }
            // prepare for the subxml fold

            for (Field f : allFields) {
                String fieldName = getFieldFQN(f);

                if (ignores.contains(fieldName)) {
                    continue;
                }

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

                                String ob4field = serializeOBs(instance);
                                writer = new PrintWriter(subxmlDir + "/" + fieldName + ".xml", "UTF-8");
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
                    } catch (Exception exception) {
                        exception.printStackTrace();
                        continue;
                    }
                }
            }
        }

        String state = Configuration.config().getProperty("statecapture.state");
        if (state.equals("passing_order")) {
            writer = new PrintWriter(Configuration.config().getProperty("replay.allFieldsFold") + "/passing_order.txt", "UTF-8");
            for (String ff : allFieldName) {
                writer.println(ff);
            }
            writer.close();

            writer = new PrintWriter(rootFold + "/passing_order.txt", "UTF-8");
            for (String key : nameToInstance.keySet()) {
                writer.println(key);
            }
            writer.close();
        } else if (state.equals("failing_order")) {
            writer = new PrintWriter(Configuration.config().getProperty("replay.allFieldsFold") + "/failing_order.txt", "UTF-8");
            for (String ff : allFieldName) {
                writer.println(ff);
            }
            writer.close();

            writer = new PrintWriter(rootFold + "/failing_order.txt", "UTF-8");
            for (String key : nameToInstance.keySet()) {
                writer.println(key);
            }
            writer.close();
        }
    }

    /**
     * Adds the current loadable classes to the current class list in eagerLoadingFields.txt file in the phase 2tmp.
     * @throws IOException
     */
    public void capture_class() throws IOException {
        // read whitelist;
        try (BufferedReader br = new BufferedReader(new FileReader(Configuration.config().getProperty("replay.pkgFile")))) {
            String line;
            while ((line = br.readLine()) != null) {
                whiteList.add(line);
            }
        }
        catch (Exception e) {
            e.printStackTrace();
            return;
        }

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
        PrintWriter writer = new PrintWriter(rootFold + "/" + "eagerLoadingFields.txt", "UTF-8");
        for (String str : classes) {
            writer.println(str);
        }
        writer.close();

    }

    protected boolean shouldCapture(Field f) {
        return true;
    }

    String createSubxmlFold() {
        String state = Configuration.config().getProperty("statecapture.state");
        String subxmlDir = "";
        if (state.equals("passing_order")) {
            subxmlDir = subxmlFold + "/passing_order_xml";
            File theDir = new File(subxmlDir);
            if (!theDir.exists()) {
                theDir.mkdirs();
            }
        } else if (state.equals("failing_order")) {
            subxmlDir = subxmlFold + "/failing_order_xml";
            File theDir = new File(subxmlDir);
            if (!theDir.exists()) {
                theDir.mkdirs();
            }
        }

        return subxmlDir;
    }

    /**
     * Takes in a string and removes problematic characters.
     *
     * @param  in  the input string to be filtered
     * @return     the input string with the unparsable characters removed
     */
    public static String sanitizeXmlChars(String in) {
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

    private String serializeOBs(Object ob) {
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

        for (String ignore : ignores) {
            int lastDot = ignore.lastIndexOf(".");
            String clz = ignore.substring(0,lastDot);
            String fld = ignore.substring(lastDot+1);
            try {
                xstream.omitField(Class.forName(clz), fld);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }

        return xstream;
    }
    
    protected String getFieldFQN(Field f) {
        String clz = f.getDeclaringClass().getName();
        String fld = f.getName();
        return clz + "." + fld;
    }

    public String readFile(String path) throws IOException {
        File file = new File(path);
        return FileUtils.readFileToString(file, "UTF-8");
    }
}
