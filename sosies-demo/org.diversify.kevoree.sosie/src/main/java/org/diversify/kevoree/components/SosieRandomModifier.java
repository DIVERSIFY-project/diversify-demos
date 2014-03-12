package org.diversify.kevoree.components;

import org.kevoree.*;
import org.kevoree.annotation.ComponentType;
import org.kevoree.annotation.*;
import org.kevoree.api.ModelService;
import org.kevoree.api.handler.ModelListenerAdapter;
import org.kevoree.api.handler.UpdateCallback;

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
    @Param(optional = true)
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
        if (threshold <= nbRequest) {
            nbRequest = 0;

        }
    }


    class AdaptationEngine extends ModelListenerAdapter implements Runnable {

        private String[] availableSosies;
        private Random random;
        private int previousSelectedIndex;
        private List<ComponentInstance> sosieRunners;

        AdaptationEngine(String availableSosies) throws Exception {
            if (availableSosies.contains("\n")) {
                this.availableSosies = availableSosies.split("\n");
            } else if (availableSosies.contains(";")) {
                this.availableSosies = availableSosies.split(";");
            } else {
                throw new Exception("Unable to create AdaptationEngine on SosieRandomModifier because you only provide one sosie to randomly applied. More sosies are needed !");
            }

        }

        @Override
        public void modelUpdated() {
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
        public void run() {
            // pick a SosieRunner component
            int sosieNumber = random.nextInt(sosieRunners.size());
            ComponentInstance sosie = sosieRunners.get(sosieNumber);

            final StringBuilder script1 = new StringBuilder();
            final StringBuilder script2 = new StringBuilder();
            final StringBuilder script3 = new StringBuilder();
            script1.append("set ").append(((ContainerNode) sosie.eContainer()).getName()).append(".").append(sosie.getName()).append(".started = 'false'\n");
            script3.append("set ").append(((ContainerNode) sosie.eContainer()).getName()).append(".").append(sosie.getName()).append(".started = 'true'\n");
            for (Port p : sosie.getProvided()) {
                for (MBinding b : p.getBindings()) {
                    script1.append("unbind ").append(((ContainerNode) sosie.eContainer()).getName()).append(".").append(sosie.getName()).append(" ").append(b.getHub().getName()).append("\n");
                    script3.append("bind ").append(((ContainerNode) sosie.eContainer()).getName()).append(".").append(sosie.getName()).append(" ").append(b.getHub().getName()).append("\n");
                }
            }

            previousSelectedIndex++;
            script2.append("set ").append(((ContainerNode) sosie.eContainer()).getName()).append(".").append(sosie.getName()).append(".sosieUrl = ").append(availableSosies[previousSelectedIndex]).append("\n");

            // stop it and unbind it
            modelService.submitScript(script1.toString(), new UpdateCallback() {
                @Override
                public void run(Boolean aBoolean) {
                    if (aBoolean) {
                        // update its sosie
                        modelService.submitScript(script2.toString(), new UpdateCallback() {
                            @Override
                            public void run(Boolean aBoolean) {
                                if (aBoolean) {
                                    // bind it to its previous channel and start it
                                    modelService.submitScript(script3.toString(), new UpdateCallback() {
                                        @Override
                                        public void run(Boolean aBoolean) {

                                        }
                                    });
                                }
                            }
                        });
                    }
                }
            });
        }
    }

}
