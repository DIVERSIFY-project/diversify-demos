package org.diversify.kevoree.loadBalancer;

import org.java_websocket.WebSocketImpl;
import org.kevoree.annotation.*;
import org.kevoree.api.ModelService;
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
public class KevoreeSimpleLBMonitor extends ModelListenerAdapter {

    @Param(optional = true, defaultValue = "8099")
    private int port;
    @Param(optional = true, defaultValue = "localhost")
    private String serverName;
    @Param(optional = true, defaultValue = "/tmp/loadbalancerclient/proxy.log")
    private String logFile;
    @Param(optional = true, defaultValue = "/tmp/loadbalancerclient/")
    private String pathWhereExtract;


    @KevoreeInject
    ModelService modelservice;

    private LBWebSocketServer server;
    private AbstractLogReader logReader;


    @Start
    public void start() throws IOException {
        modelservice.registerModelListener(this);

        File folderWhereExtract = new File(pathWhereExtract);
        if (!folderWhereExtract.exists()) {
            folderWhereExtract.mkdirs();
        }
        KevoreeLBMonitorWebContentExtractor.getInstance().extractConfiguration(folderWhereExtract.getAbsolutePath(), true);
        KevoreeLBMonitorWebContentExtractor.getInstance().replaceFileString("localhost:8099", serverName + ":80/client/ws", folderWhereExtract.getAbsolutePath());

        WebSocketImpl.DEBUG = false;

        server = new LBWebSocketServer(port);
        server.start();
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
    }


    @Override
    public void modelUpdated() {
        modelservice.unregisterModelListener(this);
        logReader = new LogReader(server, logFile);
        logReader.startReader();
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
                } catch (FileNotFoundException ignored) {
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException ignored1) {
                    }
                }
            }

            while (!stop) {
                try {
                    while (reader.ready()) {
                        String content = reader.readLine();
                        server.sendToAll(content);
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
