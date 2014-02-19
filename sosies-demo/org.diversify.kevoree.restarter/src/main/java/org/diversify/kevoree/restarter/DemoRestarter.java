package org.diversify.kevoree.restarter;

import org.kevoree.ComponentInstance;
import org.kevoree.ContainerNode;
import org.kevoree.ContainerRoot;
import org.kevoree.annotation.*;
import org.kevoree.api.ModelService;
import org.kevoree.api.handler.ModelListener;
import org.kevoree.api.handler.UpdateCallback;
import org.kevoree.library.javase.http.samples.pages.SimpleTemplatingStaticFileHandler;
import org.kevoree.modeling.api.KMFContainer;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * User: Erwan Daubert - erwan.daubert@gmail.com
 * Date: 19/02/14
 * Time: 15:56
 *
 * @author Erwan Daubert
 * @version 1.0
 */
@ComponentType
public class DemoRestarter extends SimpleTemplatingStaticFileHandler implements ModelListener {

    @Param(optional = true, defaultValue = "SosieRunner")
    private String componentType;
    @KevoreeInject
    private ModelService modelService;

    private List<String> componentPaths;

    @Start
    public void start() throws Exception {
        setTemplates("pattern=" + urlPattern);
        super.start();
        componentPaths = new ArrayList<String>();
        modelService.registerModelListener(this);
    }

    @Stop
    public void stop() throws Exception {
        super.stop();
        modelService.unregisterModelListener(this);
        if (componentPaths != null) {
            componentPaths.clear();
            componentPaths = null;
        }
    }

    @Override
    protected synchronized void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (req.getRequestURI().toLowerCase().endsWith("restart") || req.getRequestURI().toLowerCase().endsWith("restart/")) {
            final StringBuilder stopScriptBuilder = new StringBuilder();
            final StringBuilder startScriptBuilder = new StringBuilder();
            for (String componentPath : componentPaths) {
                KMFContainer component = modelService.getCurrentModel().getModel().findByPath(componentPath);
                if (component != null && component instanceof ComponentInstance) {
                    stopScriptBuilder.append("set ").append(((ContainerNode) component.eContainer()).getName()).append(".").append(((ComponentInstance) component).getName()).append(".started = 'false'\n");
                    startScriptBuilder.append("set ").append(((ContainerNode) component.eContainer()).getName()).append(".").append(((ComponentInstance) component).getName()).append(".started = 'true'\n");
                }
            }

            System.err.println(stopScriptBuilder.toString());
            modelService.unregisterModelListener(this);
            modelService.submitScript(stopScriptBuilder.toString(), new UpdateCallback() {
                @Override
                public void run(Boolean aBoolean) {
                    if (aBoolean) {
                        System.err.println(startScriptBuilder.toString());
                        modelService.submitScript(startScriptBuilder.toString(), new UpdateCallback() {
                            @Override
                            public void run(Boolean aBoolean) {
                                modelService.registerModelListener(DemoRestarter.this);
                            }
                        });
                    }
                }
            });
            PrintWriter writer = resp.getWriter();
            writer.write("done");
            writer.flush();
        } else {
            super.doGet(req, resp);
        }
    }


    @Override
    public boolean preUpdate(ContainerRoot containerRoot, ContainerRoot containerRoot2) {
        return true;
    }

    @Override
    public boolean initUpdate(ContainerRoot containerRoot, ContainerRoot containerRoot2) {
        return true;
    }

    @Override
    public boolean afterLocalUpdate(ContainerRoot containerRoot, ContainerRoot containerRoot2) {
        return true;
    }

    @Override
    public synchronized void modelUpdated() {
        componentPaths.clear();
        for (ContainerNode node : modelService.getCurrentModel().getModel().getNodes()) {
            for (ComponentInstance component : node.getComponents()) {
                if (componentType.equals(component.getTypeDefinition().getName())) {
                    componentPaths.add(component.path());
                }
            }
        }
    }

    @Override
    public void preRollback(ContainerRoot containerRoot, ContainerRoot containerRoot2) {

    }

    @Override
    public void postRollback(ContainerRoot containerRoot, ContainerRoot containerRoot2) {

    }
}
