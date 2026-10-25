package com.starfinplugins.bedwars.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ServiceLifecycle {
    private final List<ManagedService> configured = new ArrayList<ManagedService>();
    private final List<ManagedService> started = new ArrayList<ManagedService>();

    public void add(ManagedService service) {
        if (service == null) {
            throw new IllegalArgumentException("service nao pode ser nulo");
        }
        if (!started.isEmpty()) {
            throw new IllegalStateException("Nao e possivel registrar servicos durante a inicializacao");
        }
        configured.add(service);
    }

    public void start() throws Exception {
        if (!started.isEmpty()) {
            throw new IllegalStateException("Servicos ja foram inicializados");
        }
        try {
            for (ManagedService service : configured) {
                started.add(service);
                service.start();
            }
        } catch (Exception | LinkageError startFailure) {
            try {
                stop();
            } catch (Exception stopFailure) {
                startFailure.addSuppressed(stopFailure);
            }
            throw startFailure;
        }
    }

    public void stop() throws Exception {
        Exception firstFailure = null;
        for (int index = started.size() - 1; index >= 0; index--) {
            ManagedService service = started.get(index);
            try {
                service.stop();
            } catch (Exception | LinkageError error) {
                // A missing NMS class must not stop the remaining services
                // (world instances, inventories, schedulers) from closing.
                if (firstFailure == null) {
                    firstFailure = new Exception("Falha ao encerrar servicos.");
                }
                firstFailure.addSuppressed(error);
            }
        }
        started.clear();
        if (firstFailure != null) {
            throw firstFailure;
        }
    }

    public List<String> getStartedServiceNames() {
        List<String> names = new ArrayList<String>();
        for (ManagedService service : started) {
            names.add(service.getName());
        }
        return Collections.unmodifiableList(names);
    }
}
