package org.diversify.kevoree.components;

import org.kevoree.annotation.*;
import org.kevoree.api.Context;
import org.kevoree.log.Log;

import java.io.*;

/**
 * User: Erwan Daubert - erwan.daubert@gmail.com
 * Date: 05/02/14
 * Time: 15:10
 *
 * @author Erwan Daubert
 * @version 1.0
 */
@ComponentType
public class SosieRunner {

    @Param
    protected String script;

    @KevoreeInject
    protected Context context;

    private Process process;
    private File directory;

    @Start
    public void start() throws Exception {
        directory = File.createTempFile("sosie", context.getInstanceName());
        directory.delete();
        directory.mkdirs();
        String configuratorPath = copyFileFromStream(getClass().getClassLoader().getResourceAsStream("configurator.bash"), directory.getAbsolutePath(), "configurator.bash", true, true);
        String runnerPath = copyFileFromStream(getClass().getClassLoader().getResourceAsStream("runner.bash"), directory.getAbsolutePath(), "runner.bash", true, true);

        File standardOutput = File.createTempFile(context.getInstanceName(), ".log");
        process = new ProcessBuilder().directory(directory).command("bash", configuratorPath).redirectErrorStream(true).start();
        new Thread(new ProcessStreamFileLogger(process.getInputStream(), standardOutput)).start();

        int exitStatus = process.waitFor();
        if (exitStatus == 0) {
            process = new ProcessBuilder().directory(directory).command("bash", runnerPath).redirectErrorStream(true).start();
            new Thread(new ProcessStreamFileLogger(process.getInputStream(), standardOutput)).start();
            try {
                exitStatus = process.exitValue();
                throw new Exception("Unable to run runner script. Exit Status: " + exitStatus + " for " + runnerPath);
            } catch (IllegalThreadStateException ignored) {
            }
        } else {
            throw new Exception("Unable to run configurator script. Exit status: " + exitStatus + " for " + configuratorPath);
        }
    }

    @Stop
    public void stop() throws IOException, InterruptedException {
        try {
            process.exitValue();
        } catch (IllegalThreadStateException ignored) {
            process.destroy();
        }

        String cleanerPath = copyFileFromStream(getClass().getClassLoader().getResourceAsStream("cleaner.bash"), directory.getAbsolutePath(), "cleaner.bash", true, true);
        File standardOutput = File.createTempFile(context.getInstanceName(), ".log");
        process = new ProcessBuilder().directory(directory).command("bash", cleanerPath).redirectErrorStream(true).start();
        new Thread(new ProcessStreamFileLogger(process.getInputStream(), standardOutput)).start();

        int exitStatus = process.waitFor();
        if (exitStatus != 0 && directory.delete()) {
            Log.warn("Unable to clean configuration: " + directory.getAbsolutePath());
        }
    }

    public static String copyFileFromStream(InputStream inputStream, String path, String targetName, boolean replace, boolean executable) throws IOException {

        if (inputStream != null) {
            File copy = new File(path + File.separator + targetName);
            copy.mkdirs();
            if (replace) {
                if (copy.exists()) {
                    if (!copy.delete()) {
                        throw new IOException("delete file " + copy.getPath());
                    }
                    if (!copy.createNewFile()) {
                        throw new IOException("createNewFile file " + copy.getPath());
                    }
                }
            }
            OutputStream outputStream = new FileOutputStream(copy);
            byte[] bytes = new byte[1024];
            int length = inputStream.read(bytes);

            while (length > -1) {
                outputStream.write(bytes, 0, length);
                length = inputStream.read(bytes);
            }
            inputStream.close();
            outputStream.flush();
            outputStream.close();

            copy.setExecutable(executable);

            return copy.getAbsolutePath();
        }
        return null;
    }
}
