package org.diversify.kevoree.generator.new

import org.kevoree.impl.DefaultKevoreeFactory
import java.io.BufferedReader
import java.io.FileReader
import java.io.File
import org.kevoree.kevscript.KevScriptEngine
import java.io.FileOutputStream
import org.kevoree.ContainerRoot
import org.kevoree.serializer.JSONModelSerializer
import java.util.ArrayList
import java.util.HashMap

/**
 * User: Erwan Daubert - erwan.daubert@gmail.com
 * Date: 18/02/14
 * Time: 13:18
 *
 * @author Erwan Daubert
 * @version 1.0
 */

fun main(args: Array<String>) {
    var nodesConfigurationFile = args.find { arg -> arg.startsWith("nodes=") }
    var sosiesUrlFile = args.find { arg -> arg.startsWith("sosies=") }

    var modelOutputFile = args.find { arg -> arg.startsWith("outputModel=") }
    var scriptOutputFile = args.find { arg -> arg.startsWith("outputScript=") }

    if (nodesConfigurationFile != null && sosiesUrlFile != null && (modelOutputFile != null || scriptOutputFile != null)) {
        nodesConfigurationFile = nodesConfigurationFile!!.substring("nodes=".length)
        sosiesUrlFile = sosiesUrlFile!!.substring("sosies=".length)

        val scriptBuilder = StringBuilder()
        buildNodeScript(nodesConfigurationFile!!, scriptBuilder)
        appendSosieConfiguration(nodesConfigurationFile!!, sosiesUrlFile!!, scriptBuilder)
        if (scriptOutputFile != null) {
            scriptOutputFile = scriptOutputFile!!.substring("outputScript=".length)
            val stream = FileOutputStream(File(scriptOutputFile!!))
            stream.write(scriptBuilder.toString().getBytes())
            stream.flush()
            stream.close()
        }
        if (modelOutputFile != null) {
            modelOutputFile = modelOutputFile!!.substring("outputModel=".length)
            val model = buildNodeModel(scriptBuilder.toString())
            val stream = FileOutputStream(File(modelOutputFile!!))
            JSONModelSerializer().serializeToStream(model, stream)
        }
    }
}

fun buildNodeScript(nodesConfigurationFile: String, scriptBuilder: StringBuilder) {

    scriptBuilder.append("repo 'http://oss.sonatype.org/content/groups/public/'\n")
    scriptBuilder.append("repo 'http://sd-35000.dedibox.fr:8080/archiva/repository/internal/'\n")

    scriptBuilder.append("include mvn:org.kevoree.library.java:org.kevoree.library.java.ws:latest\n")
    scriptBuilder.append("include mvn:org.kevoree.library.cloud:org.kevoree.library.cloud.lxc:latest\n")
    scriptBuilder.append("include mvn:org.kevoree.library.cloud:org.kevoree.library.cloud.lightlxc:latest\n")
    scriptBuilder.append("include mvn:org.kevoree.library.cloud:org.kevoree.library.cloud.system:latest\n")
    scriptBuilder.append("include mvn:org.kevoree.library.java:org.kevoree.library.java.hazelcast:latest\n")
    scriptBuilder.append("include mvn:org.kevoree.library.java:org.kevoree.library.java.channels:latest\n")

    scriptBuilder.append("include mvn:org.kevoree.komponents:http-netty:latest\n")

    scriptBuilder.append("include mvn:org.diversify.demo:kevoree-utils-xtend:latest\n")
    scriptBuilder.append("include mvn:org.diversify:org.diversify.kevoree.nginx:1.0.0-SNAPSHOT\n") // latest must be used but doesn't work. I think the index on sd-35000 repository is not efficient
    scriptBuilder.append("include mvn:org.diversify:org.diversify.kevoree.loadBalancer:latest\n")

    scriptBuilder.append("include mvn:org.diversify:org.diversify.kevoree.restarter:latest\n")

    scriptBuilder.append("add ").append("sync : WSGroup\n")
    scriptBuilder.append("add nginxChannel : UselessChannel\n")
    scriptBuilder.append("add lbMonitorChannelGetNbSosieCalled : DistributedBroadcast\n")
    scriptBuilder.append("add lbMonitorChannelReceiveNbSosieCalled : DistributedBroadcast\n")
    scriptBuilder.append("add request : AsyncBroadcast\n")
    scriptBuilder.append("add response : AsyncBroadcast\n")


    val reader = BufferedReader(FileReader(File(nodesConfigurationFile)))
    var line = reader.readLine()
    while (line != null) {
        val configuration = line!!.split(";")
        scriptBuilder.append("add ").append(configuration[0]).append(" : ").append(configuration[1]).append("\n")
        scriptBuilder.append("set sync.port/").append(configuration[0]).append(" = '9000'\n")
        scriptBuilder.append("attach ").append(configuration[0]).append(" sync\n")
        scriptBuilder.append("network ").append(configuration[0]).append(".ip.lan ").append(configuration[2]).append("\n")

        var i = Integer.parseInt(configuration[3])

        while (i > 0) {
            i--
            scriptBuilder.append("add ").append(configuration[0]).append(".").append(configuration[0]).append("Child").append(i).append(" : JavaNode\n")
            // ack to define network information when we use JavaNode as hosting node
            if (configuration[1].equalsIgnoreCase("javanode")) {
                scriptBuilder.append("network ").append(configuration[0]).append("Child").append(i).append(".ip.lan ").append(configuration[2]).append("\n")
            }
        }

        if (configuration.size == 5) {
            scriptBuilder.append("add ").append(configuration[0]).append("Child0").append(".nginx : NginxConfigurator\n")
            scriptBuilder.append("bind ").append(configuration[0]).append("Child0").append(".nginx.useless nginxChannel\n")

            scriptBuilder.append("set ").append(configuration[0]).append("Child0").append(".nginx.servers = '###############################################################################\n" +
            "# Definition of the load balancer front-end\n" +
            "###############################################################################\n" +
            "server {\n" +
            "   listen 80;\n" +
            "   server_name localhost;\n" +
            "   access_log /tmp/loadbalancerclient/proxy.log proxy; #proxy refers to the log format defined in nginx.conf\n" +
            "   location / {\n" +
            "       proxy_pass http://backend;\n" +
            "   }\n" +
            "   location /client {\n" +
            "       root /tmp/loadbalancerclient;\n" +
            "       autoindex on;\n" +
            "   }\n" +
            "   location /client/ws {\n" +
            "       proxy_pass http://localhost:8099;\n" +
            "       # These are the option for websockets (need nginx >= v1.3.13)\n" +
            "       proxy_set_header X-Real-IP \$remote_addr;\n" +
            "       proxy_set_header Host \$host;\n" +
            "       proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;\n" +
            "       proxy_http_version 1.1;\n" +
            "       proxy_set_header Upgrade \$http_upgrade;\n" +
            "       proxy_set_header Connection \"upgrade\";\n" +
            "   }\n" +
            "}'\n")

            scriptBuilder.append("add ").append(configuration[0]).append("Child0").append(".softwareInstaller : MultipleLinesScriptRunner\n")
            scriptBuilder.append("set ").append(configuration[0]).append("Child0").append(".softwareInstaller.startScript = 'apt-get update\n" +
            "apt-get install nginx redis-server --no-install-recommends -y\n" +
            "cat /etc/nginx/nginx.conf | sed \"s/error_log \\\\/var\\\\/log\\\\/nginx\\\\/error.log;/error_log \\\\/var\\\\/log\\\\/nginx\\\\/error.log;\\\\nlog_format proxy \\'[\\\\\$time_local]; \\\\\$remote_addr; \\\\\$upstream_addr; \\\\\$upstream_response_time; \\\\\$request; \\\\\$remote_user;\\'/g\" > /tmp/nginx.conf\n" +
            "cp /tmp/nginx.conf /etc/nginx/nginx.conf\n" +
            "rm -rf /tmp/nginx.conf\n" +
            "rm -rf /etc/nginx/sites-enabled/default\n" +
            "cat > \\'/etc/redis/redis.conf\\' << EOF\n" +
            "daemonize yes\n" +
            "pidfile /var/run/redis/redis-server.pid\n" +
            "port 6379\n" +
            "# If you want you can bind a single interface, if the bind option is not\n" +
            "# specified all the interfaces will listen for incoming connections.\n" +
            "#\n" +
            "#bind <ip>\n" +
            "timeout 0\n" +
            "tcp-keepalive 60\n" +
            "loglevel notice\n" +
            "logfile /var/log/redis/redis-server.log\n" +
            "databases 16\n" +
            "save 900 1\n" +
            "save 300 10\n" +
            "save 60 10000\n" +
            "stop-writes-on-bgsave-error yes\n" +
            "rdbcompression yes\n" +
            "rdbchecksum yes\n" +
            "dbfilename dump.rdb\n" +
            "dir /var/lib/redis\n" +
            "slave-serve-stale-data yes\n" +
            "slave-read-only yes\n" +
            "repl-disable-tcp-nodelay no\n" +
            "slave-priority 100\n" +
            "maxclients 10000\n" +
            "appendonly no\n" +
            "appendfsync everysec\n" +
            "no-appendfsync-on-rewrite no\n" +
            "auto-aof-rewrite-percentage 100\n" +
            "auto-aof-rewrite-min-size 64mb\n" +
            "lua-time-limit 5000\n" +
            "slowlog-log-slower-than 10000\n" +
            "slowlog-max-len 128\n" +
            "hash-max-ziplist-entries 512\n" +
            "hash-max-ziplist-value 64\n" +
            "list-max-ziplist-entries 512\n" +
            "list-max-ziplist-value 64\n" +
            "set-max-intset-entries 512\n" +
            "zset-max-ziplist-entries 128\n" +
            "zset-max-ziplist-value 64\n" +
            "activerehashing yes\n" +
            "client-output-buffer-limit normal 0 0 0\n" +
            "client-output-buffer-limit slave 256mb 64mb 60\n" +
            "client-output-buffer-limit pubsub 32mb 8mb 60\n" +
            "hz 50\n" +
            "aof-rewrite-incremental-fsync yes\n" +
            "EOF\n" +
            "/etc/init.d/redis-server restart\n" +
            "'\n")

            scriptBuilder.append("add ").append(configuration[0]).append("Child0").append(".lbMonitor : KevoreeLBMonitor\n")
            // here we can specify the port and logFile for lbMonitor
            scriptBuilder.append("bind ").append(configuration[0]).append("Child0").append(".lbMonitor.getNbSosieCalled lbMonitorChannelGetNbSosieCalled\n")
            scriptBuilder.append("bind ").append(configuration[0]).append("Child0").append(".lbMonitor.receiveNbSosieCalled lbMonitorChannelReceiveNbSosieCalled\n")



            scriptBuilder.append("add ").append(configuration[0]).append("Child0").append(".webserver : NettyHTTPServer\n")
            scriptBuilder.append("set ").append(configuration[0]).append("Child0").append(".webserver.port = '7999'\n")

            scriptBuilder.append("add ").append(configuration[0]).append("Child0").append(".restarter : DemoRestarter\n")
            scriptBuilder.append("set ").append(configuration[0]).append("Child0").append(".restarter.componentType = 'SosieRunner'\n")


            scriptBuilder.append("add ").append(configuration[0]).append("Child0").append(".favicon : FaviconHandler\n")
            scriptBuilder.append("set ").append(configuration[0]).append("Child0").append(".favicon.urlPattern = '/favicon.*'\n")
            scriptBuilder.append("set ").append(configuration[0]).append("Child0").append(".favicon.favicon = 'favicon.png'\n")


            scriptBuilder.append("bind ").append(configuration[0]).append("Child0").append(".webserver.request request\n")
            scriptBuilder.append("bind ").append(configuration[0]).append("Child0").append(".webserver.response response\n")

            scriptBuilder.append("bind ").append(configuration[0]).append("Child0").append(".favicon.request request\n")
            scriptBuilder.append("bind ").append(configuration[0]).append("Child0").append(".favicon.content response\n")

            scriptBuilder.append("bind ").append(configuration[0]).append("Child0").append(".restarter.request request\n")
            scriptBuilder.append("bind ").append(configuration[0]).append("Child0").append(".restarter.content response\n")
        }

        line = reader.readLine()
    }
}

fun buildNodeModel(script: String): ContainerRoot {
    val factory = DefaultKevoreeFactory()
    val model = factory.createContainerRoot()
    val kevScriptEngine = KevScriptEngine()

    kevScriptEngine.execute(script, model)
    return model
}

fun appendSosieConfiguration(nodesConfigurationFile: String, sosiesUrlFile: String, scriptBuilder: StringBuilder) {

    val nodesList = ArrayList<String>()
    val javaParentNodes = ArrayList<String>()
    val portMap = HashMap<String, Int>()

    var reader = BufferedReader(FileReader(File(nodesConfigurationFile)))
    var line = reader.readLine()
    while (line != null) {
        val configuration = line!!.split(";")

        var i = Integer.parseInt(configuration[3])
        while (i > 0) {
            i--
            nodesList.add(configuration[0] + "Child" + i)
            // Ack to ensure port are not the same between component on different childNode but with the same JavaNode parent
            if (configuration[1].equalsIgnoreCase("javanode")) {
                javaParentNodes.add(configuration[0] + "Child" + i)
            }
        }
        if (configuration.size == 5) {
            nodesList.remove(configuration[0] + "Child0")
            // Ack to ensure port are not the same between component on different childNode but with the same JavaNode parent
            if (configuration[1].equalsIgnoreCase("javanode")) {
                javaParentNodes.remove(configuration[0] + "Child0")
            }
        }

        line = reader.readLine()
    }

    scriptBuilder.append("include mvn:org.diversify:org.diversify.kevoree.sosie:latest\n");

    reader = BufferedReader(FileReader(File(sosiesUrlFile)))
    line = reader.readLine()
    var i = 0
    while (line != null) {
        if (line != "") {
            val nodeName = nodesList.get(i)
            var sosieName: String? = null
            if (line!!.contains("composed-sosie-")) {
                sosieName = line!!.substring(line!!.indexOf("composed-sosie-") + "composed-sosie-".length, line!!.length - ".zip".length)
                sosieName = sosieName!!.substring(sosieName!!.indexOf("-") + 1)
            } else if (line!!.contains("ringo-")) {
                sosieName = line!!.substring(line!!.indexOf("ringo-") + "ringo-".length, line!!.length - ".zip".length)
                sosieName = sosieName!!.substring(sosieName!!.indexOf("-") + 1)
            }
            scriptBuilder.append("add ").append(nodeName).append(".").append(sosieName).append(nodeName).append(i).append(" : SosieRunner\n")
            scriptBuilder.append("set ").append(nodeName).append(".").append(sosieName).append(nodeName).append(i).append(".sosieUrl = '").append(line).append("'\n")

            var port = 0
            if (javaParentNodes.contains(nodeName)) {
                val parentNodeName = nodeName.substring(0, nodeName.indexOf("Child"))
                if (portMap.get(parentNodeName) == null) {
                    portMap.put(parentNodeName, 8080)
                } else {
                    portMap.put(parentNodeName, portMap.remove(parentNodeName)!! + 1)
                }
                port = portMap.get(parentNodeName)!!
            } else {
                if (portMap.get(nodeName) == null) {
                    portMap.put(nodeName, 8080)
                } else {
                    portMap.put(nodeName, portMap.remove(nodeName)!! + 1)
                }
                port = portMap.get(nodeName)!!
            }

            scriptBuilder.append("set ").append(nodeName).append(".").append(sosieName).append(nodeName).append(i).append(".port = '").append(port).append("'\n")

            scriptBuilder.append("bind ").append(nodeName).append(".").append(sosieName).append(nodeName).append(i).append(".useless nginxChannel\n")
            scriptBuilder.append("bind ").append(nodeName).append(".").append(sosieName).append(nodeName).append(i).append(".getNbSosieCalled lbMonitorChannelGetNbSosieCalled\n")
            scriptBuilder.append("bind ").append(nodeName).append(".").append(sosieName).append(nodeName).append(i).append(".sendNbSosieCalled lbMonitorChannelReceiveNbSosieCalled\n")

            if (i < nodesList.size - 1) {
                i++
            } else {
                i = 0
            }
        }
        line = reader.readLine()
    }
}