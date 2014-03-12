package org.diversify.kevoree.restarter;

import org.kevoree.*;
import org.kevoree.annotation.ComponentType;
import org.kevoree.annotation.*;
import org.kevoree.api.BootstrapService;
import org.kevoree.api.KevScriptService;
import org.kevoree.api.ModelService;
import org.kevoree.api.handler.ModelListener;
import org.kevoree.komponents.helpers.SynchronizedUpdateCallback;
import org.kevoree.library.javase.http.samples.pages.SimpleTemplatingStaticFileHandler;
import org.kevoree.loader.JSONModelLoader;
import org.kevoree.modeling.api.KMFContainer;
import org.kevoree.serializer.JSONModelSerializer;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * User: Erwan Daubert - erwan.daubert@gmail.com
 * Date: 19/02/14
 * Time: 15:56
 *
 * @author Erwan Daubert
 * @version 1.0
 */
@ComponentType
public class DemoManager extends SimpleTemplatingStaticFileHandler implements ModelListener {

    @Param(optional = true, defaultValue = "SosieRunner,ModelSwitcher")
    private String componentTypes;
    @Param(optional = true, defaultValue = "nginx,softwareInstaller")
    private String configuratorComponents;
    @KevoreeInject
    protected ModelService modelService;
    @KevoreeInject
    protected BootstrapService bootstrapService;
    @KevoreeInject
    protected KevScriptService kevScriptService;

    private List<String> componentPaths;
    private List<String> configuratorPaths;

    private JSONModelSerializer serializer;
    private JSONModelLoader loader;
    private int previousModel;
    private SynchronizedUpdateCallback callback;

    private String[] models;

    @Start
    public void start() throws Exception {
        setTemplates("pattern=" + urlPattern);
        super.start();
        componentPaths = new ArrayList<String>();
        configuratorPaths = new ArrayList<String>();
        modelService.registerModelListener(this);
        serializer = new JSONModelSerializer();
        loader = new JSONModelLoader();
        previousModel = 0;
        callback = new SynchronizedUpdateCallback();

        Set<String> urls = new HashSet<String>();
        urls.add("http://sd-35000.dedibox.fr:8080/archiva/repository/internal/");

        File models = bootstrapService.resolve("mvn:org.diversify:org.diversify.kevoree.modelSwitcher:latest", urls);
        if (models != null && models.exists()) {
            this.models = new String[3];

            ZipFile zipFile = new ZipFile(models);

            ZipEntry entry = zipFile.getEntry("model1.kev");
            InputStream inputStream = zipFile.getInputStream(entry);
            this.models[0] = org.kevoree.komponents.helpers.Reader.copyFileFromStream(inputStream, System.getProperty("java.io.tmpdir"), "model1.kev", true);

            entry = zipFile.getEntry("model2.kev");
            inputStream = zipFile.getInputStream(entry);
            this.models[1] = org.kevoree.komponents.helpers.Reader.copyFileFromStream(inputStream, System.getProperty("java.io.tmpdir"), "model2.kev", true);

            entry = zipFile.getEntry("model3.kev");
            inputStream = zipFile.getInputStream(entry);
            this.models[2] = org.kevoree.komponents.helpers.Reader.copyFileFromStream(inputStream, System.getProperty("java.io.tmpdir"), "model3.kev", true);
        } else {
            throw new Exception("Unable to get models");
        }
    }

    @Stop
    public void stop() throws Exception {
        super.stop();
        modelService.unregisterModelListener(this);
        if (componentPaths != null) {
            componentPaths.clear();
            componentPaths = null;
        }
        if (configuratorPaths != null) {
            configuratorPaths.clear();
            configuratorPaths = null;
        }
        if (models != null) {
            new File(models[0]).delete();
            new File(models[1]).delete();
            new File(models[2]).delete();
        }
    }

    /*private boolean isRunning;
    private boolean isStopping;
    private boolean isStarting;
    private boolean isConfiguring;
    private boolean isSwitching;*/

    @Override
    protected synchronized void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (req.getRequestURI().toLowerCase().endsWith("stopsosie") || req.getRequestURI().toLowerCase().endsWith("stopsosie/")) {
            final StringBuilder stopScriptBuilder = new StringBuilder();
            for (String componentPath : componentPaths) {
                KMFContainer component = modelService.getCurrentModel().getModel().findByPath(componentPath);
                if (component != null && component instanceof ComponentInstance) {
                    stopScriptBuilder.append("set ").append(((ContainerNode) component.eContainer()).getName()).append(".").append(((ComponentInstance) component).getName()).append(".started = 'false'\n");
                }
            }

            System.err.println(stopScriptBuilder.toString());
            modelService.unregisterModelListener(this);
            callback.initialize();
            modelService.submitScript(stopScriptBuilder.toString(), callback);
            if (callback.waitForResult(10000)) {
                modelService.registerModelListener(DemoManager.this);
            }
            PrintWriter writer = resp.getWriter();
            writer.write("done");
            writer.flush();
        } else if (req.getRequestURI().toLowerCase().endsWith("startsosie") || req.getRequestURI().toLowerCase().endsWith("startsosie/")) {
            final StringBuilder startScriptBuilder = new StringBuilder();
            for (String componentPath : componentPaths) {
                KMFContainer component = modelService.getCurrentModel().getModel().findByPath(componentPath);
                if (component != null && component instanceof ComponentInstance) {
                    startScriptBuilder.append("set ").append(((ContainerNode) component.eContainer()).getName()).append(".").append(((ComponentInstance) component).getName()).append(".started = 'true'\n");
                }
            }

            modelService.unregisterModelListener(this);
            System.err.println(startScriptBuilder.toString());
            callback.initialize();
            modelService.submitScript(startScriptBuilder.toString(), callback);
            if (callback.waitForResult(10000)) {
                modelService.registerModelListener(DemoManager.this);
            }

            PrintWriter writer = resp.getWriter();
            writer.write("done");
            writer.flush();
        } else if (req.getRequestURI().toLowerCase().endsWith("configuresystem") || req.getRequestURI().toLowerCase().endsWith("configuresystem/")) {
            final StringBuilder stopScriptBuilder = new StringBuilder();
            final StringBuilder startScriptBuilder = new StringBuilder();
            for (String componentPath : configuratorPaths) {
                KMFContainer component = modelService.getCurrentModel().getModel().findByPath(componentPath);
                if (component != null && component instanceof ComponentInstance) {
                    stopScriptBuilder.append("set ").append(((ContainerNode) component.eContainer()).getName()).append(".").append(((ComponentInstance) component).getName()).append(".started = 'false'\n");
                    startScriptBuilder.append("set ").append(((ContainerNode) component.eContainer()).getName()).append(".").append(((ComponentInstance) component).getName()).append(".started = 'true'\n");
                }
            }

            System.err.println(stopScriptBuilder.toString());
            modelService.unregisterModelListener(this);
            callback.initialize();
            modelService.submitScript(stopScriptBuilder.toString(), callback);
            if (callback.waitForResult(10000)) {
                try {
                    Thread.sleep(30000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                System.err.println(startScriptBuilder.toString());
                callback.initialize();
                modelService.submitScript(startScriptBuilder.toString(), callback);
                if (callback.waitForResult(10000)) {
                    modelService.registerModelListener(DemoManager.this);
                }
            }

            PrintWriter writer = resp.getWriter();
            writer.write("done");
            writer.flush();
        } else if (req.getRequestURI().toLowerCase().endsWith("switch")) {
            ContainerRoot model = (ContainerRoot) loader.loadModelFromStream(new FileInputStream(models[previousModel])).get(0);
            StringBuilder script = new StringBuilder();
            for (ContainerNode node : model.getNodes()) {
                for (NetworkInfo ni : node.getNetworkInformation()) {
                    for (NetworkProperty np : ni.getValues()) {
                        if (ni.getName().contains("ip") || np.getName().contains("ip")) {
                            script.append("network ").append(node.getName()).append(".").append(ni.getName()).append(".").append(np.getName()).append(" ").append(np.getValue()).append("\n");
                        }
                    }
                }
            }
            try {
                kevScriptService.execute(script.toString(), model);
            } catch (Exception ignored) {
            }
            modelService.unregisterModelListener(this);
            callback.initialize();
            modelService.update(model, callback);
            PrintWriter writer = resp.getWriter();
            if (callback.waitForResult(10000)) {
                writer.write("done");
                previousModel++;
                if (previousModel >= 3) {
                    previousModel = 0;
                }
            } else {
                writer.write("failed");
            }
            modelService.registerModelListener(this);
            writer.flush();
        } else {
            super.doGet(req, resp);
        }
    }


    @Override
    public synchronized boolean preUpdate(ContainerRoot containerRoot, ContainerRoot containerRoot2) {
        /*if (isRunning) {
            if (isConfiguring) {
                if (isStopping) {
                    boolean ok = true;
                    for (String componentPath : configuratorPaths) {
                        KMFContainer component = modelService.getCurrentModel().getModel().findByPath(componentPath);
                        if (component != null && component instanceof ComponentInstance) {
                            if ((((ComponentInstance) component).getStarted())) {
                                ok = false;
                                break;
                            }
                        }
                    }
                    return ok;
                } else if (isStarting) {
                    boolean ok = true;
                    for (String componentPath : configuratorPaths) {
                        KMFContainer component = modelService.getCurrentModel().getModel().findByPath(componentPath);
                        if (component != null && component instanceof ComponentInstance) {
                            if (!(((ComponentInstance) component).getStarted())) {
                                ok = false;
                                break;
                            }
                        }
                    }
                    return ok;
                } else {
                    return true;
                }
            } else if (isStopping) {
                boolean ok = true;
                for (String componentPath : componentPaths) {
                    KMFContainer component = modelService.getCurrentModel().getModel().findByPath(componentPath);
                    if (component != null && component instanceof ComponentInstance) {
                        if ((((ComponentInstance) component).getStarted())) {
                            ok = false;
                            break;
                        }
                    }
                }
                return ok;
            } else if (isStarting) {
                boolean ok = true;
                for (String componentPath : componentPaths) {
                    KMFContainer component = modelService.getCurrentModel().getModel().findByPath(componentPath);
                    if (component != null && component instanceof ComponentInstance) {
                        if (!(((ComponentInstance) component).getStarted())) {
                            ok = false;
                            break;
                        }
                    }
                }
                return ok;
            } else if (isSwitching) {
                return true;
            }
        }*/
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
                if (componentTypes.contains(component.getTypeDefinition().getName())) {
                    componentPaths.add(component.path());
                }
                if (configuratorComponents.contains(component.getName())) {
                    configuratorPaths.add(component.path());
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
