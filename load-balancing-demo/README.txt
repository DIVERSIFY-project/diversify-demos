This demo uses load balancing on HTTP requests in order to ditribute them on alternative 
service implemetation. A GUI allows monitoring where requests are processed.


-----------------------
Setting up the server
-----------------------

# compile:
mvn clean install

# create a standalone JAR:
mvn assembly:single


# start the server (on port 8099)

java -jar target/load-balancing-demo-0.0.1-SNAPSHOT-jar-with-dependencies.jar /path/to/nginx/log/file

# the nginx log file should have a specific format (see /load-balancing-demo/nginx/README.txt)
# if no log file is given the server will run in simulation mode and continously serve the test 
# log file (/load-balancing-demo/src/main/resources/proxy.log)
