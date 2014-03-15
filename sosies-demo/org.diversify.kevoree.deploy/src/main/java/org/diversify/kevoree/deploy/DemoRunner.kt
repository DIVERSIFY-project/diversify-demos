package org.diversify.kevoree.deploy

import org.kevoree.ContainerNode
import org.kevoree.ContainerRoot
import org.kevoree.modeling.api.json.JSONModelSerializer
import java.util.ArrayList
import org.kevoree.resolver.MavenResolver
import java.util.HashSet
import java.util.zip.ZipFile
import java.io.BufferedReader
import java.io.InputStreamReader
import org.kevoree.kevscript.KevScriptEngine
import java.io.FileInputStream
import org.kevoree.loader.JSONModelLoader
import java.io.File
import org.kevoree.cloner.DefaultModelCloner
import org.kevoree.komponents.helpers.Reader
import java.net.URI

/**
 * User: Erwan Daubert - erwan.daubert@gmail.com
 * Date: 14/03/14
 * Time: 13:12
 *
 * @author Erwan Daubert
 * @version 1.0
 */

fun main(args: Array<String>) {

    var local = args.find { arg -> arg.equalsIgnoreCase("local") }
    var deploy = args.find { arg -> arg.equalsIgnoreCase("deploy") }

    if (local == null) {
        val mavenResolver = MavenResolver()
        val urls = HashSet<String>()
        urls.add("http://sd-35000.dedibox.fr:8080/archiva/repository/internal/")

        var modelSwitcher: File?
        if (args.size == 1 && args.get(0).startsWith("mvn:")) {
            modelSwitcher = mavenResolver.resolve(args.get(0), urls)
        } else {
            modelSwitcher = mavenResolver.resolve("mvn:org.diversify:org.diversify.kevoree.modelSwitcher:latest", urls)
        }

        if (modelSwitcher != null && modelSwitcher!!.exists()) {

            var zipFile = ZipFile(modelSwitcher!!);

            val models = Array<String>(4, { i ->
                getModel(i, zipFile)
            })

            process(models, deploy != null)

        }
        System.exit(1);
    } else {
        var iaasModelPath = args.find { arg -> arg.startsWith("iaasModel=") }
        var model1Path = args.find { arg -> arg.startsWith("model1=") }
        var model2Path = args.find { arg -> arg.startsWith("model2=") }
        var model3Path = args.find { arg -> arg.startsWith("model3=") }

        if (iaasModelPath != null && model1Path != null && model2Path != null && model3Path != null) {
            val models = array(iaasModelPath!!.substring("iaasModel=".length), model1Path!!.substring("model1=".length), model2Path!!.substring("model2=".length), model3Path!!.substring("model3=".length))
            process(models, deploy != null)
        } else {
            System.err.println("Missing models");
            System.exit(1)
        }
    }
}

fun process(models: Array<String>, deploy: Boolean) {
    val iaasModel = JSONModelLoader().loadModelFromStream(FileInputStream(File(models.get(0))))?.get(0) as ContainerRoot
    val model1 = JSONModelLoader().loadModelFromStream(FileInputStream(File(models.get(1))))?.get(0) as ContainerRoot
    val model2 = JSONModelLoader().loadModelFromStream(FileInputStream(File(models.get(2))))?.get(0) as ContainerRoot
    val model3 = JSONModelLoader().loadModelFromStream(FileInputStream(File(models.get(3))))?.get(0) as ContainerRoot

    if (deploy) {
        println("Deploying on iaas...")
        sendModelToIaaS(iaasModel)
    }

    updateModel(model1, iaasModel)
    updateModel(model2, iaasModel)
    updateModel(model3, iaasModel)

    val model1Configured = configureSystem(model1)
    val model2Configured = configureSystem(model2)
    val model3Configured = configureSystem(model3)


    val model1Started = startSosies(model1)
    val model2Started = startSosies(model2)
    val model3Started = startSosies(model3)


    val reader = BufferedReader(InputStreamReader(System.`in`))

    println("Boot the system ? [yes, no]:")
    var line = reader.readLine()
    if (line.equals("yes")) {
        sendModelToBootNodes(model1)
        println("Is it OK ? [yes, no]:")
        line = reader.readLine()
        while (!line.equals("yes")) {
            sendModelToBootNodes(model1)
            println("Is it OK ? [yes, no]:")
            line = reader.readLine()
        }
    }

    println("Select configuration [1, 2, 3, quit]:")
    line = reader.readLine()
    while (line != null) {
        if (line!!.equalsIgnoreCase("quit")) {
            System.exit(0)
        } else {
            if (line.equals("1")) {
                sendModel(model1)
                sendModelWithDelayForRoot(model1Configured)
                sendModel(model1Started)
            } else if (line.equals("2")) {
                sendModel(model2)
                sendModelWithDelayForRoot(model2Configured)
                sendModel(model2Started)
            } else if (line.equals("3")) {
                sendModel(model3)
                sendModelWithDelayForRoot(model3Configured)
                sendModel(model3Started)
            } else {

            }
        }
        println("Select configuration [1, 2, 3, quit]:")
        line = reader.readLine()
    }
}

fun getModel(i: Int, zipFile: ZipFile): String {

    var modelName = "model" + i + ".kev"
    if (i == 0) {
        modelName = "model1-iaas.kev"
    }

    var entry = zipFile.getEntry("model" + i + ".kev");
    if (entry != null) {
        var inputStream = zipFile.getInputStream(entry!!);
        return Reader.copyFileFromStream(inputStream, System.getProperty("java.io.tmpdir"), modelName, true)!!
    } else {
        throw Exception("Unable to get models");
    }
}

fun updateModel(model: ContainerRoot, iaasModel: ContainerRoot) {
    val parentNodes = iaasModel.nodes.filter { node -> node.hosts.find { subNode -> subNode.name!!.startsWith("diversify") } != null }

    updateModelWithIps(model, parentNodes)
    updateModelForRedisServer(model)
}

fun sendModelToIaaS(model: ContainerRoot) {

    val nodes = model.nodes.filter { node -> node.hosts.find { subNode -> subNode.name!!.startsWith("diversify") } != null }
    while (!sendModel(model, nodes, 15, 10000)) {
        Thread.sleep(500)
    }
}

fun sendModelToBootNodes(model: ContainerRoot) {

    val nodes = model.nodes.filter { node -> node.name!!.startsWith("diversify") }
    while (!sendModel(model, nodes, 15, 10000)) {
        Thread.sleep(500)
    }
}

fun sendModel(model: ContainerRoot) {

    val nodes = model.nodes.filter { node -> node.name!!.startsWith("diversify") }
    while (!sendModel(model, nodes, 15, 1000)) {
        Thread.sleep(500)
    }
}

fun sendModelWithDelayForRoot(model: ContainerRoot) {

    val nodes = model.nodes.filter { node -> node.name!!.startsWith("diversify") }
    while (!sendModelWithDelayForRoot(model, nodes, 15, 10000, 1000)) {
        Thread.sleep(500)
    }
}

fun sendModel(model: ContainerRoot, nodes: List<ContainerNode>, nbTry: Int, delay: Long): Boolean {
    val modelString = JSONModelSerializer().serialize(model)

    if (modelString != null) {
        nodes.forEach { node ->
            sendModelToNode(node, modelString, nbTry)
        }

        return nodes.all { node ->
            waitForAcknowledge(node, model, nbTry, delay)
        }
    } else {
        return false
    }
}

fun sendModelWithDelayForRoot(model: ContainerRoot, nodes: List<ContainerNode>, nbTry: Int, delayRoot: Long, delay : Long): Boolean {
    val modelString = JSONModelSerializer().serialize(model)

    if (modelString != null) {
        val rootNode = findRootNode(model)
        if (rootNode != null) {
            sendModelToNode(rootNode, modelString, nbTry)
            if (!waitForAcknowledge(rootNode, model, nbTry, delayRoot)) {
                return false
            }
        }

        nodes.forEach { node ->
            if (rootNode == null || !rootNode.name.equals(node.name)) {
                sendModelToNode(node, modelString, nbTry)
            }
        }

        return nodes.all { node ->
            if (rootNode == null || !rootNode.name.equals(node.name)) {
                waitForAcknowledge(node, model, nbTry, delay)
            } else {
                true
            }
        }
    } else {
        return false
    }
}

fun sendModelToNode(node: ContainerNode, modelString: String, nbTry: Int) {
    System.out.println("Try to send model to " + node.name)
    val port = getPort(node)
    var done = false
    getIps(node).all { ip ->
        var KO = false;
        for (i in 1..nbTry) {
            try {
                val client = WebSocketClient(URI("ws://" + ip + ":" + port))
                client.connectBlocking()
                client.send("push/" + modelString)
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

fun waitForAcknowledge(node: ContainerNode, model: ContainerRoot, nbTry: Int, delay: Long): Boolean {
    val port = getPort(node)
    var done = false
    getIps(node).all { ip ->
        var KO = false;
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
                KO = true
            }
        }
        KO
    }
    if (done) {
        System.out.println("node updated: " + node.name)
    } else {
        System.out.println("Node " + node.name + " seems to not be up to date.")
    }
    return done
}

fun findRootNode(model: ContainerRoot): ContainerNode? {
    return model.nodes.find { node ->
        node.components.find { component -> component.name!!.equals("nginx") } != null
        &&
        node.components.find { component -> component.name!!.equals("softwareInstaller") } != null
    }
}

fun configureSystem(model: ContainerRoot): ContainerRoot {
    val script = StringBuilder();
    model.nodes.forEach { node ->
        node.components.forEach { component ->
            if (component.name.equals("nginx") || component.name.equals("softwareInstaller")) {
                script.append("set ").append(node.name!!).append(".").append(component.name!!).append(".started = 'true'\n");
            }
        }
    }

    val newModel = DefaultModelCloner().clone(model)
    if (newModel != null) {
        val engine = KevScriptEngine()
        engine.execute(script.toString(), newModel)
        return newModel
    } else {
        throw Exception("Unable to clone the model...")
    }
}

fun startSosies(model: ContainerRoot): ContainerRoot {
    val script = StringBuilder()
    model.nodes.forEach { node ->
        node.components.forEach { component ->
            if (component.typeDefinition!!.name.equals("SosieRunner")) {
                script.append("set ").append(node.name).append(".").append(component.name).append(".started = 'true'\n");
            }
        }
    }
    val newModel = DefaultModelCloner().clone(model)
    if (newModel != null) {
        val engine = KevScriptEngine()
        engine.execute(script.toString(), newModel)
        return newModel
    } else {
        throw Exception("Unable to clone the model...")
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

fun getPort(node: ContainerNode): Int {
    var group = node.groups.find { group -> group.name.equals("sync") }
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
        ips.add("127.0.0.1")
    }
    return ips
}
