package org.diversify.demo

import org.kevoree.annotation.ComponentType
import org.kevoree.annotation.Param
import org.kevoree.annotation.Start
import org.kevoree.annotation.Stop

@ComponentType
class AptGetInstallerRemoverComponent {

	@Param(optional=false)
	String packageName

	@Param(optional=true)
	String preInstallScript

	@Param(optional=true)
	String postInstallScript
	
	@Param(optional=true)
	String preRemoveScript

	@Param(optional=true)
	String postRemoveScript
	
	CommandExecutor exec = new CommandExecutor

	@Start
	public def startComponent() {
		
		if (preInstallScript!=null)	
			exec.execute(preInstallScript.split(";"))
		val command = #["/usr/bin/apt-get","install","-f", "-y",packageName]
		exec.execute(command);
		if (postInstallScript!=null)	
			exec.execute(postInstallScript.split(";"))
	}

	@Stop
	public def stopComponent() {
			if (preRemoveScript!=null)	
			exec.execute(preRemoveScript.split(";"))
		val command = #["/usr/bin/apt-get","remove","-f", "-y",packageName]
		exec.execute(command);
		if (postRemoveScript!=null)	
			exec.execute(postRemoveScript.split(";"))
	}


}
