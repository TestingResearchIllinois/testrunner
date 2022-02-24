package edu.illinois.cs.statecapture;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.reflection.*;
import com.thoughtworks.xstream.core.JVM;
import com.thoughtworks.xstream.core.UnmarshalChain;
import com.thoughtworks.xstream.io.xml.DomDriver;

import com.thoughtworks.xstream.security.AnyTypePermission;
import edu.illinois.cs.statecapture.agent.MainAgent;
import edu.illinois.cs.statecapture.StateCaptureLogger;

import java.io.*;

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

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.mockito.Mockito;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;


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
    private int xmlFileNum;
    private Set<String> diffFields = new HashSet<String> ();
    private Set<String> diffFields_filtered = new HashSet<String> ();
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
        } catch(ParserConfigurationException ex) {
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

    /**
     * Writes content into a file.
     *
     * @param  fn       name of the destination file
     * @param  content  string representing the data to be written
     * @param  append   boolean indicating whether to append to destination file or rewrite it
     */
    protected void writeToFile(String fn, String content, boolean append) {
        synchronized(StateCapture.class) {
            try {
                File f = new File(fn);
                f.createNewFile();

                FileWriter fw = new FileWriter(f.getAbsoluteFile(), append);
                BufferedWriter w = new BufferedWriter(fw);
                w.write(content);
                w.close();
            } catch (IOException e) {
                e.printStackTrace();
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
        subxmlFold = MainAgent.subxmlFold;
        rootFold = MainAgent.rootFold;
        diffFold = MainAgent.diffFold;
        slug = MainAgent.slug;
        reflectionFile = MainAgent.reflectionFold + "/0.txt";
        xmlFileNum = countDirNums(subxmlFold);
    }

    public void fixing(String fieldName) throws IOException {
        String subxml0 = subxmlFold + "/0xml";
        try {
            String path0 = subxml0 + "/" + fieldName + ".xml";
            String state0 = readFile(path0);
            String className = fieldName.substring(0, fieldName.lastIndexOf("."));
            String subFieldName = fieldName.substring(fieldName.lastIndexOf(".")+1, fieldName.length());

            Object ob_0;
            XStream xstream = getXStreamInstance();

            try {
                Class c = Class.forName(className);
                Field[] Flist = c.getDeclaredFields();
                for (int i=0; i< Flist.length; i++) {
                    if (Flist[i].getName().equals(subFieldName)) {
                        try {
                            Flist[i].setAccessible(true);
                            Field modifiersField = Field.class.getDeclaredField("modifiers");
                            modifiersField.setAccessible(true);
                            modifiersField.setInt(Flist[i], Flist[i].getModifiers() & ~Modifier.FINAL);
                        }
                        catch (Exception e) {
                            String outputPrivateError = fieldName + " reflectionError: " + e + "\n";
                            Files.write(Paths.get(reflectionFile), outputPrivateError.getBytes(),
                                    StandardOpenOption.APPEND);
                        }
                        try {
                            ob_0 = Flist[i].get(null);
                            boolean threadLocal = false;
                            if (ob_0 instanceof ThreadLocal) {
                                threadLocal = true;
                            }
                            try{
                                if (Mockito.mockingDetails(ob_0).isMock()) {
                                    Method m = Mockito.class.getDeclaredMethod("reset", Object[].class);
                                    m.invoke(null, new Object[]{new Object[]{ob_0}});
                                } else {
                                    System.setProperty("currentClassInXStream", Flist[i].getDeclaringClass().getName());
                                    System.setProperty("currentFieldInXStream", Flist[i].getName());
                                    UnmarshalChain.reset();
                                    UnmarshalChain.initializeChain(Flist[i].getDeclaringClass().getName(), Flist[i].getName());
                                    ob_0 = xstream.fromXML(state0);
                                }
                            } catch (NoSuchMethodError NSME) {
                                System.setProperty("currentClassInXStream", Flist[i].getDeclaringClass().getName());
                                System.setProperty("currentFieldInXStream", Flist[i].getName());
                                UnmarshalChain.reset();
                                UnmarshalChain.initializeChain(Flist[i].getDeclaringClass().getName(), Flist[i].getName());
                                ob_0 = xstream.fromXML(state0);
                            }
                            try {
                                if (AccessibleObject.class.isAssignableFrom(ob_0.getClass())) {
                                    ((AccessibleObject) ob_0).setAccessible(true);
                                }
                            } catch (NullPointerException NPEX) {
                                NPEX.printStackTrace();
                            }
                            if (threadLocal) {
                                Object tmp = Flist[i].get(null);
                                ((ThreadLocal)tmp).set(ob_0);
                                ob_0 = tmp;
                            }
                            // Flist[i].set(null, ob_0);
                            FieldUtils.writeField(Flist[i], (Object)null, ob_0, true);

                            String output = fieldName + " set\n";
                            Files.write(Paths.get(reflectionFile), output.getBytes(),
                                    StandardOpenOption.APPEND);
                        } catch (Exception e) {
                            e.printStackTrace();
                            String outputNormalError = fieldName + " reflectionError: " + e + "\n";
                            Files.write(Paths.get(reflectionFile), outputNormalError.getBytes(),
                                    StandardOpenOption.APPEND);
                        }
                        break;
                    }
                }
            } catch (Exception e){
            }
        } catch(Exception e) {
            String output = fieldName + " deserializeError: " + e + "\n";
            Files.write(Paths.get(reflectionFile), output.getBytes(),
                    StandardOpenOption.APPEND);
        }
    }

    public void fixingFList(List<String> fields) throws IOException {
        String subxml0 = subxmlFold + "/0xml";
        for (int index = 0; index < fields.size(); index++) {
            String fieldName = fields.get(index);
            fixing(fieldName);
        }
    }

    public void capture() {
        try {
            String phase = readFile(MainAgent.tmpfile);
            if (!phase.equals("2tmp")) {
                capture_real();
            } else {
                capture_class();
            }
        }
        catch(Exception e) {
        }
    }

    /**
     * Adds the current serialized reachable state to the currentTestStates list
     * and the current roots to the currentRoots list.
     * @throws IOException
     */
    public void capture_real() throws IOException {
        String phase = readFile(MainAgent.tmpfile);

        // read whitelist;
        try (BufferedReader br = new BufferedReader(new FileReader(MainAgent.pkgFile))) {
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
        if (phase.equals("3")) {
            try {
                List<Class> list = new ArrayList(Arrays.asList(loadedClasses));
                String tmp2Path = rootFold + "/2tmp.txt";
                Set<String> tmp2Classes = File2SetString(tmp2Path);
                for (String key : tmp2Classes) {
                    try {
                        Class tmp = Class.forName(key);
                        list.add(0, tmp);
                    } catch (ClassNotFoundException CNFE) {
                        continue;
                    } catch (NoClassDefFoundError NCDFE) {
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
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        continue;
                    }
                }
            }
        }

        int num = countDirNums(subxmlFold) - 1;
        PrintWriter writer = new PrintWriter(MainAgent.fieldFold + "/" + num + ".txt", "UTF-8");
        for (String ff : allFieldName) {
            writer.println(ff);
        }
        writer.close();

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
                continue;
            } catch (Exception exception) {
                continue;
            }
            // prepare for the subxml fold

            for (Field f : allFields) {
                String fieldName = getFieldFQN(f);

                String subFieldName = fieldName.substring(fieldName.lastIndexOf(".")+1, fieldName.length());
                if (ignores.contains(fieldName)) {
                    continue;
                }

                // if a field is final and has a primitive type there's no point to capture it.
                if (Modifier.isStatic(f.getModifiers())
                    && !(Modifier.isFinal(f.getModifiers()) &&  f.getType().isPrimitive())) {
                    try {
                        if (shouldCapture(f)) {
                            f.setAccessible(true);

                            Object instance;
                            try {
                                instance = f.get(null);
                            } catch (NoClassDefFoundError NCDFE) {
                                instance = null;
                                NCDFE.printStackTrace();
                            }
                            if(instance instanceof ThreadLocal) {
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
                    } catch (OutOfMemoryError OFME) {
                        OFME.printStackTrace();
                        continue;
                    } catch (NoClassDefFoundError NCDFE) {
                        NCDFE.printStackTrace();
                        continue;
                    } catch (Exception exception) {
                        exception.printStackTrace();
                        continue;
                    }
                }
            }
        }


        writer = new PrintWriter(rootFold + "/" + num + ".txt", "UTF-8");
        for (String key : nameToInstance.keySet()) {
            writer.println(key);
        }
        writer.close();
    }

    /**
     * Adds the current loadable classes to the current class list in 2tmp.txt file in the phase 2tmp.
     * @throws IOException
     */
    public void capture_class() throws IOException {

        String phase = readFile(MainAgent.tmpfile);

        // read whitelist;
        try (BufferedReader br = new BufferedReader(new FileReader(MainAgent.pkgFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                whiteList.add(line);
            }
        }
        catch (Exception e) {
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

        // write to the 2tmp.txt file
        PrintWriter writer = new PrintWriter(rootFold + "/" + "2tmp.txt", "UTF-8");
        for (String str : classes) {
            writer.println(str);
        }
        writer.close();

    }

    protected boolean shouldCapture(Field f) {
        return true;
    }

    String createSubxmlFold() {
        int subxmlDirCnt = 0;
        File f = new File(subxmlFold);
        File[] files = f.listFiles();
        if (files != null)
            for (int i = 0; i < files.length; i++) {
                if (files[i].isDirectory()) {
                    subxmlDirCnt++;
                }
            }

        String subxmlDir = subxmlFold + "/" + subxmlDirCnt +"xml";
        File theDir = new File(subxmlDir);
        if (!theDir.exists()){
            theDir.mkdirs();
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

        try{
            s = sanitizeXmlChars(s);
        } catch(Exception exception) {
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


        xstream.setMode(XStream.XPATH_ABSOLUTE_REFERENCES);
        xstream.addPermission(AnyTypePermission.ANY);
        // Set fields to be omitted during serialization

        xstream.omitField(Thread.class, "contextClassLoader");
        xstream.omitField(java.security.ProtectionDomain.class, "classloader");
        xstream.omitField(java.security.ProtectionDomain.class, "codesource");
        xstream.omitField(ClassLoader.class, "defaultDomain");
        xstream.omitField(ClassLoader.class, "classes");

        xstream.omitField(java.lang.ref.SoftReference.class, "timestamp");
        xstream.omitField(java.lang.ref.SoftReference.class, "referent");
        xstream.omitField(java.lang.ref.Reference.class, "referent");

        xstream.registerConverter(new CustomMapConverter(xstream.getMapper()));

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

    int countDirNums(String path) {
        File [] list = new File(path).listFiles();
        int num = 0;
        for (File file : list){
            if (file.isDirectory()){
                num ++;
            }
        }
        return num;
    }
}
