package org.diversify.demo

import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.PrintWriter
import java.util.List

class NginxXtendGenerator {
	
	def void deployConfig(Node master, List<Node> slaves){
		
		val File f = new File("/etc/nginx/sites-enabled/site-enabled")
		if (f.exists){
			f.delete
		}
		f.createNewFile
		val FileOutputStream fo = new FileOutputStream(f)
		val printer = new PrintWriter(fo)
		printer.println(generateConfig(master,slaves))
		printer.flush
		fo.close
	}
	
	
	def String generateConfig(Node master, List<Node> slaves ){
		 val template = ''' 
###############################################################################
# Definition of the Load balancer backend
# Even distribution of the requests on the different servers
# No ip hash at this point
# We can also define weigths for each server in order to favor one or the other
###############################################################################

	upstream backend {
		«FOR node : slaves» 
		server «node.ip»:«node.port»;
		«ENDFOR»
	}
###############################################################################
# Definition of the load balancer front-end
###############################################################################


server {
	listen	80;
	server_name «master.name»;
	access_log /var/www/proxy.log proxy;
	location / {
		proxy_pass http://backend;
	}
}'''
			 return template
	}
	
	def boolean restartNginx() {
		   val command5 = #["/etc/init.d/nginx","restart"]
           val process5 = Runtime.getRuntime().exec(command5)
           var Thread readerOUTthread = new Thread(new Reader(process5.getInputStream(),  false))
             var Thread       readerERRthread = new Thread(new Reader(process5.getErrorStream(), true))
            readerOUTthread.start()
            readerERRthread.start()
            process5.waitFor()
		return true
	}
}

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
