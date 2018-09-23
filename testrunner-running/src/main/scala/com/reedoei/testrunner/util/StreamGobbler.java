package com.reedoei.testrunner.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class StreamGobbler extends Thread {
    private InputStream is;

    public StreamGobbler(final InputStream is) {
        this.is = is;
    }

    @Override
    public void run() {
        try {
            final BufferedReader br = new BufferedReader(new InputStreamReader(is));

            while (true) {
                if (br.readLine() == null) break;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
