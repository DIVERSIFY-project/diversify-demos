/*
package org.diversify.kevoree.deploy

import org.kevoree.ContainerRoot
import org.kevoree.loader.JSONModelLoader
import java.io.File
import java.io.FileInputStream
import org.kevoree.kevscript.KevScriptEngine
import org.kevoree.ContainerNode
import java.util.ArrayList
import java.net.URI
import org.kevoree.serializer.JSONModelSerializer
import org.kevoree.impl.DefaultKevoreeFactory

*/
/**
 * User: Erwan Daubert - erwan.daubert@gmail.com
 * Date: 10/03/14
 * Time: 10:40
 *
 * @author Erwan Daubert
 * @version 1.0
 *//*


fun main(args: Array<String>) {
    var baseModelFile = args.find { arg -> arg.startsWith("baseIaaSModel=") }
    var scriptFile = args.find { arg -> arg.startsWith("script=") }

    var modelFile = args.find { arg -> arg.startsWith("model=") }

    var iaasModel: ContainerRoot ?
    if ((modelFile == null || modelFile == "") && baseModelFile != null && scriptFile != null && baseModelFile != "" && scriptFile != "") {
        iaasModel = JSONModelLoader().loadModelFromStream(FileInputStream(File(baseModelFile!!.substring("baseIaaSModel=".length))))?.get(0) as ContainerRoot
        if (iaasModel != null) {
            val kevScriptEngine = KevScriptEngine()

            kevScriptEngine.executeFromStream(FileInputStream(File(scriptFile!!.substring("script=".length).replaceAll(".kevs$", "-iaas.kevs"))), iaasModel)
        }
    } else {
        iaasModel = JSONModelLoader().loadModelFromStream(FileInputStream(File(modelFile!!.substring("model=".length).replaceAll(".kev$", "-iaas.kev"))))?.get(0) as ContainerRoot?
    }
    var model: ContainerRoot ?
    if ((modelFile == null || modelFile == "") && baseModelFile != null && scriptFile != null && baseModelFile != "" && scriptFile != "") {
        model = DefaultKevoreeFactory().createContainerRoot()
        if (model != null) {
            val kevScriptEngine = KevScriptEngine()

            kevScriptEngine.executeFromStream(FileInputStream(File(scriptFile!!.substring("script=".length))), model)
        }
    } else {
        model = JSONModelLoader().loadModelFromStream(FileInputStream(File(modelFile!!.substring("model=".length))))?.get(0) as ContainerRoot?
    }

    if (model != null && iaasModel != null) {
        println("Starting deployment ...")
        val parentNodes = iaasModel!!.nodes.filter { node -> node.hosts.find { subNode -> subNode.name!!.startsWith("diversify") } != null }
//        sendModel(iaasModel!!, parentNodes, 5, 30000)

        updateModelWithIps(model!!, parentNodes)
        updateModelForRedisServer(model!!)

//        Thread.sleep(10000)

        val nodes = model!!.nodes.filter { node -> node.name!!.startsWith("diversify") }
        while (!sendModel(model!!, nodes, 15, 10000)) {
            Thread.sleep(500)
        }
        System.exit(0)
    }
    System.exit(1)
}

fun sendModel(model: ContainerRoot, nodes: List<ContainerNode>, nbTry: Int, delay : Int): Boolean {
    val modeString = JSONModelSerializer().serialize(model)

    nodes.forEach { node ->
        System.out.println("Try to send model to " + node.name)
        val port = getPort(node)
        var done = false
        getIps(node).all { ip ->
            var KO = false;
            for (i in 1..nbTry) {
                try {
                    val client = WebSocketClient(URI("ws://" + ip + ":" + port))
                    client.connectBlocking()
                    client.send("push/" + modeString)
                    done = true
                    KO = false
                    break
                } catch (e: Exception) {
                    e.printStackTrace()
                    KO = true
                }
            }
            KO
        }
        if (done) {
            System.out.println("model sent to " + node.name)
        } else {
            System.out.println("Unable to sent model to " + node.name)
        }
    }

    return nodes.all { node ->
        val port = getPort(node)
        var done = false
        getIps(node).all { ip ->
            var KO = true;
            for (i in 1..nbTry) {
                try {
                    val client = WebSocketClient(URI("ws://" + ip + ":" + port))
                    client.connectBlocking()
//                    client.send("push/" + modeString)
                    if (client.waitFor(model, delay, 5)) {
                        done = true
                        KO = false
                        break
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            KO
        }
        if (done) {
            System.out.println("node updated: " + node.name)
        } else {
            System.out.println("Node " + node.name + " seems to not be up to date.")
        }
        done
    }
}

fun updateModelWithIps(model: ContainerRoot, parentNodes: List<ContainerNode>): Boolean {
    return parentNodes.all { node ->
        val port = getPort(node)
        !getIps(node).all { ip ->
            val client = WebSocketClient(URI("ws://" + ip + ":" + port))
            client.connectBlocking()
            client.send("pull")
            !client.waitForIps(model, node.hosts.filter { subNode -> subNode.name!!.startsWith("diversify") }, 30000, 15)
        }
    }

}

fun updateModelForRedisServer(model: ContainerRoot) {
    val sosies = ArrayList<String>()
    var redisServer = ""
    var softwareInstallUpdate = ""
    model.nodes.forEach { node ->
        node.components.forEach { component ->
            if (component.typeDefinition!!.name!!.equalsIgnoreCase("SosieRunner")) {
                sosies.add("set " + node.name + "." + component.name + ".redisServer = '")
            } else if (component.typeDefinition!!.name!!.equalsIgnoreCase("ScriptRunner") && component.dictionary!!.findValuesByID("startScript")!!.value!!.contains("/etc/init.d/redis-server restart")) {
                redisServer = getIps(node).get(0)

                softwareInstallUpdate = "set " + node.name + "." + component.name + ".startScript = '" + component.dictionary!!.findValuesByID("startScript")!!.value!!.replace("{redis-ip}", redisServer).replace("\\", "\\\\").replace("\'", "\\'") + "'";
            }
        }
    }

    val script = StringBuilder()

    sosies.forEach { sosie ->
        script.append(sosie + redisServer + "'\n")
    }
    script.append(softwareInstallUpdate + "\n")

    val engine = KevScriptEngine()

    engine.execute(script.toString(), model)
}

fun getPort(node: ContainerNode): Int {
    var group = node.groups.find { group -> group.name == "sync" }
    if (group != null) {
        val fragmentDictionary = group!!.findFragmentDictionaryByID(node.name!!)
        if (fragmentDictionary != null) {
            val value = fragmentDictionary.findValuesByID("port")
            if (value != null) {
                return Integer.parseInt(value.value!!)
            }
        }
    }
    return 9000
}

fun getIps(node: ContainerNode): List<String> {
    val ips = ArrayList<String>()

    node.networkInformation.forEach { ni ->
        ni.values.forEach { np ->
            if (ni.name!!.contains("ip") || np.name!!.contains("ip")) {
                ips.add(np.value!!)
            }
        }
    }
    if (ips.size() == 0) {

    }
    return ips
}
*/
