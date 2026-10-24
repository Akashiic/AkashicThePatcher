package dev.akashic.asynciconcompat;

import static org.junit.Assert.assertEquals;

import cpw.mods.fml.common.eventhandler.EventBus;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

public class CompatHooksConcurrencyTest {

    @Test
    public void chiselListGuardSurvivesConcurrentAdds() throws Exception {
        final List<Object> list = new ArrayList<>();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int worker = 0; worker < 8; worker++) {
                futures.add(pool.submit(() -> {
                    for (int i = 0; i < 5000; i++) {
                        CompatHooks.addChiselSubmapSynchronized(list, new Object());
                    }
                }));
            }
            for (Future<?> future : futures) future.get();
            assertEquals(40000, list.size());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    public void eventBusGuardSerializesRegistrations() throws Exception {
        final TrackingEventBus bus = new TrackingEventBus();
        final CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int worker = 0; worker < 8; worker++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    CompatHooks.registerForgeEventHandlerSynchronized(bus, new Object());
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) future.get();
            assertEquals(1, bus.maxActive.get());
        } finally {
            pool.shutdownNow();
        }
    }

    private static final class TrackingEventBus extends EventBus {
        private final AtomicInteger active = new AtomicInteger();
        private final AtomicInteger maxActive = new AtomicInteger();

        @Override
        public void register(Object target) {
            int now = active.incrementAndGet();
            maxActive.accumulateAndGet(now, Math::max);
            try {
                Thread.sleep(5L);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } finally {
                active.decrementAndGet();
            }
        }
    }
}