package org.diversify.kevoree.loadBalancer;

import org.java_websocket.WebSocketImpl;
import org.kevoree.annotation.ComponentType;
import org.kevoree.annotation.Param;
import org.kevoree.annotation.Start;
import org.kevoree.annotation.Stop;
import org.kevoree.api.handler.ModelListenerAdapter;
import org.kevoree.log.Log;
import org.thingml.lbmonitor.AbstractLogReader;
import org.thingml.lbmonitor.LBWebSocketServer;
import org.thingml.lbmonitor.LogReader;

import java.io.File;
import java.io.IOException;

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
    @Param(optional = true, defaultValue = "/tmp/loadbalancerclient/proxy.log")
    private String logFile;
    @Param(optional = true, defaultValue = "/tmp/loadbalancerclient/")
    private String pathWhereExtract;

    private LBWebSocketServer server;
    private AbstractLogReader logReader;

    @Start
    public void start() throws IOException {
        File folderWhereExtract = new File(pathWhereExtract);
        if (!folderWhereExtract.exists()) {
            folderWhereExtract.mkdirs();
        }
        KevoreeLBMonitorWebContentExtractor.extractConfiguration(folderWhereExtract.getAbsolutePath(), true);
        WebSocketImpl.DEBUG = false;
        server = new LBWebSocketServer(port);
        server.start();

        Log.info("[LBWebSocketServer] Server started on port {}", server.getPort());
    }

    @Stop
    public void stop() throws IOException, InterruptedException {
        server.stop();
        logReader.stopReader();
    }


    @Override
    public void modelUpdated() {
        logReader = new LogReader(server, logFile);

        logReader.startReader();
    }
}
