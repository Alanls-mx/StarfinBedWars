package com.starfinplugins.bedwars.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class ServiceLifecycleLinkageTest {
    @Test public void missingClassDoesNotAbortOtherServiceShutdown() throws Exception {
        final List<String> calls = new ArrayList<String>();
        ServiceLifecycle lifecycle = new ServiceLifecycle();
        lifecycle.add(service("worlds", calls, false, false));
        lifecycle.add(service("shops", calls, false, true));
        lifecycle.add(service("visuals", calls, false, false));
        lifecycle.start();

        try {
            lifecycle.stop();
            org.junit.Assert.fail("Should report the linkage failure after cleanup.");
        } catch (Exception error) {
            assertEquals("Falha ao encerrar servicos.", error.getMessage());
            assertEquals(1, error.getSuppressed().length);
            assertTrue(error.getSuppressed()[0] instanceof NoClassDefFoundError);
        }
        assertEquals(Arrays.asList(
            "start:worlds", "start:shops", "start:visuals",
            "stop:visuals", "stop:shops", "stop:worlds"), calls);
        assertTrue(lifecycle.getStartedServiceNames().isEmpty());
    }

    @Test public void startupLinkageFailureStillShutsDownStartedServices() {
        final List<String> calls = new ArrayList<String>();
        ServiceLifecycle lifecycle = new ServiceLifecycle();
        lifecycle.add(service("worlds", calls, false, false));
        lifecycle.add(service("shops", calls, true, false));
        try {
            lifecycle.start();
            org.junit.Assert.fail("Expected linkage error on startup.");
        } catch (NoClassDefFoundError expected) {
            assertEquals(Arrays.asList(
                "start:worlds", "start:shops",
                "stop:shops", "stop:worlds"), calls);
        } catch (Exception error) {
            org.junit.Assert.fail("Unexpected checked exception: " + error.getMessage());
        }
    }

    private static ManagedService service(final String name, final List<String> calls,
                                          final boolean failStart, final boolean failStop) {
        return new ManagedService() {
            @Override public String getName() { return name; }
            @Override public void start() {
                calls.add("start:" + name);
                if (failStart) throw new NoClassDefFoundError("NPC missing on startup");
            }
            @Override public void stop() {
                calls.add("stop:" + name);
                if (failStop) throw new NoClassDefFoundError("NPC missing on shutdown");
            }
        };
    }
}
