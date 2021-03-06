package org.diversify.demo

import java.util.ArrayList
import java.util.List
import org.kevoree.ComponentInstance
import org.kevoree.ContainerNode
import org.kevoree.ContainerRoot
import org.kevoree.DictionaryValue
import org.kevoree.MBinding
import org.kevoree.NetworkInfo
import org.kevoree.NetworkProperty
import org.kevoree.annotation.ComponentType
import org.kevoree.annotation.Input
import org.kevoree.annotation.KevoreeInject
import org.kevoree.annotation.Output
import org.kevoree.annotation.Param
import org.kevoree.annotation.Start
import org.kevoree.annotation.Stop
import org.kevoree.api.Context
import org.kevoree.api.ModelService
import org.kevoree.api.Port
import org.kevoree.api.handler.ModelListenerAdapter

@ComponentType
class NginxLoadBalancerComponent extends ModelListenerAdapter{

	@Param(defaultValue="80")
	Integer port;

	@Start
	public def startComponent() {
		var master = new Node
		master.port=""+this.port
		master.name = context.instanceName
		master.ip = getNodeIp(modelService.pendingModel.findNodesByID(context.nodeName))
		generator.deployConfig(master,this.generateNginxConfig(modelService.pendingModel),serverNameDNS)
		generator.restartNginx
		modelService.registerModelListener(this)
		
		
	}
	

	@Stop
	public def stopComponent() {
		modelService.unregisterModelListener(this)
	}

	@Input(optional=true)
	public def receiveMessage(Object o) {
	}

	@Output(optional=false)
	private Port outputPort;

	@Param(optional=true, defaultValue="localhost")
	private String serverNameDNS

	@KevoreeInject
	private ModelService modelService;

	@KevoreeInject
	protected Context context;

	private List<Node> nodes = new ArrayList<Node>()
	
	NginxXtendGenerator generator = new NginxXtendGenerator()

	def List<Node> generateNginxConfig(ContainerRoot model) {
		var nods = new ArrayList<Node>()

		//var fact = new DefaultKevoreeFactory()
		//var networkinfo = fact.createNetworkInfo()
		//var networkproperty = fact.createNetworkProperty()
		var nodeName = context.nodeName

		//Get the current Model
		
		//var node = modelService.pendingModel.findNodesByID(nodeName)
		for (MBinding b : model.findNodesByID(context.nodeName).findComponentsByID(context.instanceName).required.
			get(0).bindings) {
			for (MBinding b1 : b.hub.bindings) {
			if (b1 != b) {

				for (DictionaryValue value : (b1.port.eContainer as ComponentInstance).dictionary.values) {
					if ("port".equals(value.name)) {
						var res = new Node
						res.name = (b1.port.eContainer as ComponentInstance).name
						res.port = value.value
						res.ip = getNodeIp(b1.port.eContainer.eContainer as ContainerNode)
						if (res.ip != null) {
							nods.add(res)
						}
						
					}
				}
}
			}

		}
		return nods

	//getListNodes(model.model.nodes)
	}

	/*def getListNodes(List<ContainerNode> _nodes) {
		for (ContainerNode n : _nodes) {

			var n1 = getNodeNameAndPort(n)
			if (n1 != null) {
				n1.setIp(getNodeIp(n))
			}
			if (n1.ip != null) {
				nodes.add(n1)
			}
			getListNodes(n.hosts)

		}
	}*/

	def String getNodeIp(ContainerNode n) {
		for (NetworkInfo ni : n.getNetworkInformation()) {
			if ("ip".equals(ni.getName())) {
				for (NetworkProperty np : ni.getValues()) {
					if ("lan".equals(np.getName())) {
						return np.getValue()
					}
				}
			}

		}
		return null;
	}
	
	override modelUpdated() {
		var master = new Node
		master.port=""+this.port
		master.name = context.instanceName
		master.ip = getNodeIp(modelService.currentModel.model.findNodesByID(context.nodeName))
		generator.deployConfig(master,this.generateNginxConfig(modelService.currentModel.model),serverNameDNS)
		generator.restartNginx 
	}

	/*def Node getNodeNameAndPort(ContainerNode n) {

		for (ComponentInstance comp : n.components) {
			for (DictionaryValue value : comp.dictionary.values) {
				if ("port".equals(value.name)) {
					var res = new Node
					res.name = comp.name
					res.port = value.value
					return res
				}
			}

		}

		return null

	}*/
}
