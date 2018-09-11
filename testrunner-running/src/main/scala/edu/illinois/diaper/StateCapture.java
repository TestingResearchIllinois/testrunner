package edu.illinois.diaper;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.DomDriver;
import edu.illinois.diaper.agent.MainAgent;
import org.custommonkey.xmlunit.DetailedDiff;
import org.custommonkey.xmlunit.Diff;
import org.custommonkey.xmlunit.Difference;
import org.custommonkey.xmlunit.NodeDetail;
import org.custommonkey.xmlunit.XMLUnit;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.CharArrayReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class StateCapture implements IStateCapture {

    protected final String testName;
    protected final List<String> currentTestStates = new ArrayList<String>();
    protected final List<Set<String>> currentRoots = new ArrayList<Set<String>>();
    private DocumentBuilder dBuilder = null;
    private boolean dirty;
    private boolean classLevel;

    // The State, field name of static root to object pointed to
    private static final LinkedHashMap<String, Object> nameToInstance = new LinkedHashMap<String, Object>();
    private static final boolean shouldIgnore = true;
    protected static final int MAX_NUM = 4;    
    private static final boolean verbose;
    private static final String logFilePath;
    private static final String projectName;
    protected static final boolean enableLogging;
    protected static final DiaperLogger logger;
    private static final int execSize = Runtime.getRuntime().availableProcessors();
    private static final ExecutorService executor = Executors.newFixedThreadPool(execSize);
    private static final Set<String> whiteList;
    public static final Set<String> ignores;
    private static final Queue<Future<?>> submittedTasks = new LinkedList<Future<?>>();

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
        XMLUnit.setNormalizeWhitespace(Boolean.TRUE);

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
    }

    // Constructor to control if it's a class-level state capture
    public StateCapture(String testName, boolean classLevel) {
        this(testName);
        this.classLevel = classLevel;
    }

    private void initialize() {
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
     */
    public void runCapture() {       

        //call capture() and add the state map to currentTestStates
        capture();
        // if we already captured 4 states for the current test, then we are ready to diff
        if (currentTestStates.size() == MAX_NUM) {
            if (enableLogging) {
                logger.startTimer();
            }
            diffPairs();
            if (enableLogging) {
                logger.stopTimeAndUpdate(Task.DIFF);
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
     * @param  afterRoots   set of the root static fileds for the 'after' state
     * @param  fileName     name of the output file in which we save the diff
     */
    private void recordDiff(String testname, String beforeState, Set<String> beforeRoots,
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

            if (!statesAreSame) {
                writeToFile(fileName + "_" + testname.replaceAll(" ", "_")
                + "_before.xml", beforeState, false);
                writeToFile(fileName + "_" + testname.replaceAll(" ", "_")
                + "_after.xml", afterState, false);
            }
            
            if (verbose) {
                Diff diff = new Diff(beforeState, afterState);
                DetailedDiff detDiff = new DetailedDiff(diff);
                List differences = detDiff.getAllDifferences();
                Collections.sort(differences, new Comparator() {
                    public int compare(Object o1, Object o2) {
                        Difference d1 = (Difference)o1;
                        Difference d2 = (Difference)o2;
                        // Sort based on id, which should represent order in the XML
                        return Integer.compare(d1.getId(), d2.getId());
                    }
                });
                for (Object object : differences) {
                    Difference difference = (Difference)object;
                    
                    sb.append("***********************\n");
                    sb.append(difference);
                    sb.append("\n~~~~\n");
                    makeDifferenceReport(difference, beforeState, sb);
                    sb.append("***********************\n");
                }
            }
            writeToFile(fileName, sb.toString(), true);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
//
//    public DiffContainer makeDifferenceReport(final Map<String, Object> before, final Map<String, Object> after) {
//        final StringBuilder sb = new StringBuilder();
//
//        final String beforeState = serializeRoots(before);
//
//        try {
//            Diff diff = new Diff(beforeState, serializeRoots(after));
//            DetailedDiff detDiff = new DetailedDiff(diff);
//            List differences = detDiff.getAllDifferences();
//            differences.sort((o1, o2) -> {
//                Difference d1 = (Difference) o1;
//                Difference d2 = (Difference) o2;
//                // Sort based on id, which should represent order in the XML
//                return Integer.compare(d1.getId(), d2.getId());
//            });
//
//            for (Object object : differences) {
//                Difference difference = (Difference)object;
//
//                sb.append("***********************\n");
////                sb.append(difference);
////                sb.append("\n~~~~\n");
//                makeDifferenceReport(difference, beforeState, sb);
//                sb.append("***********************\n");
//            }
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//
//        return sb.toString();
//    }

    private void makeDifferenceReport(Difference difference, String xmlDoc, StringBuilder sb) {
        NodeDetail controlNode = difference.getControlNodeDetail();
        NodeDetail afterNode = difference.getTestNodeDetail();
              
        String diffXpath = controlNode.getXpathLocation();
        if (diffXpath == null) {
            diffXpath = afterNode.getXpathLocation();
            if (diffXpath == null) {
                sb.append("NULL xpath\n");
                return;
            }
        }
        sb.append(controlNode.getXpathLocation());
        sb.append("\n");
        sb.append(afterNode.getXpathLocation());
        sb.append("\n");

        String[] elems = diffXpath.split("/");
        if (elems.length >= 3) {
            diffXpath = "/" + elems[1] + "/" + elems[2] + "/string[1]";
            try {                 
                XPath xPath =  XPathFactory.newInstance().newXPath();
                final String root = xPath.compile(diffXpath).evaluate(stringToXmlDocument(xmlDoc), XPathConstants.STRING).toString();
                sb.append("Static root: ");
                sb.append(root);
                sb.append("\n");
                sb.append("AUGUST ID: " + difference.getId());
                sb.append("\n");

                String controlValue = controlNode.getNode().getNodeValue();
                String foundValue = afterNode.getNode().getNodeValue();

                sb.append(controlValue + " ~> " + foundValue + "\n");
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }
          

    private String getObjectPathFromNode(Node n) {
        if(n==null)
            return "";
        String ret = "";
       
        if(n.getNodeName().equals("entry")) {
            ret += "[" + n.getChildNodes().item(1).getTextContent() + "]" ;
        } else {
            ret += "." + n.getNodeName();
        }
        return getObjectPathFromNode(n.getParentNode()) + ret;
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
                if (!f.exists()) {
                    f.createNewFile();
                }
                BufferedWriter w = new BufferedWriter(new FileWriter(f.getAbsoluteFile(), append));
                w.write(content);
                w.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    static StateCapture lastNonDirty = null;

    /**
     * Does the pairwise diffing of the captured states
     **/
    private void diffPairs() {
        // If is dirty (serialization failed), then do nothing
        // Likely this part is redundant as state should not be run for diff if serialization failed
        if (dirty) {
            return;
        }

        Runnable r = new Runnable() {
        StateCapture myNonDirty = lastNonDirty;
                public void run() {
                    /* Local Diffing
                       diff 0 and 3, 0 and 2, 1 and 2, 1 and 3
                    */

                    // fix all the xml document representation issues.
                    for (int i = 0; i < currentTestStates.size(); i++) {
                        String state = currentTestStates.get(i);
                        state = documentToString(stringToXmlDocument(state));
                        currentTestStates.set(i, state);
                    }

                    // Append extra misc stuff to file name
                    String misc = "";
                    if (classLevel) {
                        misc = "_cls";
                    }
                    // before @before and before @after
                    recordDiff(testName, currentTestStates.get(0), currentRoots.get(0),
                               currentTestStates.get(2), currentRoots.get(2), "LOG_bbba" + misc);
                    // before @before and after @after
                    recordDiff(testName, currentTestStates.get(0), currentRoots.get(0),
                               currentTestStates.get(3), currentRoots.get(3), "LOG_bbaa" + misc);
                    // after @before and before @after
                    recordDiff(testName, currentTestStates.get(1), currentRoots.get(1),
                               currentTestStates.get(2), currentRoots.get(2), "LOG_abba" + misc);
                    // after @before and after @after
                    recordDiff(testName, currentTestStates.get(1), currentRoots.get(1), 
                               currentTestStates.get(3), currentRoots.get(3), "LOG_abaa" + misc);

                    /* NonLocal Diffing synchronized
                       prev(1) and curr(1)
                    */        
                    // getLastIndex, since a test might be rerun, e.g., due to subclassing
                    try {
                        if (myNonDirty != null && !classLevel) { // Don't do pabcab for class level
                            recordDiff(myNonDirty.testName, myNonDirty.currentTestStates.get(1), myNonDirty.currentRoots.get(1), currentTestStates.get(1), currentRoots.get(1), "LOG_pabcab");
                        }
                    } catch (Throwable e) {
                        e.printStackTrace();
                    }
                }
            
            };

        if (!dirty && !classLevel) {
            lastNonDirty = this;
        }
        
        submittedTasks.add( executor.submit(r));
 

        /*Runtime runtime = Runtime.getRuntime();
        float totalMem = runtime.maxMemory();
        float freeMemory = runtime.freeMemory(); 

        if (freeMemory / totalMem < 0.5) {
            if (submittedTasks.size() == 0)
                System.out.println("***LEAK:No tasks submitted while memory is still high!");
            for (Future<?> f : submittedTasks) {
                try {
                    f.get();
                } catch (Exception ex) {
                    System.out.println("***DIAPER: Future get:");
                    ex.printStackTrace();
                }
            }
            }*/
    }


    /**
     * Adds the current serialized reachable state to the currentTestStates list
     * and the current roots to the currentRoots list.
     */
    public LinkedHashMap<String, Object> capture() {
        if (enableLogging) {
            logger.startTimer();
        }

        // Capture system properties. Seems to be a common cause of pollution.
        for (final String property : System.getProperties().stringPropertyNames()) {
            nameToInstance.put("System.property." + property, System.getProperty(property));
        }

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

            Set<Field> allFields = new HashSet<>();
            try {
                Field[] declaredFields = c.getDeclaredFields();
                Field[] fields = c.getFields();
                allFields.addAll(Arrays.asList(declaredFields));
                allFields.addAll(Arrays.asList(fields));
            } catch (NoClassDefFoundError e) {
                continue;
            }

            for (Field f : allFields) {
                String fieldName = getFieldFQN(f);
                if (ignores.contains(fieldName)) {
                    continue;
                }
                // if a field is final and has a primitive type there's no point to capture it.
                if (Modifier.isStatic(f.getModifiers())
                    && !(Modifier.isFinal(f.getModifiers()) &&  f.getType().isPrimitive() )  ) {
                    try {
                        if (shouldCapture(f)) {
                            f.setAccessible(true);
                            nameToInstance.put(fieldName, f.get(null));
                        }
                    } catch (NoClassDefFoundError e) {
                        // Case of reflection not being able to find class, is in external library?
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        if (enableLogging) {
            logger.stopTimeAndUpdate(Task.STATE_CAPTURE);
            logger.startTimer();
        }

        String serializedState = serializeRoots(nameToInstance);
        this.currentTestStates.add(serializedState);
        this.currentRoots.add(new HashSet<String>(nameToInstance.keySet()));
        if (enableLogging) {
            logger.stopTimeAndUpdate(Task.SERIALIZATION);
        }

        return nameToInstance;
    }

    protected boolean shouldCapture(Field f) {
        String fieldName = getFieldFQN(f);
        String fldLower = fieldName.toLowerCase();

        if (fldLower.contains("mockito") || 
            fldLower.contains("$$")) {
//            System.out.println("***Ignored_Root: " + fieldName);
            return false;
        }
        
//        Package p = f.getDeclaringClass().getPackage();
//        if (p!=null) {
//            String pkg = p.getName();
//            if (!whiteList.contains(pkg)) {
//                return false;
//            }
//        }
    
        return true;
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

    public String serialize(final Object obj) {
        return sanitizeXmlChars(getXStreamInstance().toXML(obj));
    }
    
    /**
     * This is the method that calls XStream to serialize the state map into a string.
     *
     * @param  state  the string to object map representing the roots of the state
     * @return        string representing the serialized input state
     */
    public String serializeRoots(Map<String, Object> state) {
        XStream xstream = getXStreamInstance();
        String s = "";
        try {
            s = xstream.toXML(state);
            s = sanitizeXmlChars(s); 
        } catch (Exception e) {
            // In case serialization fails, mark the StateCapture for this test
            // as dirty, meaning it should be ignored
            dirty = true;
            throw e;
        }
        return s;
    }

    private XStream getXStreamInstance() {
        XStream xstream = new XStream(new DomDriver());
        xstream.setMode(XStream.XPATH_ABSOLUTE_REFERENCES);
        // Set fields to be omitted during serialization
        xstream.omitField(java.lang.ref.SoftReference.class, "timestamp");
        xstream.omitField(java.lang.ref.SoftReference.class, "referent");
        xstream.omitField(java.lang.ref.Reference.class, "referent");

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


}
