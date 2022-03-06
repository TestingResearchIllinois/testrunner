package edu.illinois.cs.statecapture.agent;

import java.lang.instrument.Instrumentation;
   
public class MainAgent {  
    private static Instrumentation inst;

    public static Instrumentation getInstrumentation() { return inst; }
   
    public static void premain(String agentArgs, Instrumentation inst) {
        MainAgent.inst = inst;
    }
}
