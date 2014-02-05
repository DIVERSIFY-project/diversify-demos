/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.thingml.lbmonitor;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import org.java_websocket.WebSocketImpl;

/**
 *
 * @author ffl
 */
public class LBMonitor {

    public static void main(String[] args) throws Exception{
        WebSocketImpl.DEBUG = false;
        int port = 8099; 
      /*
        try {
            port = Integer.parseInt(args[0]);
        } catch (Exception ex) {
        }
        */
        
        LBWebSocketServer server = new LBWebSocketServer(port);
        server.start();
        LogReaderSimulator simulator = new LogReaderSimulator(server);
        simulator.startReader();
        System.out.println("[LBWebSocketServer] Server stated on port " + server.getPort());

        BufferedReader sysin = new BufferedReader(new InputStreamReader(System.in));
        System.out.println("Enter 'exit' to quit or 'restart' to restart the web socket server...");
        while (true) {
           
            System.out.print("LBWebSocketServer>");
            String in = sysin.readLine();
            server.sendToAll(in);
            if (in.equals("exit")) {
                server.stop();
                break;
            } else if (in.equals("restart")) {
                server.stop();
                server.start();
                break;
            }
        }
        simulator.stopReader();
    }
}
