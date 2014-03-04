package org.diversify.kevoree.nginx;

import org.kevoree.*;
import org.kevoree.annotation.*;
import org.kevoree.annotation.ComponentType;
import org.kevoree.api.Context;
import org.kevoree.api.ModelService;
import org.kevoree.api.Port;
import org.kevoree.api.handler.ModelListenerAdapter;
import org.kevoree.komponents.helpers.ProcessStreamFileLogger;
import org.kevoree.log.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: Erwan Daubert - erwan.daubert@gmail.com
 * Date: 27/02/14
 * Time: 09:18
 *
 * @author Erwan Daubert
 * @version 1.0
 */
@ComponentType
public class NginxConfigurator extends ModelListenerAdapter {

    @Param(optional = true, defaultValue = "localhost")
    private String servers;

    @Output(optional = false)
    private Port useless;

    @KevoreeInject
    private ModelService modelService;
    @KevoreeInject
    protected Context context;

    private static final String commentUpstreamBackend = "###############################################################################\n" +
            "# Definition of the Load balancer backend\n" +
            "# Even distribution of the requests on the different servers\n" +
            "# No ip hash at this point\n" +
            "# We can also define weights for each server in order to favor one or the other\n" +
            "###############################################################################\n";
    private static final String startUpstreamBackend = "upstream backend {\n";
    private static final String endUpstreamBackend = "}\n";

    @Start
    public void start() {
        modelService.registerModelListener(this);
    }

    @Stop
    public void stop() {
        modelService.unregisterModelListener(this);
    }

    @Override
    public void modelUpdated() {
        Map<String, List<String>> ipsForNodes = new HashMap<String, List<String>>();

        ComponentInstance instance = modelService.getCurrentModel().getModel().findNodesByID(context.getNodeName()).findComponentsByID(context.getInstanceName());

        StringBuilder nginxSiteDefinition = new StringBuilder();
        nginxSiteDefinition.append(commentUpstreamBackend).append(startUpstreamBackend);

        for (MBinding b : instance.getRequired().get(0).getBindings()) {
            for (MBinding b1 : b.getHub().getBindings()) {
                if (b1 != b) {
                    ComponentInstance component = ((ComponentInstance) b1.getPort().eContainer());
                    if (component.getDictionary().findValuesByID("port") != null) {
                        ContainerNode node = ((ContainerNode) component.eContainer());
//                        newNodePaths.add(node.path());
                        List<String> ips = getIpsForNode(node);
                        if (ips.size() > 0) {
                            ipsForNodes.put(node.path(), ips);
//                            if (!componentPaths.contains(component.path()) || unresolvedNodeIpPaths.contains(node.path())) {
//                                newComponentPaths.add(component.path());
                            // FIXME we suppose the first ip is the good one but they should be used differently
                            nginxSiteDefinition.append("server ").append(ips.get(0)).append(":").append(component.getDictionary().findValuesByID("port").getValue()).append(";\n");
//                            }
                        }
                    }
                }
            }
        }
        nginxSiteDefinition.append(endUpstreamBackend);
        nginxSiteDefinition.append(servers);

        Log.warn("We will try to update the nginx configuration. Please be aware that this runtime must have the right to update it and reload the nginx configuration");
        try {
            writeSiteDefinition(nginxSiteDefinition.toString(), "/etc/nginx/sites-enabled/" + context.getInstanceName());
        } catch (IOException e) {
            Log.error("Unable to write site definition on /etc/nginx/sites-enabled/{}", e, context.getInstanceName());
        }
        try {
            reloadNginxConfiguration();
        } catch (Exception e) {
            Log.error("Unable to reload the nginx configuration. Please look at the nginx log");
        }
    }

    private List<String> getIpsForNode(ContainerNode n) {
        List<String> ips = new ArrayList<String>();
        for (NetworkInfo ni : n.getNetworkInformation()) {
            if ("ip".equalsIgnoreCase(ni.getName())) {
                for (NetworkProperty np : ni.getValues()) {
                    ips.add(np.getValue());
                }
            } else {
                for (NetworkProperty np : ni.getValues()) {
                    if ("ip".equalsIgnoreCase(np.getName())) {
                        ips.add(np.getValue());
                    }
                }
            }

        }
        return ips;
    }

    private void writeSiteDefinition(String siteDefinition, String location) throws IOException {
        File f = new File(location);
        if ((f.exists() && f.delete() && f.createNewFile()) || !f.exists()) {
            FileOutputStream fo = new FileOutputStream(f);
            fo.write(siteDefinition.getBytes());
            fo.close();
        } else {
            throw new IOException("Unable to create the file: " + location);
        }
    }

    private void reloadNginxConfiguration() throws Exception {
        File standardOutput = new File(System.getProperty("java.io.tmpdir") + File.separator + context.getInstanceName() + ".nginx.log");
        // using restart because reload seems to not start nginx if it is not started
        Process process = new ProcessBuilder().command("/etc/init.d/nginx", "restart").redirectErrorStream(true).start();
        new Thread(new ProcessStreamFileLogger(process.getInputStream(), standardOutput, false)).start();
        process.waitFor();
        if (process.exitValue() != 0) {
            throw new Exception("Unable to reload the nginx configuration");
        }
        standardOutput.delete();
    }
}
