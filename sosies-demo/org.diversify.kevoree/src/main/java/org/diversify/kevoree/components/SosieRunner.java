package org.diversify.kevoree.components;

import org.kevoree.annotation.*;
import org.kevoree.api.Context;

import java.io.*;

/**
 * User: Erwan Daubert - erwan.daubert@gmail.com
 * Date: 18/02/14
 * Time: 11:41
 *
 * @author Erwan Daubert
 * @version 1.0
 */
@ComponentType
public class SosieRunner {

    @Param(optional = true, defaultValue = "8080")
    protected int port;
    @Param(optional = false)
    protected String sosieUrl;
    @Param(optional = false)
    protected String sosieName;
    @KevoreeInject
    protected Context context;

    private Process process;
    private File directory;
    private String runnerPath;
    private File standardOutput;

    @Start
    public void start() throws Exception {
        directory = File.createTempFile("sosie", context.getInstanceName());
        if (directory.delete() && directory.mkdirs()) {
            runnerPath = copyFileFromStream(this.getClass().getClassLoader().getResourceAsStream("runner.bash"), directory.getAbsolutePath(), "runner.bash", true, true);

            standardOutput = new File(directory.getAbsolutePath() + File.separator + context.getInstanceName() + ".log");
            standardOutput.createNewFile();
            process = new ProcessBuilder().directory(directory).command("bash", runnerPath, "get", sosieUrl, directory.getAbsolutePath()).redirectErrorStream(true).start();
            new Thread(new ProcessStreamFileLogger(process.getInputStream(), standardOutput)).start();

            int exitStatus = process.waitFor();
            if (exitStatus == 0) {
                process = new ProcessBuilder().directory(directory).command("bash", runnerPath, "run", directory.getAbsolutePath() + File.separator + sosieName, port + "").redirectErrorStream(true).start();
                new Thread(new ProcessStreamFileLogger(process.getInputStream(), standardOutput)).start();
                try {
                    exitStatus = process.exitValue();
                    process = null;
                    throw new Exception("Unable to run runner script. Exit Status: " + exitStatus + " for '" + runnerPath + " run " + directory.getAbsolutePath() + " " + port +"'");
                } catch (IllegalThreadStateException ignored) {
                }
            } else {
                throw new Exception("Unable to download sosie. Exit status: " + exitStatus + " for 'bash " + runnerPath + " get " + sosieUrl + "'");
            }
        }
    }

    @Stop
    public void stop() throws IOException, InterruptedException {
        if (process != null) {
            process = new ProcessBuilder().directory(directory).command("bash", runnerPath, "kill", port + "").redirectErrorStream(true).start();
            new Thread(new ProcessStreamFileLogger(process.getInputStream(), standardOutput)).start();
            if (process.waitFor() == 0) {
                process = new ProcessBuilder().directory(directory).command("bash", runnerPath, "clean", directory.getAbsolutePath()).redirectErrorStream(true).start();
                new Thread(new ProcessStreamFileLogger(process.getInputStream(), standardOutput)).start();
                if (process.waitFor() == 0) {
                    process = null;
                    standardOutput.delete();
                }
            }
        }
    }

    @Input
    public void useless() {}

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
