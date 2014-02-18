package org.diversify.demo

import org.kevoree.ContainerRoot
import org.kevoree.annotation.ComponentType
import org.kevoree.annotation.Input
import org.kevoree.annotation.KevoreeInject
import org.kevoree.annotation.Output
import org.kevoree.annotation.Param
import org.kevoree.annotation.Start
import org.kevoree.annotation.Stop
import org.kevoree.api.KevScriptService
import org.kevoree.api.ModelService
import org.kevoree.api.Port
import org.kevoree.cloner.DefaultModelCloner

@ComponentType
class AptgetRemoverComponent {

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
		val command = #["/usr/bin/apt-get","remove","-f", "-y",packageName]
		exec.execute(command);
		if (postScript!=null)	
			exec.execute(postScript.split(";"))
	}

	@Stop
	public def stopComponent() {
		
	}


}
