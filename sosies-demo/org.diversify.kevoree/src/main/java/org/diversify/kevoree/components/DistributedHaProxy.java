package org.diversify.kevoree.components;

import org.kevoree.*;
import org.kevoree.annotation.ComponentType;
import org.kevoree.annotation.KevoreeInject;
import org.kevoree.annotation.Param;
import org.kevoree.api.ModelService;
import org.kevoree.library.java.haproxy.HAProxy;
import org.kevoree.library.java.haproxy.OSHelper;
import org.kevoree.library.java.haproxy.api.Backend;
import org.kevoree.library.java.haproxy.api.Server;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

/**
 * User: Erwan Daubert - erwan.daubert@gmail.com
 * Date: 05/02/14
 * Time: 18:10
 *
 * @author Erwan Daubert
 * @version 1.0
 */
@ComponentType
public class DistributedHaProxy extends HAProxy {

    @Param(optional = false)
    protected String typesList;

    @KevoreeInject
    protected ModelService modelService;

    protected List<String> types;


    private void processTypes() {
        types = Arrays.asList(typesList.split(","));
    }

    @Override
    public void generateConfig(File configFile, ContainerRoot model) throws IOException {
        StringBuilder buffer = new StringBuilder();
        buffer.append(OSHelper.read(this.getClass().getClassLoader().getResourceAsStream("base.cfg")));
        buffer.append("\n");
        HashMap<String, Backend> backends = new HashMap<String, Backend>();
        for (ContainerNode node : model.getNodes()) {
            for (ComponentInstance instance : node.getComponents()) {
                if (types.contains(instance.getTypeDefinition().getName())) {

                    if (!backends.containsKey(instance.path())) {
                        Backend backend = new Backend();
                        backends.put(instance.path(), backend);
                    }
                    Backend backend = backends.get(instance.path());
                    backend.setName(instance.getName());
                    for (String address : getAddresses(node.getName())) {
                        Server s = new Server();
                        s.setIp(address);
                        s.setName(instance.getName());
                        String port = instance.getDictionary().findValuesByID("port").getValue();
//                        s.setPort(instance.getDictionary().findValuesByID("http_port").getValue());
                        s.setPort(port); // set by default because I don't how to change it in SosieRunner
                        backend.getServers().add(s);
                    }
                }
            }
        }

        if (backends.size() > 0) {
            buffer.append("default_backend ");
            String firstKey = backends.keySet().iterator().next();
            buffer.append(backends.get(firstKey).getName());
            buffer.append("\n");
        }
        for (String key : backends.keySet()) {
            buffer.append("\n");
            buffer.append(backends.get(key));
            buffer.append("\n");
        }
        FileWriter writer = new FileWriter(configFile);
        writer.write(buffer.toString());
        writer.close();
    }

    public List<String> getAddresses(String nodeName) {
        List<String> addresses = new ArrayList<String>();

        ContainerNode node = modelService.getCurrentModel().getModel().findNodesByID(nodeName);
        if (node == null) {
            node = modelService.getPendingModel().findNodesByID(nodeName);
        }
        if (node != null) {
            for (NetworkInfo ni : node.getNetworkInformation()) {
                for (NetworkProperty np : ni.getValues()) {
                    if (np.getName().toLowerCase().startsWith("ip") || np.getName().toLowerCase().endsWith("ip")) {
                        addresses.add(np.getValue());
                    }
                }
            }
        }
        return addresses;
    }

    @Override
    public boolean afterLocalUpdate(ContainerRoot currentModel, ContainerRoot proposedModel) {
        return true;
    }

    @Override
    public void modelUpdated() {
        super.afterLocalUpdate(null, modelService.getCurrentModel().getModel());
    }
}
