package org.diversify.kevoree.components;

import org.kevoree.ComponentInstance;
import org.kevoree.ContainerNode;
import org.kevoree.ContainerRoot;
import org.kevoree.annotation.*;
import org.kevoree.api.ModelService;
import org.kevoree.api.handler.ModelListenerAdapter;
import org.kevoree.komponents.helpers.SynchronizedUpdateCallback;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * User: Erwan Daubert - erwan.daubert@gmail.com
 * Date: 10/03/14
 * Time: 18:03
 *
 * @author Erwan Daubert
 * @version 1.0
 */
@ComponentType
public class SosieRandomModifier {

    @Param(optional = true, defaultValue = "10")
    private int threshold;
    @Param(optional = false)
    private String availableSosies;

    @KevoreeInject
    private ModelService modelService;

    private int nbRequest;
    private ExecutorService executor;
    private AdaptationEngine engine;

    @Start
    public void start() throws Exception {
        if (availableSosies != null && !"".equals(availableSosies)) {
            executor = java.util.concurrent.Executors.newSingleThreadExecutor();
            engine = new AdaptationEngine(availableSosies);
            modelService.registerModelListener(engine);
        }
    }

    @Stop
    public void stop() {
        if (executor != null) {
            executor.shutdown();
            try {
                executor.awaitTermination(5000, TimeUnit.MILLISECONDS);
            } catch (InterruptedException ignored) {
                ignored.printStackTrace();
            }
            modelService.unregisterModelListener(engine);
        }
    }

    @Input
    public synchronized void notificationRequest(Object o) {
        nbRequest++;
        if (threshold <= nbRequest) {
            nbRequest = 0;
            executor.submit(engine);
        }
    }


    class AdaptationEngine extends ModelListenerAdapter implements Runnable {

        private String[] availableSosies;
        private Random random;
        private int selectedIndex;
        private List<ComponentInstance> sosieRunners;

        private SynchronizedUpdateCallback callback;

        AdaptationEngine(String availableSosies) throws Exception {
            if (availableSosies.contains("\n")) {
                this.availableSosies = availableSosies.split("\n");
            } else if (availableSosies.contains(";")) {
                this.availableSosies = availableSosies.split(";");
            } else {
                throw new Exception("Unable to create AdaptationEngine on SosieRandomModifier because you only provide one sosie to randomly apply. More sosies are needed !");
            }
            random = new Random();
            sosieRunners = new ArrayList<ComponentInstance>();
            callback = new SynchronizedUpdateCallback();
        }

        @Override
        public synchronized void modelUpdated() {
            sosieRunners.clear();
            ContainerRoot model = modelService.getCurrentModel().getModel();
            for (ContainerNode n : model.getNodes()) {
                for (ComponentInstance c : n.getComponents()) {
                    if (c.getTypeDefinition().getName().equals("SosieRunner")) {
                        sosieRunners.add(c);
                    }
                }
            }
        }

        @Override
        public synchronized void run() {
            // pick a SosieRunner component
            int sosieNumber = random.nextInt(sosieRunners.size());
            ComponentInstance sosie = sosieRunners.get(sosieNumber);

            final StringBuilder script2 = new StringBuilder();

            script2.append("set ").append(((ContainerNode) sosie.eContainer()).getName()).append(".").append(sosie.getName()).append(".sosieUrl = '").append(availableSosies[selectedIndex]).append("'\n");

            // update its sosie
            callback.initialize();
            modelService.submitScript(script2.toString(), callback);
            callback.waitForResult(10000);
            selectedIndex++;
            if (selectedIndex >= availableSosies.length) {
                selectedIndex = 0;
            }
        }
    }

}
