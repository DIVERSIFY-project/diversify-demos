Tested with nginx on ubuntu 12.04 LTS

For websocket proxy the default version of nginx which ships with ubuntu 12.04 is not recent enough.

*******************************************************************************
To upgrade to the latest stable release of nginx:
*******************************************************************************

sudo apt-get remove nginx
sudo apt-get autoremove

sudo add-apt-repository ppa:nginx/stable
sudo apt-get update
sudo apt-get install nginx

sudo service nginx restart

*******************************************************************************
Here is the log format we are using (should be put in nginx.conf)
*******************************************************************************

log_format proxy '[$time_local]; $remote_addr; $upstream_addr; $upstream_response_time; $request; $remote_user;'

*******************************************************************************
Example Configuration with:
 * the load balancer on /
 * Some static pages on /client
 * Web sockets on /client/ws
*******************************************************************************

upstream backend { 

	# That is the list of servers to load balance
	# This is the part which should be updated for each deployment

	server 54.80.92.239:8080;
	server 54.80.92.239:8081;
	server 54.80.92.239:8082;
	server 54.80.92.239:8083;
	server 54.80.92.239:8084;

	server 54.227.136.103:8080;
	server 54.227.136.103:8080;

}


server {
	listen 80;
	server_name localhost;
	access_log /var/www/proxy.log proxy; #proxy refers to the log format defined in nginx.conf

	location / {
		proxy_pass http://backend;
	}

	location /client {
		root   /var/www;
		autoindex on;

	}

	location /client/ws {
			proxy_pass http://localhost:8099;
		
			# These are the option for websockets (need nginx >= v1.3.13)
			proxy_set_header X-Real-IP $remote_addr;
			proxy_set_header Host $host;
			proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
		
			proxy_http_version 1.1;
			proxy_set_header Upgrade $http_upgrade;
			proxy_set_header Connection "upgrade";
	}

}


