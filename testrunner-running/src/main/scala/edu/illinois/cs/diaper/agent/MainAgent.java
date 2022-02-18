package edu.illinois.cs.diaper.agent;
import java.lang.instrument.*;  
import java.nio.file.*;
import java.io.*;
import java.nio.charset.*;
   
public class MainAgent {  
    private static Instrumentation inst;
    public static String xmlFold;
    public static String subxmlFold;
    public static String rootFold;
    public static String pkgFile;
    public static String diffFold;
    public static String slug;
    public static String targetTestName;
    public static String outputPath;
    public static String diffFieldFold;
    public static String fieldFold;
    //public static String lognum;
    public static String subdiffFold;
    public static String tmpfile;
    public static String reflectionFold;

    public static Instrumentation getInstrumentation() { return inst; }
   
    public static void premain(String agentArgs, Instrumentation inst) {  
    	//System.out.println("here is premain");
        MainAgent.inst = inst;
        String[] args = agentArgs.split(",");
        System.out.println("args: " + agentArgs);
        xmlFold = args[0];
        subxmlFold = args[1];
        rootFold = args[2];
        pkgFile = args[3];
        diffFold = args[4];
        slug = args[5];
        targetTestName = args[6];
        outputPath = args[7];
        fieldFold = args[8];
        diffFieldFold = args[9];
        subdiffFold = args[10];
        tmpfile=args[11];
        reflectionFold = args[12];


        System.out.println("targetTestName: " + targetTestName);

        //System.out.println("agentArgs: " + agentArgs);
        //loadClasses(agentArgs);
        //Runtime.getRuntime().addShutdownHook(new Hook());
    }

    /*private static void loadClasses(String agentArgs) {
        if (agentArgs == null) {
            System.out.println("Preloading Classes is NOT Enabled");
            return;
        }
        System.out.println("Eagerly Loading Classes from: " + agentArgs);
        Charset charset = Charset.forName("US-ASCII");
        Path path = FileSystems.getDefault().getPath(agentArgs);
        try (BufferedReader reader = Files.newBufferedReader(path, charset)) {
            String line = null;
            while ((line = reader.readLine()) != null) {
                if (line.contains("edu.illinois")) {
                    continue;
                }
                try {
                    Class.forName(line);
                } catch (ClassNotFoundException ex) {
                    System.err.println("Class not found to eager load: " + line);
                } catch (java.lang.Throwable e) {
                    System.err.println("Exception in eager loading: " + e.getClass() + " : " +  line);
                }
            }
        } catch (IOException x) {
            System.err.format("IOException: %s%n", x);
        }
    }*/

    private static class Hook extends Thread {

        public void run() {
            Class[] loadedClasses = MainAgent.getInstrumentation().getAllLoadedClasses();
            try {
                FileWriter fw = new FileWriter(new File("/tmp/AUGUSTSHIDIAPER"), true);
                for (Class c : loadedClasses) {
                    fw.write(c.getName() + "\n");
                }
                fw.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
