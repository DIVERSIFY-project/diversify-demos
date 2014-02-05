/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.thingml.lbmonitor;

import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Collection;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

/**
 *
 * @author ffl
 */
public class LBWebSocketServer extends WebSocketServer {

    public LBWebSocketServer(int port) throws UnknownHostException {
        super(new InetSocketAddress(port));
    }

    public LBWebSocketServer(InetSocketAddress address) {
        super(address);
    }

    @Override
    public void onOpen(WebSocket ws, ClientHandshake ch) {
        System.out.println("[LBWebSocketServer] Open Client: " + ws.getRemoteSocketAddress().getAddress().getHostAddress());
    }

    @Override
    public void onClose(WebSocket ws, int i, String string, boolean bln) {
        System.out.println("[LBWebSocketServer] Close Client: " + ws.getRemoteSocketAddress().getAddress().getHostAddress());
    }

    @Override
    public void onMessage(WebSocket ws, String string) {
        System.out.println("[LBWebSocketServer] Message from " + ws.getRemoteSocketAddress().getAddress().getHostAddress() + " Data = " + string);
        // Just echo for now
        ws.send("Echo : " + string);

    }

    @Override
    public void onError(WebSocket ws, Exception excptn) {
        System.out.println("[LBWebSocketServer] Error ws = " + ws + " exception = " + excptn.getMessage());
        excptn.printStackTrace();
    }

    /**
     * Sends <var>text</var> to all currently connected WebSocket clients.
     *
     * @param text The String to send across the network.
     * @throws InterruptedException When socket related I/O errors occur.
     */
    public void sendToAll(String text) {
        Collection<WebSocket> con = connections();
        synchronized (con) {
            for (WebSocket c : con) {
                c.send(text);
            }
        }
    }

}
