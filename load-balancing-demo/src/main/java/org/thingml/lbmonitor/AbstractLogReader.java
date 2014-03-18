/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.thingml.lbmonitor;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author ffl
 */
public abstract class AbstractLogReader implements Runnable {
    protected boolean stop = false;
    LBWebSocketServer server;
    Thread thread;

   public AbstractLogReader(LBWebSocketServer server) {
        this.server = server;
    }

    public void startReader() {
        stop = false;
        thread = new Thread(this);
        thread.start();
    }

    public void stopReader() {
        stop = true;
        try {
            thread.join();
        } catch (InterruptedException ex) {
            Logger.getLogger(LogReaderSimulator.class.getName()).log(Level.SEVERE, null, ex);
        }
        thread = null;
    }
    
}
