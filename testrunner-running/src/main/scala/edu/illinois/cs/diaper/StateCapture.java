package edu.illinois.cs.diaper;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.reflection.*;
import com.thoughtworks.xstream.core.JVM;
import com.thoughtworks.xstream.core.UnmarshalChain;
import com.thoughtworks.xstream.io.xml.DomDriver;

import com.thoughtworks.xstream.security.AnyTypePermission;
import edu.illinois.cs.diaper.agent.MainAgent;
import edu.illinois.cs.diaper.DiaperLogger;

import java.io.*;

import java.lang.annotation.Annotation;
import java.lang.Object;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Modifier;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Future;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.Properties;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.Files;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.maven.project.MavenProject;
import org.mockito.Mockito;

import org.xml.sax.InputSource;
import org.xmlunit.builder.DiffBuilder;
import org.xmlunit.diff.Comparison.Detail;
import org.xmlunit.diff.DefaultNodeMatcher;
import org.xmlunit.diff.Diff;
import org.xmlunit.diff.Difference;
import org.xmlunit.diff.ElementSelectors;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.OutputKeys;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;
import javax.xml.xpath.XPathConstants;
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
    private static final boolean shouldIgnore = true;
    protected static final int MAX_NUM = 4;    
    private static final boolean verbose;
    private static final String logFilePath;
    private static final String projectName;
    private static final boolean enableLogging;
    private static final DiaperLogger logger;
    private static final int execSize = Runtime.getRuntime().availableProcessors();
    private static ExecutorService executor = Executors.newFixedThreadPool(execSize);
    private static final Set<String> whiteList;
    private static final Set<String> ignores;
    private static Queue<Future<?>> submittedTasks = new LinkedList<Future<?>>();

    //for reflection and deserialzation
    private int xmlFileNum;
    private Set<String> diffFields = new HashSet<String> ();
    private Set<String> diffFields_filtered = new HashSet<String> ();
    // public String xmlFold;
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
        }
        else {
            verbose = ((p.getProperty("verbose").equals("1")) ? true : false);
        }
    
        // Check if time logging is requested
        if (p.getProperty("logFile") == null) {
            enableLogging = false;
            logFilePath = "LOG_logfile";
        }
        else {
            enableLogging = true;
            logFilePath = p.getProperty("logFile");
        }
        if (p.getProperty("projName") == null) {
            projectName = "NO_NAME";
        }
        else {
            projectName = p.getProperty("projName");
        }

        logger = new DiaperLogger(projectName, logFilePath);

        Runnable r = new Runnable() {
                public void run() {
                    try { 
                        executor.shutdown();
                        executor.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
                    } catch(Exception ex) {
                        ex.printStackTrace();
                    }
                }
            };
        Thread t = new Thread(r);
        Runtime.getRuntime().addShutdownHook(t);

        whiteList = fileToSet(p, "whitelist");
        ignores = fileToSet(p, "ignores");
    }

    public static void awaitTermination() {
        try { 
            executor.shutdown();
            executor.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
            executor = Executors.newFixedThreadPool(execSize);
            for(Future f : submittedTasks) {
                try{
                    f.get();
                } catch (Throwable t) {
                    t.printStackTrace();
                }

            }

            submittedTasks = new LinkedList<Future<?>>();
        } catch(Exception ex) {
            ex.printStackTrace();
        }
        
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
                ex.printStackTrace();
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

    private void initialize() throws IOException {
        //run capture 40 times to stabilize things a bit
        for (int i = 0; i < 40; i++) {
            // create new instance to avoid writing to file
            StateCapture sc = new StateCapture("diaper@initialization -- dummy" + i);
            sc.capture();         
        }
    }

    public static IStateCapture instanceFor(String entityName) {
        return new StateCapture(entityName);
    }

    // Factory instance for setting class level
    public static IStateCapture instanceFor(String entityName, boolean classLevel) {
        return new StateCapture(entityName, classLevel);
    }

    /**
     * Call this method to capture the state, save it, and do the diffing if all required
     * states for the current test have been captured.
     * @throws IOException 
     */
    public void runCapture() {


        //call capture() and add the state map to currentTestStates
        //capture();
        // if we already captured 4 states for the current test, then we are ready to diff
        if (currentTestStates.size() == MAX_NUM) {
            if (enableLogging) {
                logger.startTimer();
            }
            //diffPairs();
            if (enableLogging) {
                logger.stopTimeAndUpdate(DiaperLogger.Task.DIFF);
                logger.saveToFileAndReset();
            }
        }
    }


    /**
     * Removes the fields in the before state and not in the after state.
     *
     * @param  beforeState  string representing the 'before' state
     * @param  beforeRoots  set of the root static fields for the 'before' state
     * @param  afterState   string representing the 'after' state
     * @param  afterRoots   set of the root static fields for the 'after' state
     * @return              string representing the 'after' state with only the common fields with
     *                      the 'before' state
     */
    // If there are any extra fields in after not in before, add them
    private String checkAdded(String beforeState, Set<String> beforeRoots, 
                              String afterState, Set<String> afterRoots) {
        Set<String> rootsDifference = new HashSet<String>(afterRoots);
        rootsDifference.removeAll(beforeRoots);

        if (rootsDifference.isEmpty()) {
            return afterState;
        }

        Document after = stringToXmlDocument(afterState);
        Element root = after.getDocumentElement();
        NodeList ls = root.getChildNodes();
        for (int i = 0; i < ls.getLength(); i++) {
            Node n = ls.item(i);
            if (n.getNodeName().equals("entry")) {
                Node keyNode = n.getChildNodes().item(1);
                if (rootsDifference.contains(keyNode.getTextContent())) {
                    Node tmp = n.getPreviousSibling();
                    root.removeChild(n);
                    root.removeChild(tmp);
                    i = i - 2;
                }
            }
        }

        if (ls.getLength() == 1) {
            root.removeChild(ls.item(0));
        }
        
        return documentToString(after);
    }

    private Document cloneDocument(Document doc) {
        try {
            TransformerFactory tfactory = TransformerFactory.newInstance();
            Transformer tx   = tfactory.newTransformer();
            DOMSource source = new DOMSource(doc);
            DOMResult result = new DOMResult();
            tx.transform(source,result);
            return (Document)result.getNode();
        } catch (Exception ex) {
            ex.printStackTrace();
            return null;
        }
    }

    private void cleanupDocument(Document doc) {
        try{
            XPath xp = XPathFactory.newInstance().newXPath();
            NodeList nl = (NodeList) xp.evaluate("//text()[normalize-space(.)='']", 
                                                 doc, XPathConstants.NODESET);

            for (int i=0; i < nl.getLength(); ++i) {
                Node node = nl.item(i);
                node.getParentNode().removeChild(node);
            }   
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Performs the diff between the two passed state maps and saves it into a file.
     * If 'verbose' is specified, a detailed diff is also performed and appended to the file.
     *
     * @param  testname     name of the test currently diffing
     * @param  beforeState  string representing the 'before' state
     * @param  beforeRoots  set of the root static fields for the 'before' state
     * @param  afterState   string representing the 'after' state
     * @param  afterRoots   set of the root static fields for the 'after' state
     * @param  fileName     name of the output file in which we save the diff
     */
    /* private void recordDiff(String testname, String beforeState, Set<String> beforeRoots,
                                String afterState, Set<String> afterRoots, String fileName) {

        // returns a new afterState only having the roots that are common with the beforeState
        afterState = checkAdded(beforeState, beforeRoots, afterState, afterRoots);
        try {
            boolean statesAreSame = beforeState.equals(afterState);
            // create a string builder
            StringBuilder sb = new StringBuilder();
            sb.append(testname);
            sb.append(" ");
            sb.append(statesAreSame);
            sb.append("\n");

            // if (!statesAreSame) {
            //     writeToFile(fileName + "_" + testname.replaceAll(" ", "_")
            //             + "_before.xml", beforeState, false);
            //     writeToFile(fileName + "_" + testname.replaceAll(" ", "_")
            //             + "_after.xml", afterState, false);
            // }

            // if (this.verbose) {
            Diff diff = DiffBuilder.compare(beforeState).withTest(afterState).
                withNodeMatcher(new DefaultNodeMatcher(
                    // ElementSelectors.conditionalBuilder().whenElementIsNamed("entry")
                    // .thenUse(ElementSelectors.byXPath("./key", ElementSelectors.byNameAndText))
                    // .elseUse(ElementSelectors.byName)
                    // .build()
                    ElementSelectors.byName
                ))
                .checkForSimilar()
                .build();
            Iterable<Difference> differences = diff.getDifferences();
            for (Object object : differences) {
                Difference difference = (Difference)object;

                sb.append("***********************\n");
                sb.append(difference);
                sb.append("\n~~~~\n");
                makeDifferenceReport(difference, beforeState, sb);
                sb.append("***********************\n");
            }
            // }
            writeToFile(fileName, sb.toString(), true);

        } catch (Exception e) {
            e.printStackTrace();
        }
    } */

    private void recordsubDiff(String testname, String beforeState,
                            String afterState, String fileName) {
        try {
            boolean statesAreSame = beforeState.equals(afterState);
            // create a string builder
            StringBuilder sb = new StringBuilder();

            Diff diff = DiffBuilder.compare(beforeState).withTest(afterState).
                withNodeMatcher(new DefaultNodeMatcher(
                    /*ElementSelectors.conditionalBuilder().whenElementIsNamed("entry")
                    .thenUse(ElementSelectors.byXPath("./key", ElementSelectors.byNameAndText))
                    .elseUse(ElementSelectors.byName)
                    .build()*/
                    ElementSelectors.byName
                ))
                .checkForSimilar()
                .build();
            Iterable<Difference> differences = diff.getDifferences();
            for (Object object : differences) {
                Difference difference = (Difference)object;

                sb.append("***********************\n");
                sb.append(difference);
                sb.append("\n~~~~\n");
                makeSubDifferenceReport(difference, beforeState, sb);
                sb.append("***********************\n");
            }

            writeToFile(fileName, sb.toString(), true);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    /**
     * From THE INTERNET :)
     **/
    private String documentToString(Document doc) {
        try{
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            StringWriter sw = new StringWriter();
            StreamResult result = new StreamResult(sw);
            DOMSource source = new DOMSource(doc);
            transformer.transform(source, result);
            String str = sw.toString();
            str = str.trim();
            str = str.substring(str.indexOf('\n') + 1);
            return str;
        } catch (Exception ex) {
            ex.printStackTrace();
            return "";
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
                // fw.close();
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

    private void diffSub() throws FileNotFoundException, UnsupportedEncodingException {
        String subxml0 = subxmlFold + "/0xml";
        String subxml1 = subxmlFold + "/1xml";
        String afterRootPath= rootFold + "/1.txt";
        Set<String> afterRoots = File2SetString(afterRootPath);

        for(String s: afterRoots) {
            String path0 = subxml0 + "/" + s + ".xml";
            String path1 = subxml1 + "/" + s + ".xml";
            String state0 = ""; String state1 = "";
            File file0 = new File(path0);
            if(!file0.exists()){
                continue;
            }
            else {
                try{
                    state0 = readFile(path0);
                    state1 = readFile(path1);
                }
                catch(IOException e) {
                }

                if (!state0.equals(state1)) {
                    diffFields_filtered.add(s);
                    String subdiffFile = MainAgent.subdiffFold + "/" + s + ".txt";
                    recordsubDiff(testName, state0, state1, subdiffFile);
                }
            }
        }

        int num = new File(MainAgent.diffFieldFold).listFiles().length;
        PrintWriter writer = new PrintWriter(MainAgent.diffFieldFold + "/" + num+ ".txt", "UTF-8");

        for(String ff: diffFields_filtered) {

            writer.println(ff);
        }
        writer.close();
    }

    private void outputStatstics() {
        String subxml0 = subxmlFold + "/0xml";
        String subxml1 = subxmlFold + "/1xml";

        try {
            String allfieldsPath = MainAgent.fieldFold + "/0.txt";
            BufferedReader reader = new BufferedReader(new FileReader(allfieldsPath));
            int allfields = 0;
            while (reader.readLine() != null) allfields++;
            reader.close();

            String statistics = slug + "," + testName + "," + allfields
                    + "," + countFiles(subxml0) + "," + countFiles(subxml1) + "," + diffFields.size()
                + "," + diffFields_filtered.size() + "\n";

            Files.write(Paths.get(MainAgent.outputPath), statistics.getBytes(),
                    StandardOpenOption.APPEND);

        } catch (Exception e) {
            //exception handling left as an exercise for the reader
        }
    }

    private LinkedHashMap<String, Object> deserialize() {
        LinkedHashMap<String, Object> f2o_correct =
                new LinkedHashMap<String, Object>();
        String subxml0 = subxmlFold + "/0xml";
        String diffFile = MainAgent.diffFieldFold + "/0.txt";
        try (BufferedReader br = new BufferedReader(new FileReader(diffFile))) {
            String s;
            while ((s = br.readLine()) != null) {
                try{
                    String path0 = subxml0 + "/" + s + ".xml";
                    String state0 = readFile(path0);

                    XStream xstream = getXStreamInstance();
                    Object ob_0 = xstream.fromXML(state0);
                    f2o_correct.put(s, ob_0);
                }
                catch (Exception e) {
                    String output = s + " deserializeError: " + e + "\n";
                    Files.write(Paths.get(reflectionFile), output.getBytes(),
                            StandardOpenOption.APPEND);
                }

            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return f2o_correct;
    }

    private void reflection(LinkedHashMap<String, Object> f2o) {
            for(String fieldName: f2o.keySet()) {
                Object ob = "";
                ob = f2o.get(fieldName);

                String className = fieldName.substring(0, fieldName.lastIndexOf("."));
                String subFieldName = fieldName.substring(fieldName.lastIndexOf(".")+1, fieldName.length());
                try{
                    Class c = Class.forName(className);
                    Field[] Flist = c.getDeclaredFields();
                    for(int i=0; i< Flist.length; i++) {
                        if(Flist[i].getName().equals(subFieldName)) {
                            try{
                                Flist[i].setAccessible(true);
                                Field modifiersField = Field.class.getDeclaredField("modifiers");
                                modifiersField.setAccessible(true);
                                modifiersField.setInt(Flist[i], Flist[i].getModifiers() & ~Modifier.FINAL);
                            }
                            catch(Exception e) {
                                String outputPrivateError = fieldName + " reflectionError: " + e + "\n";
                                Files.write(Paths.get(reflectionFile), outputPrivateError.getBytes(),
                                        StandardOpenOption.APPEND);
                            }
                            try{
                                if(AccessibleObject.class.isAssignableFrom(ob.getClass())) {
                                    ((AccessibleObject)ob).setAccessible(true);
                                }
                                // Flist[i].set(Flist[i].getType(), ob);
                                FieldUtils.writeField(Flist[i], Flist[i].getType(), ob, true);
                                String output = fieldName + " set\n";
                                Files.write(Paths.get(reflectionFile), output.getBytes(),
                                        StandardOpenOption.APPEND);
                            }
                            catch(Exception e) {
                                String outputNormalError = fieldName + " reflectionError: " + e + "\n";
                                Files.write(Paths.get(reflectionFile), outputNormalError.getBytes(),
                                        StandardOpenOption.APPEND);
                            }
                            break;
                        }
                    }
                }
                catch(Exception e){
                }
            }
    }

    public void reflectionAll() {
        LinkedHashMap<String, Object> f2o_correct = deserialize();
        reflection(f2o_correct);
    }

    public void setup() {
        // xmlFold = MainAgent.xmlFold;
        subxmlFold = MainAgent.subxmlFold;
        rootFold = MainAgent.rootFold;
        diffFold = MainAgent.diffFold;
        slug = MainAgent.slug;
        reflectionFile = MainAgent.reflectionFold + "/0.txt";
        xmlFileNum = countDirNums(subxmlFold);
    }

    public void diffing() {
        try {
            //diffPairs();
            diffSub();
        }
        catch (Exception e){
        }
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


    //
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
                    || clz.contains("diaper.com.")
                    || clz.contains("diaper.org.")
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
                || clz.contains("diaper.com.")
                || clz.contains("diaper.org.")
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
                        continue;
                    } catch (NoClassDefFoundError NCDFE) {
                        continue;
                    } catch (Exception exception) {
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
                    || clz.contains("diaper.com.")
                    || clz.contains("diaper.org.")) {
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
        // previous code************
        /*String fieldName = getFieldFQN(f);
        String fldLower = fieldName.toLowerCase();

        if (fldLower.contains("mockito") ||
            fldLower.contains("$$")) {
            //System.out.println("***Ignored_Root: " + fieldName);
            return false;
        }

        Package p = f.getDeclaringClass().getPackage();
        //System.out.println("&&&&&&&&&package: " + p + " whiteList: " + whiteList
          //      + " p.getName: " + p.getName());
        if (p!=null) {
            String pkg = p.getName();
            if (!whiteList.contains(pkg)) {
                return false;
            }
        }

        return true;*/
        //previous code*********
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

    /* private void diffPairs() throws IOException {

        String beforeState = readFile(xmlFold + "/0.xml");
        String afterState = readFile(xmlFold + "/1.xml");
        String beforeRootPath= rootFold + "/0.txt";
        String afterRootPath= rootFold + "/1.txt";
        Set<String> beforeRoots = File2SetString(beforeRootPath);
        Set<String> afterRoots = File2SetString(afterRootPath);
        String diffFileName = diffFold + "/diff";
        System.out.println("diffFilename: " + diffFileName);
        recordDiff(testName, beforeState, beforeRoots, afterState, afterRoots, diffFileName);
    } */

    /* private void makeDifferenceReport(Difference difference, String xmlDoc, StringBuilder sb) {
        Detail controlNode = difference.getComparison().getControlDetails();
        Detail afterNode = difference.getComparison().getTestDetails();

        String diffXpath = controlNode.getXPath();
        if (diffXpath == null) {
            diffXpath = afterNode.getXPath();
            if (diffXpath == null) {
                sb.append("NULL xpath\n");
                return;
            }
        }
        sb.append(controlNode.getXPath());
        sb.append("\n");
        sb.append(afterNode.getXPath());
        sb.append("\n");

        String[] elems = diffXpath.split("/");
        if (elems.length >= 3) {
            diffXpath = "/" + elems[1] + "/" + elems[2];
            try {
                XPath xPath =  XPathFactory.newInstance().newXPath();
                Node n = (Node) xPath.compile(diffXpath).evaluate(stringToXmlDocument(xmlDoc), XPathConstants.NODE);
                n = n.getChildNodes().item(1);
                sb.append("Static root: ");
                String fieldD = n.getTextContent();
                sb.append(fieldD);
                sb.append("\n");
                diffFields.add(fieldD);
            } catch (Exception ex) {
                //ex.printStackTrace();
                System.out.println("exception in makedifferencereport!!" + ex);
            }
        }
    } */

    private void makeSubDifferenceReport(Difference difference, String xmlDoc, StringBuilder sb) {
        Detail controlNode = difference.getComparison().getControlDetails();
        Detail afterNode = difference.getComparison().getTestDetails();

        String diffXpath = controlNode.getXPath();
        if (diffXpath == null) {
            diffXpath = afterNode.getXPath();
            if (diffXpath == null) {
                sb.append("NULL xpath\n");
                return;
            }
        }
        sb.append(controlNode.getXPath());
        sb.append("\n");
        sb.append(afterNode.getXPath());
        sb.append("\n");

        sb.append(difference.getComparison().getType() + "\n");
        sb.append("--------\n");

        // Deal specifically with <entry> if in map
        if (controlNode != null) {
            Node target = controlNode.getTarget();
            if (target != null && target.getNodeName().equals("entry")) {   // Tag name "entry" matches some map structure we want to explore
                for (int i = 0; i < target.getChildNodes().getLength(); i++) {
                    sb.append(target.getChildNodes().item(i).getTextContent());
                    sb.append("\n");
                }
            }
        }
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
            //throw e;
        } catch (OutOfMemoryError error) {
            dirty = true;
        }

        try{
            s = sanitizeXmlChars(s);
        } catch(Exception exception) {
            exception.printStackTrace();
            dirty = true;
        } catch (OutOfMemoryError error) {
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
        //XStream xstream = new XStream(new DomDriver());
        //XStream xstream = new XStream(new PureJavaReflectionProvider(new FieldDictionary(
              //  new AlphabeticalFieldkeySorter())),new DomDriver());
        //XStream xstream = new XStream(new Sun14ReflectionProvider(new FieldDictionary(
               //  new AlphabeticalFieldkeySorter())),new DomDriver());
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
        //
        //com.sun.jmx.mbeanserver.ModifiableClassLoaderRepository.class;
        /*try {
            xstream.omitField(Class.forName("com.sun.jmx.mbeanserver.ClassLoaderRepositorySupport$LoaderEntry"), "loader");
            xstream.omitField(Class.forName("io.netty.buffer.PoolArena"), "allocationsTiny");
            xstream.omitField(Class.forName("io.netty.buffer.PoolArena"), "allocationsSmall");
            xstream.omitField(Class.forName("io.netty.buffer.PoolArena"), "allocationsHuge");
            xstream.omitField(Class.forName("io.netty.buffer.PoolArena"), "activeBytesHuge");
            xstream.omitField(Class.forName("io.netty.buffer.PoolArena"), "deallocationsHuge");
        }
        catch(Exception ex) {
            System.out.println("error occur in Class.forName in getXStreamInstance: " + ex);
        }*/

        xstream.omitField(java.lang.ref.SoftReference.class, "timestamp");
        xstream.omitField(java.lang.ref.SoftReference.class, "referent");
        xstream.omitField(java.lang.ref.Reference.class, "referent");

        xstream.registerConverter(new CustomMapConverter(xstream.getMapper()));
        
        /*
          String ignores[][] = new String[][] {
          {"com.squareup.wire.Wire", "messageAdapters"},
          {"com.squareup.wire.Wire", "builderAdapters"},
          {"com.squareup.wire.Wire", "enumAdapters"},
          {"org.apache.http.impl.conn.CPool", "COUNTER"},
          {"org.apache.http.impl.conn.ManagedHttpClientConnectionFactory", "COUNTER"},
          {"org.apache.http.localserver.LocalTestServer", "TEST_SERVER_ADDR"},
          {"org.apache.http.impl.auth.NTLMEngineImpl", "RND_GEN"}};
        */

        for (String ignore : ignores) {
            int lastDot = ignore.lastIndexOf(".");
            String clz = ignore.substring(0,lastDot);
            String fld = ignore.substring(lastDot+1);
            try {
                xstream.omitField(Class.forName(clz), fld);
            } catch (Exception ex) {
                //ex.printStackTrace();
                //Do not throw runtime exception, since some modules might indeed not
                //load all classes in the project.
                //throw new RuntimeException(ex);
            }
        }

        return xstream;
    }

    private Document stringToXmlDocument(String str) {
        try {           
            CharArrayReader rdr = new CharArrayReader(str.toCharArray());
            InputSource is = new InputSource(rdr);
            Document doc = dBuilder.parse(is);
            //cleanupDocument(doc);
            return doc;

        } catch(Exception ex) {
            ex.printStackTrace();
            return null;
        }

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

    int countFiles(String path) {
        File f = new File(path);
        return f.listFiles().length;
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
