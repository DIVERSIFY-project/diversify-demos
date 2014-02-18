package org.diversify.demo

import org.kevoree.annotation.ComponentType
import org.kevoree.annotation.Param
import org.kevoree.annotation.Start
import org.kevoree.annotation.Stop

@ComponentType
class AptgetInstallerComponent {

	@Param(optional=false)
	String packageName

	@Param(optional=true)
	String preScript

	@Param(optional=true)
	String postScript
	
	CommandExecutor exec = new CommandExecutor

	@Start
	public def startComponent() {
		
		if (preScript!=null)	
			exec.execute(preScript.split(";"))
		val command = #["/usr/bin/apt-get","install","-f", "-y",packageName]
		exec.execute(command);
		if (postScript!=null)	
			exec.execute(postScript.split(";"))
	}

	@Stop
	public def stopComponent() {
		
	}


}
