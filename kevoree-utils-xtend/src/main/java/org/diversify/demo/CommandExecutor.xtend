package org.diversify.demo

import java.util.List

class CommandExecutor {

	def boolean execute(List<String> command5) {
		   //val command5 = command//#["/etc/init.d/nginx","restart"]
           val process5 = Runtime.getRuntime().exec(command5)
           var Thread readerOUTthread = new Thread(new Reader(process5.getInputStream(),  false))
             var Thread       readerERRthread = new Thread(new Reader(process5.getErrorStream(), true))
            readerOUTthread.start()
            readerERRthread.start()
            process5.waitFor()
		return true
	}
}


