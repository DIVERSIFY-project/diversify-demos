package org.diversify.demo

import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader

class Reader implements  Runnable{
	var BufferedReader br
	var error  = true
	new(InputStream stream , boolean error) {
         br  = new BufferedReader(new InputStreamReader(stream));
		this.error=error
	}
	

      override  def run() {
            var String line
            try {
                line = br.readLine()
                while (line != null) {
                    line =  "/" + line
                    if (error) {
                        System.err.println(line);
                    } else {
                        System.out.println(line);
                    }
                    line = br.readLine()
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }