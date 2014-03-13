package org.diversify.kevoree.components;

import org.json.JSONObject;
import org.json.JSONStringer;
import org.kevoree.ComponentInstance;
import org.kevoree.ContainerNode;
import org.kevoree.NetworkInfo;
import org.kevoree.NetworkProperty;
import org.kevoree.annotation.*;
import org.kevoree.api.BootstrapService;
import org.kevoree.api.Context;
import org.kevoree.api.ModelService;
import org.kevoree.api.Port;
import org.kevoree.komponents.helpers.ProcessStreamFileLogger;
import org.kevoree.log.Log;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

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
    private Port sendSosieInformation;
    @KevoreeInject
    protected Context context;
    @KevoreeInject
    protected ModelService modelService;
    @KevoreeInject
    private BootstrapService bootstrapService;

    protected String sosieName;
    private Process process;
    private File directory;
    private String runnerPath;
    private File standardOutput;

    private final String rhinoRepoUrl = "http://sd-35000.dedibox.fr:8080/archiva/repository/internal/";

    public void setSosieUrl(String sosieUrl) throws Exception {

        boolean needUpdate = this.sosieUrl != null && ((ComponentInstance) (((ContainerNode) modelService.getCurrentModel().getModel().findNodesByID(context.getNodeName())).findComponentsByID(context.getInstanceName()))).getStarted();
        this.sosieUrl = sosieUrl;
        if (needUpdate) {
            stop();
            start();
        }
    }

    @Start
    public void start() throws Exception {
        if (sosieUrl.contains("composed-sosie-")) {
            sosieName = sosieUrl.substring(sosieUrl.indexOf("composed-sosie-") + "composed-sosie-".length(), sosieUrl.length() - ".zip".length());
            sosieName = sosieName.substring(sosieName.indexOf("-") + 1);
        } else if (sosieUrl.contains("ringo-")) {
            sosieName = sosieUrl.substring(sosieUrl.indexOf("ringo-") + "ringo-".length(), sosieUrl.length() - ".zip".length());
            sosieName = sosieName.substring(sosieName.indexOf("-") + 1);
        } else {
            sosieName = "ringojs-0.10";
        }
        if (sosieName.equalsIgnoreCase("regular")) {
            sosieName = "ringojs-0.10";
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
                    // send information about the sosie
                    sendInformation();

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
            process = new ProcessBuilder().directory(directory).command("bash", runnerPath, "isRunning", port + "").redirectErrorStream(true).start();
            new Thread(new ProcessStreamFileLogger(process.getInputStream(), standardOutput, true)).start();
            if (process.waitFor() == 0) {
                throw new Exception("Unable to stop " + context.getInstanceName() + " on " + context.getNodeName());
            }
        }
    }

    @Input
    public void useless() {
    }

    private void sendInformation() throws Exception {
        if (sosieName.contains("rhino")) {
            String rhinoVersion = sosieName.substring(sosieName.indexOf("rhino"));

            standardOutput = new File(directory.getAbsolutePath() + File.separator + context.getInstanceName() + ".log");
            standardOutput.createNewFile();
            process = new ProcessBuilder().directory(directory).command("bash", runnerPath, "get", rhinoRepoUrl + "org.diversify/rhino/1-" + rhinoVersion + "/rhino-1-" + rhinoVersion + ".zip", directory.getAbsolutePath()).redirectErrorStream(true).start();
            new Thread(new ProcessStreamFileLogger(process.getInputStream(), standardOutput, true)).start();
            if (process.waitFor() == 0) {
                process = null;
                standardOutput.delete();

                FileInputStream inputStream = new FileInputStream(new File(directory.getAbsolutePath() + File.separator + rhinoVersion + File.separator + "diversificationPoint" + rhinoVersion.replace("rhino", "")));
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

                byte[] bytes = new byte[2048];
                int length = inputStream.read(bytes);

                while (length != -1) {
                    outputStream.write(bytes, 0, length);
                    length = inputStream.read(bytes);
                }

                JSONObject jsonReader = new JSONObject(new String(outputStream.toByteArray()));

                JSONObject fragmentPosition = ((JSONObject) jsonReader.get("CodeFragmentPosition"));
                JSONObject fragmentReplace = ((JSONObject) jsonReader.get("CodeFragmentReplace"));


                StringBuilder info = new StringBuilder();
                info.append("Position: ");
                info.append(fragmentPosition.get("Position"));
                info.append("\nStatementType: ");
                info.append(((String) fragmentPosition.get("Type")).replace("Ct", "").replace("Impl", "")).append(" -> ");
                info.append(((String) fragmentReplace.get("Type")).replace("Ct", "").replace("Impl", ""));


                String message = new JSONStringer().object().key("node").value(getIps().get(0).replace(".", "_") + "_" + port).key("information").value(info.toString()).endObject().toString();

                Log.info("Sending information about sosie: {}", message);
                sendSosieInformation.send(message);
            } else {
                throw new Exception("Unable to download Rhino sosie " + rhinoVersion + " for " + context.getInstanceName() + " on " + context.getNodeName());
            }
        } else {
            String message = new JSONStringer().object().key("node").value(getIps().get(0).replace(".", "_") + "_" + port).key("information").value("Regular version of rhino and ringo").endObject().toString();

            Log.info("Sending information about sosie: {}", message);
            sendSosieInformation.send(message);
        }
    }


    private List<String> getIps() {
        List<String> ips = new ArrayList<String>();
        if (modelService.getPendingModel() != null) {
            ContainerNode node = modelService.getPendingModel().findNodesByID(context.getNodeName());
            if (node != null) {
                for (NetworkInfo networkInfo : node.getNetworkInformation()) {
                    for (NetworkProperty networkProperty : networkInfo.getValues()) {
                        if (networkInfo.getName().equalsIgnoreCase("ip") || networkProperty.getName().equalsIgnoreCase("ip")) {
                            ips.add(networkProperty.getValue());
                        }
                    }
                }
            } else {
                Log.warn("{}: Unable to find the current node in current model", context.getInstanceName());
            }
        } else {
            Log.warn("{}: Unable to get current model from ModelService", context.getInstanceName());
        }
        return ips;
    }
    /*private boolean isHostFromLocalNode(String host) {
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
    }*/

    // FIXME must be replaced
    /*@Input
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
                        sendSosieInformation.send(result);
                    }
                }
            }
        }
    }*/

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
