package org.diversify.kevoree.loadBalancer;

import org.java_websocket.WebSocketImpl;
import org.json.JSONException;
import org.json.JSONObject;
import org.kevoree.annotation.*;
import org.kevoree.api.Context;
import org.kevoree.api.ModelService;
import org.kevoree.api.Port;
import org.kevoree.api.handler.ModelListenerAdapter;
import org.kevoree.log.Log;
import org.thingml.lbmonitor.AbstractLogReader;
import org.thingml.lbmonitor.LBWebSocketServer;

import java.io.*;

/**
 * User: Erwan Daubert - erwan.daubert@gmail.com
 * Date: 18/02/14
 * Time: 15:39
 *
 * @author Erwan Daubert
 * @version 1.0
 */
@ComponentType
public class KevoreeLBMonitor extends ModelListenerAdapter {

    @Param(optional = true, defaultValue = "8099")
    private int port;
    @Param(optional = true, defaultValue = "localhost")
    private String serverName;
    @Param(optional = true, defaultValue = "/tmp/loadbalancerclient/proxy.log")
    private String logFile;
    @Param(optional = true, defaultValue = "/tmp/loadbalancerclient/")
    private String pathWhereExtract;
    @Output(optional = true)
    private Port notifyRequest;

    @KevoreeInject
    ModelService modelService;
    @KevoreeInject
    Context context;

    private LBWebSocketServer server;
    private AbstractLogReader logReader;


    @Start
    public void start() throws IOException {
        modelService.registerModelListener(this);

        File folderWhereExtract = new File(pathWhereExtract);
        if (!folderWhereExtract.exists()) {
            folderWhereExtract.mkdirs();
        }
//        System.out.println(InetAddress.getByName(serverName).getHostAddress());
        KevoreeLBMonitorWebContentExtractor.getInstance().extractConfiguration(folderWhereExtract.getAbsolutePath(), true);
        KevoreeLBMonitorWebContentExtractor.getInstance().replaceFileString("localhost:8099", serverName + ":80/client/ws", folderWhereExtract.getAbsolutePath());
        KevoreeLBMonitorWebContentExtractor.getInstance().replaceServerAndPortString("localhost", serverName, "8099", "80/client/ws", folderWhereExtract.getAbsolutePath());

        WebSocketImpl.DEBUG = false;
        server = new LBWebSocketServer(port);
        server.start();

        new File(logFile).delete();

        Log.info("[LBWebSocketServer] Server started on port {}", server.getPort());
    }

    @Stop
    public void stop() throws IOException, InterruptedException {
        if (logReader != null) {
            logReader.stopReader();
        }
        if (server != null) {
            server.stop();
        }
        KevoreeLBMonitorWebContentExtractor.getInstance().deleteConfiguration(pathWhereExtract);
    }

    @Override
    public void modelUpdated() {
        modelService.unregisterModelListener(this);
        logReader = new LogReader(server, logFile);
        logReader.startReader();
    }

    @Input(optional = false)
    public synchronized void receiveSosieInformation(Object result) {
        Log.warn("Receive information from a sosie");
        if (result instanceof String) {
            //register file corresponding to the sosie
            try {
                JSONObject jsonReader = new JSONObject(result.toString());
                String nodeId = jsonReader.get("node").toString();
                String information = jsonReader.get("information").toString();
                OutputStream stream = new FileOutputStream(new File(pathWhereExtract + File.separator + "client" + File.separator + nodeId + ".txt"));
                stream.write(information.getBytes("UTF-8"));
                stream.flush();
                stream.close();

                Log.warn("Information about the sosie stored on {}", pathWhereExtract + File.separator + "client" + File.separator + nodeId + ".txt");
                server.sendToAll("update=" + nodeId);
            } catch (JSONException ignored) {
                ignored.printStackTrace();
            } catch (FileNotFoundException ignored) {
                ignored.printStackTrace();
            } catch (UnsupportedEncodingException ignored) {
                ignored.printStackTrace();
            } catch (IOException ignored) {
                ignored.printStackTrace();
            }
        }
    }

    private class LogReader extends AbstractLogReader {

        private String logFilePath;

        public LogReader(LBWebSocketServer server, String logFilePath) {
            super(server);
            this.logFilePath = logFilePath;
        }

        @Override
        public void run() {
            BufferedReader reader = null;
            while (reader == null) {
                try {
                    reader = new BufferedReader(new FileReader(new File(logFilePath)));
                } catch (FileNotFoundException ignored) {}
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ignored1) {
                }
            }
            while (!stop) {
                try {
                    while (reader.ready()) {
                        String content = reader.readLine();
                        try {
                            if (content.split(";").length >= 2 && !content.split(";")[2].trim().equals("-")) {
                                String[] hosts = content.split(";")[2].trim().split(",");
                                for (String host : hosts) {
                                    String toSend = content.replace(content.split(";")[2], host);
                                    notifyRequest.send(toSend);
                                    server.sendToAll(toSend);
                                }
                            }
                        } catch (Throwable ignored) {
                            ignored.printStackTrace();
                        }
                    }
                } catch (IOException ignored) {
                    ignored.printStackTrace();
                }
            }
            try {
                reader.close();
            } catch (IOException ignored) {
            }
        }
    }
}
