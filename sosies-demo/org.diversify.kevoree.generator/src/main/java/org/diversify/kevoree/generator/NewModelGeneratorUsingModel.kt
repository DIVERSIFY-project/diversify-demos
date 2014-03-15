package org.diversify.kevoree.generator

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
import org.kevoree.loader.JSONModelLoader
import java.io.FileInputStream

/**
 * User: Erwan Daubert - erwan.daubert@gmail.com
 * Date: 18/02/14
 * Time: 13:18
 *
 * @author Erwan Daubert
 * @version 1.0
 */
class ModelGeneratorFromModel {

    val kevoreeLibraryVersion = "3.5.1"
    fun main(args: Array<String>) {
        var baseModelFile = args.find { arg -> arg.startsWith("model=") }
        var nodesConfigurationFile = args.find { arg -> arg.startsWith("nodes=") }
        var sosiesUrlFile = args.find { arg -> arg.startsWith("sosies=") }

        var modelOutputFile = args.find { arg -> arg.startsWith("outputModel=") }
        var scriptOutputFile = args.find { arg -> arg.startsWith("outputScript=") }

        var modelUpdateOption = args.find { arg -> arg.startsWith("modelUpdater=") }

        var modelUpdate = false
        if (modelUpdateOption != null && modelUpdateOption != "") {
            modelUpdate = java.lang.Boolean.parseBoolean(modelUpdateOption!!.substring("modelUpdater=".length))
        }

        var baseModel = DefaultKevoreeFactory().createContainerRoot()
        if (baseModelFile != null && baseModelFile != "") {
            baseModel = JSONModelLoader().loadModelFromStream(FileInputStream(File(baseModelFile!!.substring("model=".length))))?.get(0) as ContainerRoot
        }

        if (nodesConfigurationFile != null && sosiesUrlFile != null && (modelOutputFile != null || scriptOutputFile != null)) {
            nodesConfigurationFile = nodesConfigurationFile!!.substring("nodes=".length)
            sosiesUrlFile = sosiesUrlFile!!.substring("sosies=".length)

            var scriptBuilder = StringBuilder()
            buildNodeScriptIaaS(baseModel, nodesConfigurationFile!!, scriptBuilder)
            if (scriptOutputFile != null) {
                var tmpScriptOutputFile = scriptOutputFile!!.substring("outputScript=".length).replace(".kevs", "-iaas.kevs")
                val stream = FileOutputStream(File(tmpScriptOutputFile))
                stream.write(scriptBuilder.toString().getBytes())
                stream.flush()
                stream.close()
            }
            if (modelOutputFile != null) {
                var tmpModelOutputFile = modelOutputFile!!.substring("outputModel=".length).replaceAll(".kev$", "-iaas.kev")
                val model = buildNodeModel(baseModel, scriptBuilder.toString())
                val stream = FileOutputStream(File(tmpModelOutputFile))
                JSONModelSerializer().serializeToStream(model, stream)
            }

            scriptBuilder = StringBuilder()
            buildNodeScript(nodesConfigurationFile!!, scriptBuilder)
            appendMasterConfiguration(nodesConfigurationFile!!, scriptBuilder, modelUpdate)
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
                val model = buildNodeModel(DefaultKevoreeFactory().createContainerRoot(), scriptBuilder.toString())
                val stream = FileOutputStream(File(modelOutputFile!!))
                JSONModelSerializer().serializeToStream(model, stream)
            }
        }
    }

    fun buildNodeModel(baseModel: ContainerRoot, script: String): ContainerRoot {
        val kevScriptEngine = KevScriptEngine()

        kevScriptEngine.execute(script, baseModel)
        return baseModel
    }

    fun buildNodeScriptIaaS(baseModel: ContainerRoot, nodesConfigurationFile: String, scriptBuilder: StringBuilder) {

        scriptBuilder.append("repo 'http://oss.sonatype.org/content/groups/public/'\n")
        scriptBuilder.append("repo 'http://sd-35000.dedibox.fr:8080/archiva/repository/internal/'\n")

        scriptBuilder.append("include mvn:org.kevoree.library.java:org.kevoree.library.java.ws:$kevoreeLibraryVersion\n")
        scriptBuilder.append("include mvn:org.kevoree.library.cloud:org.kevoree.library.cloud.lxc:$kevoreeLibraryVersion\n")
        scriptBuilder.append("include mvn:org.kevoree.library.cloud:org.kevoree.library.cloud.lightlxc:$kevoreeLibraryVersion\n")

        if (baseModel.findGroupsByID("sync") == null) {
            scriptBuilder.append("add ").append("sync : WSGroup\n")
        }

        var port = 9000

        val reader = BufferedReader(FileReader(File(nodesConfigurationFile)))
        var line = reader.readLine()
        while (line != null) {
            val configuration = line!!.split(";")

            if (baseModel.findNodesByID(configuration[0]) == null) {
                scriptBuilder.append("add ").append(configuration[0]).append(" : ").append(configuration[1]).append("\n")
                scriptBuilder.append("set sync.port/").append(configuration[0]).append(" = '" + port + "'\n")
                port++
                scriptBuilder.append("attach ").append(configuration[0]).append(" sync\n")
                scriptBuilder.append("network ").append(configuration[0]).append(".ip.lan ").append(configuration[2]).append("\n")
            }
            var i = Integer.parseInt(configuration[3])

            while (i > 0) {
                i--
                scriptBuilder.append("add ").append(configuration[0]).append(".").append("diversify").append(configuration[0]).append("Child").append(i).append(" : JavaNode\n")
                // ack to define network information when we use JavaNode as hosting node
                if (configuration[1].equalsIgnoreCase("javanode")) {
                    scriptBuilder.append("network ").append("diversify").append(configuration[0]).append("Child").append(i).append(".ip.lan ").append(configuration[2]).append("\n")
                }
            }
            line = reader.readLine()
        }
    }

    fun buildNodeScript(nodesConfigurationFile: String, scriptBuilder: StringBuilder) {

        scriptBuilder.append("repo 'http://oss.sonatype.org/content/groups/public/'\n")
        scriptBuilder.append("repo 'http://sd-35000.dedibox.fr:8080/archiva/repository/internal/'\n")
        scriptBuilder.append("repo 'http://maven.reacloud.com/repository/reacloud/snapshots/'\n")

        scriptBuilder.append("include mvn:org.kevoree.library.java:org.kevoree.library.java.ws:$kevoreeLibraryVersion\n")
        scriptBuilder.append("include mvn:org.kevoree.library.java:org.kevoree.library.java.hazelcast:$kevoreeLibraryVersion\n")
        scriptBuilder.append("include mvn:org.kevoree.library.cloud:org.kevoree.library.cloud.lxc:$kevoreeLibraryVersion\n")
        scriptBuilder.append("include mvn:org.kevoree.library.cloud:org.kevoree.library.cloud.lightlxc:$kevoreeLibraryVersion\n")
        scriptBuilder.append("include mvn:org.kevoree.library.cloud:org.kevoree.library.cloud.system:$kevoreeLibraryVersion\n")
        scriptBuilder.append("include mvn:org.kevoree.library.java:org.kevoree.library.java.hazelcast:$kevoreeLibraryVersion\n")
        scriptBuilder.append("include mvn:org.kevoree.library.java:org.kevoree.library.java.channels:$kevoreeLibraryVersion\n")


        scriptBuilder.append("include mvn:org.diversify.demo:kevoree-utils-xtend:latest\n")
        scriptBuilder.append("include mvn:org.diversify:org.diversify.kevoree.nginx:latest\n")
        scriptBuilder.append("include mvn:org.diversify:org.diversify.kevoree.loadBalancer:latest\n")
        scriptBuilder.append("include mvn:org.diversify:org.diversify.kevoree.sosie:latest\n");

        scriptBuilder.append("include mvn:org.diversify:org.diversify.kevoree.manager:latest\n")

        scriptBuilder.append("add ").append("broadcast : BroadcastGroup\n")

        scriptBuilder.append("add ").append("sync : WSGroup\n")
        scriptBuilder.append("add nginxChannel : UselessChannel\n")
        scriptBuilder.append("add lbMonitorChannelReceiveSosieInformation : DistributedBroadcast\n")
        scriptBuilder.append("add request : AsyncBroadcast\n")
        scriptBuilder.append("add response : AsyncBroadcast\n")


        var port = 9000

        val reader = BufferedReader(FileReader(File(nodesConfigurationFile)))
        var line = reader.readLine()
        while (line != null) {
            val configuration = line!!.split(";")
            var i = Integer.parseInt(configuration[3])

            while (i > 0) {
                i--
                scriptBuilder.append("add ").append("diversify").append(configuration[0]).append("Child").append(i).append(" : JavaNode\n")
                scriptBuilder.append("set ").append("diversify").append(configuration[0]).append("Child").append(i).append(".log = 'trace'").append("\n")
                // ack to define network information when we use JavaNode as hosting node
                if (configuration[1].equalsIgnoreCase("javanode")) {
                    scriptBuilder.append("network ").append("diversify").append(configuration[0]).append("Child").append(i).append(".ip.lan ").append(configuration[2]).append("\n")
                }
                scriptBuilder.append("attach ").append("diversify").append(configuration[0]).append("Child").append(i).append(" broadcast\n")
//                scriptBuilder.append("set ").append("broadcast.port = '1010'\n")
                scriptBuilder.append("attach ").append("diversify").append(configuration[0]).append("Child").append(i).append(" sync\n")
            }
            line = reader.readLine()
        }
    }

    fun appendMasterConfiguration(nodesConfigurationFile: String, scriptBuilder: StringBuilder, modelUpdate: Boolean) {
        val reader = BufferedReader(FileReader(File(nodesConfigurationFile)))
        var line = reader.readLine()
        while (line != null) {
            val configuration = line!!.split(";")
            if (configuration.size == 5) {
                scriptBuilder.append("add ").append("diversify").append(configuration[0]).append("Child0").append(".nginx : NginxConfigurator\n")
                scriptBuilder.append("bind ").append("diversify").append(configuration[0]).append("Child0").append(".nginx.useless nginxChannel\n")

                scriptBuilder.append("set ").append("diversify").append(configuration[0]).append("Child0").append(".nginx.servers = '###############################################################################\n" +
                "# Definition of the load balancer front-end\n" +
                "###############################################################################\n" +
                "server {\n" +
                "   listen 80;\n" +
                "   server_name localhost;\n" +
                "   access_log /tmp/loadbalancerclient/proxy.log proxy; #proxy refers to the log format defined in nginx.conf\n" +
                "   location / {\n" +
                "       proxy_pass http://backend;\n" +
                "       # These are the option for websockets (need nginx >= v1.3.13)\n" +
                "       proxy_set_header X-Real-IP \$remote_addr;\n" +
                "       proxy_set_header Host \$host;\n" +
                "       proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;\n" +
                "       proxy_http_version 1.1;\n" +
                "       proxy_set_header Upgrade \$http_upgrade;\n" +
                "       proxy_set_header Connection \"upgrade\";\n" +
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
                scriptBuilder.append("set ").append("diversify").append(configuration[0]).append("Child0").append(".nginx.started = 'false'\n")

                scriptBuilder.append("add ").append("diversify").append(configuration[0]).append("Child0").append(".softwareInstaller : ScriptRunner\n")
                scriptBuilder.append("set ").append("diversify").append(configuration[0]).append("Child0").append(".softwareInstaller.startScript = 'apt-get update\n" +
                "apt-get install nginx redis-server git --no-install-recommends -y\n" +

                "cat > /etc/nginx/nginx.conf << EOF\n" +
                "user www-data;\n" +
                "worker_processes 4;\n" +
                "pid /run/nginx.pid;\n" +
                "events {\n" +
                "worker_connections 768;\n" +
                "# multi_accept on;\n" +
                "}\n" +
                "http {\n" +
                "set_real_ip_from 10.0.0.0/8;\n" +
                "real_ip_header X-Forwarded-For;\n" +
                "##\n" +
                "# Basic Settings\n" +
                "##\n" +
                "sendfile on;\n" +
                "tcp_nopush on;\n" +
                "tcp_nodelay on;\n" +
                "keepalive_timeout 65;\n" +
                "types_hash_max_size 2048;\n" +
                "# server_tokens off;\n" +
                "# server_names_hash_bucket_size 64;\n" +
                "# server_name_in_redirect off;\n" +
                "include /etc/nginx/mime.types;\n" +
                "default_type application/octet-stream;\n" +
                "##\n" +
                "# Logging Settings\n" +
                "##\n" +
                "access_log /var/log/nginx/access.log;\n" +
                "error_log /var/log/nginx/error.log;\n" +
                "log_format proxy \\'[\\\\\$time_local]; \\\\\$remote_addr; \\\\\$upstream_addr; \\\\\$upstream_response_time; \\\\\$request; \\\\\$remote_user;\\'\n" +
                "##\n" +
                "# Gzip Settings\n" +
                "##\n" +
                "gzip on;\n" +
                "gzip_disable \"msie6\";\n" +
                "include /etc/nginx/conf.d/*.conf;\n" +
                "include /etc/nginx/sites-enabled/*;\n" +
                "}\n" +
                "EOF\n" +

                "rm -rf /etc/nginx/sites-enabled/default\n" +

                "cat > \\'/etc/redis/redis.conf\\' << EOF\n" +
                "daemonize yes\n" +
                "pidfile /var/run/redis/redis-server.pid\n" +
                "port 6379\n" +
                "# If you want you can bind a single interface, if the bind option is not\n" +
                "# specified all the interfaces will listen for incoming connections.\n" +
                "#\n" +
                // this line is specific to JavaNode !! Maybe we need to do something else for LightLXC
                "bind {redis-ip}\n" +
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
                "wget \"http://sd-35000.dedibox.fr:8080/archiva/repository/internal/org/diversify/ringo/1-REGULAR/ringo-1-REGULAR.zip\" --content-disposition -O \"/tmp/sosie.zip\"\n" +
                "tar -xvf \"/tmp/sosie.zip\" -C \"/tmp/\"\n" +
                "rm -rf \"/tmp/sosie.zip\"\n" +
                "cd \"/tmp/\"\n" +
                "rm -rf mdms\n" +
                "git config --system http.sslVerify false\n" +
                "git clone https://github.com/maxleiko/mdms-ringojs.git mdms\n" +
                // this line is specific to JavaNode !! Maybe we need to do something else for LightLXC
                "redis-cli -h {redis-ip} -p 6379 FLUSHDB\n" +
                "rm \"/tmp/mdms/config.json\"\n" +
                "cat > \"/tmp/mdms/config.json\" << EOF\n" +
                "{\n" +
                // this line is specific to JavaNode !! Maybe we need to do something else for LightLXC
                "    \"redis-server\": \"{redis-ip}\",\n" +
                "    \"redis-port\":   6379\n" +
                "}\n" +
                "EOF\n" +
                "/tmp/ringojs-0.10/bin/ringo /tmp/mdms/tools/fakedb.js" +
                "'\n")
                scriptBuilder.append("set ").append("diversify").append(configuration[0]).append("Child0").append(".softwareInstaller.started = 'false'\n")

                scriptBuilder.append("add ").append("diversify").append(configuration[0]).append("Child0").append(".lbMonitor : KevoreeLBMonitor\n")
                scriptBuilder.append("set ").append("diversify").append(configuration[0]).append("Child0").append(".lbMonitor.serverName = 'cloud.diversify-project.eu'\n")
                // here we can specify the port and logFile for lbMonitor
                scriptBuilder.append("bind ").append("diversify").append(configuration[0]).append("Child0").append(".lbMonitor.receiveSosieInformation lbMonitorChannelReceiveSosieInformation\n")

                if (modelUpdate) {
                    scriptBuilder.append("add ").append("diversify").append(configuration[0]).append("Child0").append(".sosieRandomModifier : SosieRandomModifier\n")
                }
            }
            line = reader.readLine()
        }
    }

    fun appendSosieConfiguration(nodesConfigurationFile: String, sosiesUrlFile: String, scriptBuilder: StringBuilder) {

        val nodesList = ArrayList<String>()
        val javaParentNodes = ArrayList<String>()
        val portMap = HashMap<String, Int>()
        var redisServer = ""

        var reader = BufferedReader(FileReader(File(nodesConfigurationFile)))
        var line = reader.readLine()
        while (line != null) {
            val configuration = line!!.split(";")

            var i = Integer.parseInt(configuration[3])
            while (i > 0) {
                i--
                nodesList.add("diversify" + configuration[0] + "Child" + i)
                // Ack to ensure port are not the same between component on different childNode but with the same JavaNode parent
                if (configuration[1].equalsIgnoreCase("javanode")) {
                    javaParentNodes.add(configuration[0] + "Child" + i)
                }
            }
            if (configuration.size == 5) {
                nodesList.remove("diversify" + configuration[0] + "Child0")
                // Ack to ensure port are not the same between component on different childNode but with the same JavaNode parent
                if (configuration[1].equalsIgnoreCase("javanode")) {
                    javaParentNodes.remove(configuration[0] + "Child0")
                }
                // this line is specific to JavaNode !! Maybe we need to do something else for LightLXC
                redisServer = configuration[2];
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
                scriptBuilder.append("set ").append(nodeName).append(".").append(sosieName).append(nodeName).append(i).append(".started = 'false'\n")

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
                scriptBuilder.append("set ").append(nodeName).append(".").append(sosieName).append(nodeName).append(i).append(".redisServer = '").append(redisServer).append("'\n")

                scriptBuilder.append("bind ").append(nodeName).append(".").append(sosieName).append(nodeName).append(i).append(".useless nginxChannel\n")
                scriptBuilder.append("bind ").append(nodeName).append(".").append(sosieName).append(nodeName).append(i).append(".sendSosieInformation lbMonitorChannelReceiveSosieInformation\n")

                scriptBuilder.append("add ").append(nodeName).append(".softwareInstaller : ScriptRunner\n")
                scriptBuilder.append("set ").append(nodeName).append(".softwareInstaller.startScript = 'apt-get update\n" +
                "apt-get install git --no-install-recommends -y'\n")
                scriptBuilder.append("set ").append(nodeName).append(".softwareInstaller.started = 'false'\n")

                if (i < nodesList.size - 1) {
                    i++
                } else {
                    i = 0
                }
            }
            line = reader.readLine()
        }
    }
}