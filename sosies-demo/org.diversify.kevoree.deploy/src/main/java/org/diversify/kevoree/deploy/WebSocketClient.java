package org.diversify.kevoree.deploy;

import org.java_websocket.exceptions.WebsocketNotConnectedException;
import org.java_websocket.handshake.ServerHandshake;
import org.kevoree.ContainerNode;
import org.kevoree.ContainerRoot;
import org.kevoree.NetworkInfo;
import org.kevoree.NetworkProperty;
import org.kevoree.compare.DefaultModelCompare;
import org.kevoree.kevscript.KevScriptEngine;
import org.kevoree.loader.JSONModelLoader;
import org.kevoree.modeling.api.trace.TraceSequence;

import java.net.URI;
import java.util.List;

/**
 * User: Erwan Daubert - erwan.daubert@gmail.com
 * Date: 10/03/14
 * Time: 11:02
 *
 * @author Erwan Daubert
 * @version 1.0
 */
public class WebSocketClient extends org.java_websocket.client.WebSocketClient {

    private boolean done;
    private ContainerRoot model;

    public WebSocketClient(URI serverURI) {
        super(serverURI);
    }

    @Override
    public void onOpen(ServerHandshake serverHandshake) {
    }

    @Override
    public synchronized void onMessage(String s) {
        model = (ContainerRoot) new JSONModelLoader().loadModelFromString(s).get(0);
        this.notify();
//        System.out.println("onMessage: " + s);
    }

    @Override
    public void onClose(int i, String s, boolean b) {

    }

    @Override
    public void onError(Exception e) {

    }

    public synchronized boolean waitFor(ContainerRoot model, int timeout, int nbTry) {
        while (!done && nbTry > 0) {
            this.model = null;
            try {
                this.send("pull");
                this.wait(timeout);
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (WebsocketNotConnectedException ignored) {}
            if (this.model != null) {
                TraceSequence traces = new DefaultModelCompare().diff(model, this.model);
                done = traces.getTraces().size() <= 0;
            }
        }
        return done;
    }

    public synchronized boolean waitForIps(ContainerRoot model, List<ContainerNode> nodes, int timeout, int nbTry) {
        while (!done && nbTry > 0) {
            this.model = null;
            this.send("pull");
            try {
                this.wait(timeout);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if (this.model != null) {
                boolean allIpExist = true;
                StringBuilder script = new StringBuilder();
                for (ContainerNode n : nodes) {
                    ContainerNode node = this.model.findNodesByID(n.getName());
                    if (node != null) {
                        boolean ipExists = false;
                        for (NetworkInfo ni : node.getNetworkInformation()) {
                            for (NetworkProperty np : ni.getValues()) {
                                if (ni.getName().contains("ip") || np.getName().contains("ip")) {
                                    script.append("network ").append(node.getName()).append(".").append(ni.getName()).append(".").append(np.getName()).append(" ").append(np.getValue()).append("\n");
                                    ipExists = true;
                                    break;
                                }
                            }
                        }
                        if (!ipExists) {
                            allIpExist = false;
                            break;
                        }
                    } else {
                        allIpExist = false;
                    }
                }
                if (allIpExist) {
                    System.out.println("script: " + script.toString());
                    KevScriptEngine engine = new KevScriptEngine();
                    try {
                        engine.execute(script.toString(), model);
                        return true;
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        return false;
    }
}
