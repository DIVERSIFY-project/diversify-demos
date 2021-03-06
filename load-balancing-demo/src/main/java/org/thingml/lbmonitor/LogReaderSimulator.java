/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.thingml.lbmonitor;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author ffl
 */
public class LogReaderSimulator extends AbstractLogReader {
    
    public LogReaderSimulator(LBWebSocketServer server) {
        super(server);
    }

    public void run() {

        while (!stop) {

            InputStream sample_log = LogReaderSimulator.class.getClassLoader().getResourceAsStream("proxy.log");
            BufferedReader log_reader = new BufferedReader(new InputStreamReader(sample_log));

            try {
                while (true) {
                    String line = log_reader.readLine();
                    if (line == null) {
                        break;
                    }
                    
                    
                    
                    server.sendToAll(line);
                    int t = 250 + (int) (Math.random() * 1500);
                    Thread.sleep(t);
                }
                
                log_reader.close();
                sample_log.close();
            } catch (Exception e) {
                e.printStackTrace();
            }

        }

    }

}
