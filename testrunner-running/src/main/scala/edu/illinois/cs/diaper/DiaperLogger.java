package edu.illinois.cs.diaper;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;

public class DiaperLogger {
    public enum Task { SERIALIZATION, STATE_CAPTURE, DIFF };
    private static double serializationTime;
    private static double stateCaptureTime;
    private static double diffTime;
    private String projName;
    private long startTime;
    private long  endTime;
    private String pathToLogFile;


    public DiaperLogger(String projName, String pathToLogFile) {
	this.projName = projName;
	this.pathToLogFile = pathToLogFile;
	createHeader();
    }

    private void writeToFile(String fn, String content, boolean append) {
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

    public double getSerializationTime() {
	return serializationTime;
    }

    public double getStateCaptureTime() {
	return stateCaptureTime;
    }
    public void startTimer() {
	startTime = System.currentTimeMillis();
    }

    public void stopTimeAndUpdate(Task task) {
	endTime = System.currentTimeMillis();
	double duration_sec = (double)(endTime - startTime)/1000;
	if ( task == Task.SERIALIZATION) {
	    serializationTime += duration_sec;
	}
	else if (task == Task.STATE_CAPTURE) {
	    stateCaptureTime += duration_sec;
	}
	else if (task == Task.DIFF) {
	    diffTime += duration_sec;
	}
	
	// reset the times
	startTime = 0;
	endTime = 0;
    }

    public void saveToFileAndReset() {

	// save the entry: projName, stateCaptureTime, serializationTime, diffTime
	String entry = String.format("%s,%.3f,%.3f,%.3f\n", projName, stateCaptureTime,
				     serializationTime, diffTime);
	writeToFile(pathToLogFile, entry, true);
	
	// reset the times
	serializationTime = 0;
	stateCaptureTime = 0;
	diffTime = 0;
    }
    private void createHeader() {
	String header = "projName,stateCaptureTime, serializationTime, diffTime\n";
	writeToFile(pathToLogFile, header, false);
    }
}