package org.diversify.kevoree.loadBalancer;

import org.java_websocket.WebSocketImpl;
import org.kevoree.annotation.ComponentType;
import org.kevoree.annotation.Param;
import org.kevoree.annotation.Start;
import org.kevoree.annotation.Stop;
import org.kevoree.log.Log;
import org.thingml.lbmonitor.AbstractLogReader;
import org.thingml.lbmonitor.LBWebSocketServer;
import org.thingml.lbmonitor.LogReader;

import java.io.IOException;
import java.net.UnknownHostException;

/**
 * User: Erwan Daubert - erwan.daubert@gmail.com
 * Date: 18/02/14
 * Time: 15:39
 *
 * @author Erwan Daubert
 * @version 1.0
 */
@ComponentType
public class KevoreeLBMonitor {

    @Param(optional = true, defaultValue = "8099")
    private int port;
    @Param(optional = true, defaultValue = "/var/www/proxy.log")
    private String logFile;

    private LBWebSocketServer server;
    private AbstractLogReader logReader;

    @Start
    public void start() throws UnknownHostException {
        WebSocketImpl.DEBUG = false;
        server = new LBWebSocketServer(port);
        server.start();

        logReader = new LogReader(server, logFile);

        logReader.startReader();
        Log.info("[LBWebSocketServer] Server started on port {}", server.getPort());
    }

    @Stop
    public void stop() throws IOException, InterruptedException {
        server.stop();
        logReader.stopReader();
    }
}
