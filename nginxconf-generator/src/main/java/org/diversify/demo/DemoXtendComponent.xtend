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
class DemoXtendComponent {

	@Start
	public def startComponent() {
		println("Start");
	}

	@Stop
	public def stopComponent() {
		println("Stop");
	}

	@Input
	public def consumeHello(Object o) {
		println("Received " + o.toString());
		if (o instanceof String) {
			var msg = o as String;
			println("HelloConsumer received: " + msg);
		}
	}

	@Output
	private Port simplePort;

	@Param(defaultValue="2000")
	@Property
	private int myparameter = 2000;

	//Init the variables (from inside a component)
	var cloner = new DefaultModelCloner();

	@KevoreeInject
	private KevScriptService kevScriptService;

	@KevoreeInject
	private ModelService modelService;

	def adaptComponent() {
		//Get the current Model
		var model = modelService.getCurrentModel();
		// Clone the model to make it changeable
		var ContainerRoot localModel = cloner.clone(model.getModel()) as ContainerRoot
		
		
		// Apply the script on the current model, to get a new configuration
		kevScriptService.execute("//kevscripttoapply", localModel)

		//Ask the platform to apply the new model; register an optional callback to know when the adaptation is finised.
		modelService.update(localModel, [e | println("ok")])
		
	}

}
