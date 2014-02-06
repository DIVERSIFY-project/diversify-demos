/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.thingml.lbmonitor;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author ffl
 */
public class LogReader extends AbstractLogReader {

    String log_file;

    public LogReader(LBWebSocketServer server, String log_file) {
        super(server);
        this.log_file = log_file;
    }

    public void run() {

        BufferedInputStream reader;
        StringBuilder buffer = new StringBuilder();
        
        try {
            reader = new BufferedInputStream(new FileInputStream(log_file));

            while (!stop) {

                if (reader.available() > 0) {
                    char c = (char) reader.read();
                    if (c == 0x0A || c ==0x0D) { // We got a line
                        if (buffer.length() > 0) {
                            server.sendToAll(buffer.toString());
                            System.out.println("[LogReader] " + buffer.toString());
                            buffer.setLength(0); // Clear the buffer
                        }   
                    }
                    else buffer.append(c);
                
                } else {
                        Thread.sleep(250);
                }
            }
            reader.close();
        } catch (Exception ex) {
            Logger.getLogger(LogReader.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
