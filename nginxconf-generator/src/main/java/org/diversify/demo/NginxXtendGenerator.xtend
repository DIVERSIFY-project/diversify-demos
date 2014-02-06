package org.diversify.demo

import java.util.List


class NginxXtendGenerator {
	
	
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
}
			 '''
			 return template
		 
	}
}