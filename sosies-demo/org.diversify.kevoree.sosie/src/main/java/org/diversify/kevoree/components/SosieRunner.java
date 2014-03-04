package org.diversify.kevoree.components;

import org.kevoree.ContainerNode;
import org.kevoree.NetworkInfo;
import org.kevoree.NetworkProperty;
import org.kevoree.annotation.*;
import org.kevoree.api.Context;
import org.kevoree.api.ModelService;
import org.kevoree.api.Port;
import org.kevoree.komponents.helpers.ProcessStreamFileLogger;
import org.kevoree.log.Log;

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
    @Param(optional = true, defaultValue = "localhost")
    private String redisServer;
    @Param(optional = true, defaultValue = "6379")
    private int redisServerPort;
    @Output
    private Port sendNbSosieCalled;
    @KevoreeInject
    protected Context context;
    @KevoreeInject
    protected ModelService modelService;

    protected String sosieName;
    private Process process;
    private File directory;
    private String runnerPath;
    private File standardOutput;

    @Start
    public void start() throws Exception {
        if (sosieUrl.contains("composed-sosie-")) {
            sosieName = sosieUrl.substring(sosieUrl.indexOf("composed-sosie-") + "composed-sosie-".length(), sosieUrl.length() - ".zip".length());
            sosieName = sosieName.substring(sosieName.indexOf("-") + 1);
        } else if (sosieUrl.contains("ringo-")) {
            sosieName = sosieUrl.substring(sosieUrl.indexOf("ringo-") + "ringo-".length(), sosieUrl.length() - ".zip".length());
            sosieName = sosieName.substring(sosieName.indexOf("-") + 1);
        }
        directory = new File(System.getProperty("java.io.tmpdir") + File.separator + context.getInstanceName());
        if ((directory.isFile() && directory.delete() && directory.mkdirs()) || (!directory.exists() && directory.mkdirs()) || (directory.isDirectory())) {
            runnerPath = copyFileFromStream(this.getClass().getClassLoader().getResourceAsStream("runner.bash"), directory.getAbsolutePath(), "runner.bash", true, true);

            standardOutput = new File(directory.getAbsolutePath() + File.separator + context.getInstanceName() + ".log");
            standardOutput.createNewFile();
            process = new ProcessBuilder().directory(directory).command("bash", runnerPath, "get", sosieUrl, directory.getAbsolutePath()).redirectErrorStream(true).start();
            new Thread(new ProcessStreamFileLogger(process.getInputStream(), standardOutput, true)).start();

            int exitStatus = process.waitFor();
            if (exitStatus == 0) {
                process = new ProcessBuilder().directory(directory).command("bash", runnerPath, "run", directory.getAbsolutePath(), directory.getAbsolutePath() + File.separator + sosieName, port + "", redisServer, redisServerPort + "").redirectErrorStream(true).start();
                new Thread(new ProcessStreamFileLogger(process.getInputStream(), standardOutput, true)).start();
                try {
                    exitStatus = process.exitValue();
                    process = null;
                    throw new Exception("Unable to run runner script. Exit Status: " + exitStatus + " for '" + runnerPath + " run " + directory.getAbsolutePath() + " " + directory.getAbsolutePath() + File.separator + sosieName + " " + port + " " + redisServer + " " + redisServerPort + "'");
                } catch (IllegalThreadStateException ignored) {
                    Log.info("Sosie '{}' is started", context.getInstanceName());
                }
            } else {
                throw new Exception("Unable to download sosie. Exit status: " + exitStatus + " for 'bash " + runnerPath + " get " + sosieUrl + "'");
            }
        } else {
            throw new Exception("Unable to create the temporary folder to store sosie content");
        }
    }

    @Stop
    public void stop() throws Exception {
        Log.info("Stopping {} on {}", context.getInstanceName(), context.getNodeName());
        process = new ProcessBuilder().directory(directory).command("bash", runnerPath, "kill", port + "").redirectErrorStream(true).start();
        new Thread(new ProcessStreamFileLogger(process.getInputStream(), standardOutput, true)).start();
        if (process.waitFor() == 0) {
            process = new ProcessBuilder().directory(directory).command("bash", runnerPath, "clean", directory.getAbsolutePath()).redirectErrorStream(true).start();
            new Thread(new ProcessStreamFileLogger(process.getInputStream(), standardOutput, true)).start();
            if (process.waitFor() == 0) {
                process = null;
                standardOutput.delete();
            } else {
                throw new Exception("Unable to clean temporary folder for " + context.getInstanceName() + " on " + context.getNodeName());
            }
        } else {
            throw new Exception("Unable to stop " + context.getInstanceName() + " on " + context.getNodeName());
        }
    }

    @Input
    public void useless() {
    }

    private boolean isHostFromLocalNode(String host) {
        if (modelService.getCurrentModel() != null && modelService.getCurrentModel().getModel() != null) {
            ContainerNode node = modelService.getCurrentModel().getModel().findNodesByID(context.getNodeName());
            if (node != null) {
                for (NetworkInfo networkInfo : node.getNetworkInformation()) {
                    for (NetworkProperty networkProperty : networkInfo.getValues()) {
                        if (host.equals(networkProperty.getValue())) {
                            return true;
                        }
                    }
                }
            } else {
                Log.warn("{}: Unable to find the current node in current model", context.getInstanceName());
            }
        } else {
            Log.warn("{}: Unable to get current model from ModelService", context.getInstanceName());
        }
        return false;
    }

    @Input
    public void getNbSosieCalled(Object host) {
        if (host instanceof String) {
            String[] ipNPort = ((String) host).split(":");
            if (ipNPort.length == 2) {
                // check if port is the one of this component
                if (Integer.parseInt(ipNPort[1]) == port) {
                    // check if ip is the one of the localNode
                    if (isHostFromLocalNode(ipNPort[0])) {
                        BufferedReader reader = null;
                        int result = -1;
                        try {
                            reader = new BufferedReader(new FileReader(new File(directory.getAbsolutePath() + File.separator + "count")));
                            result = Integer.parseInt(reader.readLine());
                        } catch (FileNotFoundException e) {
                            Log.error("Unable to read the log file: {}", directory.getAbsolutePath() + File.separator + "count");
                        } catch (IOException e) {
                            Log.error("Unable to read the content of the file: {}", directory.getAbsolutePath() + File.separator + "count");
                        } catch (NumberFormatException e) {
                            Log.error("Unable to parse the number stored in the file: {}", directory.getAbsolutePath() + File.separator + "count");
                        } finally {
                            try {
                                if (reader != null) {
                                    reader.close();
                                }
                            } catch (IOException ignored) {
                            }
                        }
                        sendNbSosieCalled.send(result);
                    }
                }
            }
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
