package org.diversify.kevoree.components;

import java.io.*;

/**
 * User: Erwan Daubert - erwan.daubert@gmail.com
 * Date: 23/03/12
 * Time: 10:05
 *
 * @author Erwan Daubert
 * @version 1.0
 */

public class ProcessStreamFileLogger implements Runnable {

    private InputStream inputStream;
    private File file;

    public ProcessStreamFileLogger(InputStream inputStream, File file) {
        this.file = file;
        this.inputStream = inputStream;
    }

    @Override
    public void run() {
        if (inputStream != null || file != null) {
            FileWriter outputStream = null;
            BufferedReader readerIn = null;
            try {
                outputStream = new FileWriter(file);
                readerIn = new BufferedReader(new InputStreamReader(inputStream));
                if (!file.exists()) {
                    file.createNewFile();
                }
                String lineIn = readerIn.readLine();
                while (lineIn != null) {
                    outputStream.write(lineIn + "\n");
                    outputStream.flush();
                    lineIn = readerIn.readLine();
                }
            } catch (Exception e) {

            } finally {
                if (outputStream != null) {
                    try {
                        outputStream.flush();
                        outputStream.close();
                    } catch (IOException e) {
                    }
                }
                if (readerIn != null) {
                    try {
                        readerIn.close();
                    } catch (IOException e) {

                    }
                }
            }
        }
    }
}
