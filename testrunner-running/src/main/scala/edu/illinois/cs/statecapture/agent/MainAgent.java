package edu.illinois.cs.statecapture.agent;
import java.lang.instrument.Instrumentation;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
   
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
    public static String subdiffFold;
    public static String tmpfile;
    public static String reflectionFold;

    public static Instrumentation getInstrumentation() { return inst; }
   
    public static void premain(String agentArgs, Instrumentation inst) {
        MainAgent.inst = inst;
        String[] args = agentArgs.split(",");
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
    }
}
