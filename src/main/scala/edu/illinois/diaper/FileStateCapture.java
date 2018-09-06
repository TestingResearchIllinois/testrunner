package edu.illinois.diaper;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public class FileStateCapture extends StateCapture {

    public FileStateCapture(String testName) {
        super(testName);
    }

    public static IStateCapture instanceFor(String entityName) {
        return new FileStateCapture(entityName);
    }

    private long id;

    private static Map<String, Integer> file2hash = new HashMap<String, Integer>();

    // Opens up file name and hashes the String contents
    private int hashFile(String filename) {
        StringBuilder sb = new StringBuilder();
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(new File(filename))));
            String s = br.readLine();
            while (s != null) {
                sb.append(s);
                s = br.readLine();
            }
            br.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return sb.toString().hashCode();
    }

    // Recursively get all files in in a directory, added to the passed in list
    private List<String> getFileNames(List<String> fileNames, Path dir) {
        try {
            DirectoryStream<Path> stream = Files.newDirectoryStream(dir);
            for (Path path : stream) {
                if (path.toFile().isDirectory()) {
                    getFileNames(fileNames, path);
                }
                else {
                    fileNames.add(path.toAbsolutePath().toString());
                }
            }
            stream.close();
        } catch(IOException e) {
            e.printStackTrace();
        }

        return fileNames;
    }

    private List<String> getModifiedFiles(InputStream stream) {
        List<String> modified = new ArrayList<String>();
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(stream));
            String s = br.readLine();
            while (s != null) {
                // These files should be ignored, they can show up in the newer command
                if (s.trim().equals(".") || s.contains("LOG_") || s.contains("NOW-") || s.trim().equals("/tmp") || s.contains("hsperfdata_")) {
                    s = br.readLine();
                    continue;
                }
                // Save name of modified file
                modified.add(s);

                s = br.readLine();
            }
            br.close();
        } catch(IOException e) {
            System.out.println("AUGUST SHI EXCEPTION");
            e.printStackTrace();
        }

        return modified;
    }

    // Checks the newer files and see if there are any hashes that are changed
    private List<String> getChangedFiles() {
        List<String> modified = new ArrayList<String>();
        try {
            // Find newer in /tmp and in .
            Process p = Runtime.getRuntime().exec("find /tmp -newer NOW-" + id);
            modified = getModifiedFiles(p.getInputStream());
            p = Runtime.getRuntime().exec("find . -newer NOW-" + id);
            modified.addAll(getModifiedFiles(p.getInputStream()));

        } catch(IOException e) {
            System.out.println("AUGUST SHI EXCEPTION");
            e.printStackTrace();
        }

        /*// For each one, check if the hashing map has the file on record
        List<String> toRemove = new ArrayList<String>();    // Non-modified files to remove at end
        for (String filename : modified) {
            // If in map, need to check if contents changed
            if (file2hash.containsKey(filename)) {
                int hashcode = hashFile(filename);
                // Contents did not change, should not consider this file
                if (hashcode == file2hash.get(filename)) {
                    toRemove.add(filename);
                }
            }
        }
        modified.removeAll(toRemove);
        // Modified files have new hashes, incrementally update mapping
        for (String filename : modified) {
            file2hash.put(filename, hashFile(filename));
        }

        // Next, check all previously hashed files and see if they still exist
        // Deleted files should also be flagged as pollution
        for (String filename : file2hash.keySet()) {
            File f = new File(filename);
            // Previously hashed file was deleted
            if (!f.exists()) {
                modified.add(filename);
                // Remove from mapping
                file2hash.remove(filename);
            }
        }*/

        return modified;
    }

    @Override
    public void runCapture() {
        // Add dummy placeholder for state
        this.currentTestStates.add("");
        this.currentRoots.add(new HashSet<String>());

        // If we have already captured MAX_NUM states for the current test, we can check if there are changed files
        if (currentTestStates.size() == MAX_NUM) {
            List<String> changed = getChangedFiles();

            // Output if file changed in same format as with heap
            boolean changedFiles = changed.isEmpty();
            StringBuilder sb = new StringBuilder();
            sb.append(testName);
            sb.append(" ");
            sb.append(changedFiles);
            sb.append('\n');
            for (String diffFile : changed) {
                sb.append("***********************\n");
                sb.append(diffFile);
                sb.append('\n');
                sb.append("***********************\n");
            }

            // Only do bbaa
            writeToFile("LOG_bbaa_file", sb.toString(), true);
        }
        else if (currentTestStates.size() == 1) {
            try {
                // First populate map of hashes for files
                // Restrict to /tmp and .
                // Only populate if it starts off empty (needs to be "seeded") for first run
                /*if (file2hash.isEmpty()) {
                    System.out.println("AUGUST SHI SEED");
                    List<String> fileNames = getFileNames(new ArrayList<String>(), Paths.get("/tmp"));
                    fileNames = getFileNames(fileNames, Paths.get("."));

                    // Add has of each file into map
                    for (String file : fileNames) {
                        file2hash.put(file, hashFile(file));
                    }
                }*/

                // Create the timestamp file
                id = System.currentTimeMillis();
                Runtime.getRuntime().exec("touch NOW-" + id);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
