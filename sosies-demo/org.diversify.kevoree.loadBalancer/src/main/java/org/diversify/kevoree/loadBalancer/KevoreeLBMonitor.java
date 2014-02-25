package org.diversify.kevoree.loadBalancer;

import org.java_websocket.WebSocketImpl;
import org.kevoree.annotation.*;
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

    @Output
    private Port getNbSosieCalled;

    @KevoreeInject
    ModelService modelservice;

    private LBWebSocketServer server;
    private AbstractLogReader logReader;
    private GetNbRequestCallback callback;


    @Start
    public void start() throws IOException {
        modelservice.registerModelListener(this);

        File folderWhereExtract = new File(pathWhereExtract);
        if (!folderWhereExtract.exists()) {
            folderWhereExtract.mkdirs();
        }
        KevoreeLBMonitorWebContentExtractor.getInstance().extractConfiguration(folderWhereExtract.getAbsolutePath(), true);
        if (!"localhost".equals(serverName))
            KevoreeLBMonitorWebContentExtractor.getInstance().replaceFileString("localhost", serverName, folderWhereExtract.getAbsolutePath());

        WebSocketImpl.DEBUG = false;

        server = new LBWebSocketServer(port);
        server.start();
        //logReader = new LogReader(server, logFile);
        //logReader.startReader();
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
        callback = new GetNbRequestCallback();
        logReader.startReader();
    }

    @Input
    public void receiveNbSosieCalled(Object result) {
        if (result instanceof Integer) {
            callback.receive((Integer) result);
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
            try {
                BufferedReader reader = new BufferedReader(new FileReader(new File(logFilePath)));

                while (!stop) {
                    while (reader.ready()) {
                        String content = reader.readLine();
                        if (!content.split(";")[2].trim().equals("-")) {
                            String[] hosts = content.split(";")[2].trim().split(",");
                            for (String host : hosts) {
                                callback.initialize();
                                getNbSosieCalled.send(host);
                                String toSend = content.replace(content.split(";")[2], host) + "; " + callback.getNbSosieCalled();
                                System.err.println("toSend: " + toSend);
                                server.sendToAll(toSend);
                            }
                        }
                    }
                }
                reader.close();
            } catch (FileNotFoundException ignored) {
                ignored.printStackTrace();
            } catch (IOException ignored) {
                ignored.printStackTrace();
            }
        }
    }

    private class GetNbRequestCallback {
        int nbSosieCalled;
        boolean received;

        synchronized void initialize() {
            received = false;
        }

        synchronized void receive(int nbSosieCalled) {
            this.nbSosieCalled = nbSosieCalled;
            received = true;
            this.notify();
        }

        synchronized int getNbSosieCalled() {
            if (!received) {
                try {
                    this.wait();
                } catch (InterruptedException ignored) {
                    ignored.printStackTrace();
                }
            }
            return nbSosieCalled;

        }
    }
}
