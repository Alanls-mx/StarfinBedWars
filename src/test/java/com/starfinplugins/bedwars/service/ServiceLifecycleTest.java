package com.starfinplugins.bedwars.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class ServiceLifecycleTest {
    @Test
    public void startsInOrderAndStopsInReverseOrder() throws Exception {
        final List<String> calls = new ArrayList<String>();
        ServiceLifecycle lifecycle = new ServiceLifecycle();
        lifecycle.add(service("first", calls, false));
        lifecycle.add(service("second", calls, false));

        lifecycle.start();
        assertEquals(Arrays.asList("start:first", "start:second"), calls);
        assertEquals(Arrays.asList("first", "second"), lifecycle.getStartedServiceNames());

        lifecycle.stop();
        assertEquals(Arrays.asList("start:first", "start:second", "stop:second", "stop:first"), calls);
        assertEquals(0, lifecycle.getStartedServiceNames().size());
    }

    @Test
    public void rollsBackStartedServicesWhenInitializationFails() throws Exception {
        final List<String> calls = new ArrayList<String>();
        ServiceLifecycle lifecycle = new ServiceLifecycle();
        lifecycle.add(service("first", calls, false));
        lifecycle.add(service("broken", calls, true));

        try {
            lifecycle.start();
            fail("Initialization failure was ignored");
        } catch (IllegalStateException expected) {
            assertEquals(Arrays.asList("start:first", "start:broken", "stop:broken", "stop:first"), calls);
            assertEquals(0, lifecycle.getStartedServiceNames().size());
        }
    }

    private ManagedService service(final String name, final List<String> calls, final boolean failStart) {
        return new ManagedService() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public void start() {
                calls.add("start:" + name);
                if (failStart) {
                    throw new IllegalStateException("Expected test failure");
                }
            }

            @Override
            public void stop() {
                calls.add("stop:" + name);
            }
        };
    }
}
