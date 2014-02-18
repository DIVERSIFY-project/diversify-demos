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

    scriptBuilder.append("include mvn:org.kevoree.library.java:org.kevoree.library.java.ws:latest\n")
    scriptBuilder.append("include mvn:org.kevoree.library.cloud:org.kevoree.library.cloud.lxc:latest\n")
    scriptBuilder.append("include mvn:org.kevoree.library.cloud:org.kevoree.library.cloud.lightlxc:latest\n")
    scriptBuilder.append("include mvn:org.diversify.demo:nginxconf-generator:latest\n")
    scriptBuilder.append("include mvn:org.diversify:org.diversify.kevoree.loadBalancer:latest\n");

    scriptBuilder.append("add ").append("sync : WSGroup\n")


    val reader = BufferedReader(FileReader(File(nodesConfigurationFile)))
    var line = reader.readLine()
    while (line != null) {
        val configuration = line!!.split(";")
        scriptBuilder.append("add ").append(configuration[0]).append(" : ").append(configuration[1]).append("\n")
        scriptBuilder.append("set sync.port/").append(configuration[0]).append(" = '9000'\n")
        scriptBuilder.append("attach ").append(configuration[0]).append(" sync\n")
        scriptBuilder.append("network ").append(configuration[0]).append(".ip.lan ").append(configuration[2]).append("\n")

        var i = Integer.parseInt(configuration[3])

        while (i >= 0) {
            scriptBuilder.append("add ").append(configuration[0]).append(".").append(configuration[0]).append("Child").append(i).append(" : JavaNode\n")
            i--
        }

        if (configuration.size == 5) {
            scriptBuilder.append("add ").append(configuration[0]).append("Child0").append(".nginx : NginxLoadBalancerComponent\n")
            scriptBuilder.append("add channel : UselessChannel\n")
            scriptBuilder.append("bind ").append(configuration[0]).append("Child0").append(".nginx.outputPort channel\n")

            scriptBuilder.append("add ").append(configuration[0]).append("Child0").append(".lbMonitor : KevoreeLBMonitor\n")
            // here we can specify the port and logFile for lbMonitor
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
    val portMap = HashMap<String, Int>()

    var reader = BufferedReader(FileReader(File(nodesConfigurationFile)))
    var line = reader.readLine()
    while (line != null) {
        val configuration = line!!.split(";")
        var i = Integer.parseInt(configuration[3])
        while (i >= 0) {
            nodesList.add(configuration[0] + "Child" + i)
            i--
        }
        if (configuration.size == 5) {
            nodesList.remove(configuration[0] + "Child0")
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
            }
            scriptBuilder.append("add ").append(nodeName).append(".").append(sosieName).append(" : SosieRunner\n")
            scriptBuilder.append("set ").append(nodeName).append(".").append(sosieName).append(".sosieUrl = '").append(line).append("'\n")
            scriptBuilder.append("set ").append(nodeName).append(".").append(sosieName).append(".sosieName = '").append(sosieName).append("'\n")

            if (portMap.get(nodeName) == null) {
                portMap.put(nodeName, 8080)
            } else {
                portMap.put(nodeName, portMap.remove(nodeName)!! + 1)
            }

            scriptBuilder.append("set ").append(nodeName).append(".").append(sosieName).append(".port = '").append(portMap.get(nodeName)).append("'\n")

            scriptBuilder.append("bind ").append(nodeName).append(".").append(sosieName).append(".useless channel\n")

            if (i < nodesList.size - 1) {
                i++
            } else {
                i = 0
            }
        }
        line = reader.readLine()
    }
}