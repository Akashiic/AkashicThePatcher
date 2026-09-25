package com.akashic.thepatcher.module.startupaudit;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import cpw.mods.fml.common.ModContainer;
import cpw.mods.fml.common.event.FMLEvent;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class FmlPhaseAuditHooks {

    private static final Logger LOG =
        LogManager.getLogger("Akashic-ThePatcher/FMLPhaseAudit");

    private static final int TOP_LIMIT = 15;
    private static final ThreadMXBean THREAD_MX =
        ManagementFactory.getThreadMXBean();

    private static final ThreadLocal<PhaseState> PHASE =
        new ThreadLocal<PhaseState>();

    private static final ThreadLocal<ArrayDeque<ModToken>> MOD_STACK =
        new ThreadLocal<ArrayDeque<ModToken>>() {
            @Override
            protected ArrayDeque<ModToken> initialValue() {
                return new ArrayDeque<ModToken>();
            }
        };

    private static final ThreadLocal<ArrayDeque<SpanToken>> SPAN_STACK =
        new ThreadLocal<ArrayDeque<SpanToken>>() {
            @Override
            protected ArrayDeque<SpanToken> initialValue() {
                return new ArrayDeque<SpanToken>();
            }
        };

    private FmlPhaseAuditHooks() {}

    public static void phaseEnter(FMLEvent event) {
        if (!enabled()) return;

        PhaseState stale = PHASE.get();
        if (stale != null) {
            LOG.warn(
                "FMLPhaseAudit stale phase replaced old={} new={}",
                stale.name,
                eventName(event));
        }

        PHASE.set(
            new PhaseState(
                eventName(event),
                System.nanoTime(),
                threadCpuNanos(),
                usedHeap()));
    }

    public static void phaseExit(FMLEvent event) {
        PhaseState state = PHASE.get();
        if (state == null) return;

        PHASE.remove();

        long wallNanos =
            System.nanoTime() - state.startedWallNanos;
        long cpuNow = threadCpuNanos();
        long cpuNanos =
            state.startedCpuNanos >= 0L && cpuNow >= 0L
                ? Math.max(0L, cpuNow - state.startedCpuNanos)
                : -1L;

        long callbacksNanos = 0L;
        for (ModSample sample : state.samples) {
            callbacksNanos += sample.nanos;
        }

        long outsideNanos =
            Math.max(0L, wallNanos - callbacksNanos);

        List<ModSample> sorted =
            new ArrayList<ModSample>(state.samples);

        Collections.sort(
            sorted,
            new Comparator<ModSample>() {
                @Override
                public int compare(
                    ModSample left,
                    ModSample right) {

                    if (left.nanos == right.nanos) return 0;
                    return left.nanos < right.nanos ? 1 : -1;
                }
            });

        int over50 = 0;
        int over250 = 0;
        int over1000 = 0;

        for (ModSample sample : state.samples) {
            long millis = sample.nanos / 1_000_000L;
            if (millis >= 50L) over50++;
            if (millis >= 250L) over250++;
            if (millis >= 1000L) over1000++;
        }

        StringBuilder top = new StringBuilder();

        int limit =
            Math.min(TOP_LIMIT, sorted.size());

        for (int i = 0; i < limit; i++) {
            if (i > 0) top.append(',');

            ModSample sample = sorted.get(i);

            top.append(sample.modId)
                .append(':')
                .append(sample.nanos / 1_000_000L)
                .append("ms");
        }

        long heapDelta =
            usedHeap() - state.startedHeapBytes;

        LOG.info(
            "FMLPhaseAudit PHASE event={} observedAs={} wall={}ms cpu={}ms callbackSum={}ms outsideCallbacks={}ms "
                + "callbacks={} over50={} over250={} over1000={} heapDelta={}KiB top={}",
            state.name,
            eventName(event),
            Long.valueOf(wallNanos / 1_000_000L),
            Long.valueOf(cpuNanos < 0L ? -1L : cpuNanos / 1_000_000L),
            Long.valueOf(callbacksNanos / 1_000_000L),
            Long.valueOf(outsideNanos / 1_000_000L),
            Integer.valueOf(state.samples.size()),
            Integer.valueOf(over50),
            Integer.valueOf(over250),
            Integer.valueOf(over1000),
            Long.valueOf(heapDelta / 1024L),
            top.toString());
    }

    public static void modEnter(
        FMLEvent event,
        ModContainer mod) {

        if (!enabled()) return;

        MOD_STACK.get().push(
            new ModToken(
                eventName(event),
                modId(mod),
                System.nanoTime()));
    }

    public static void modExit(
        FMLEvent event,
        ModContainer mod) {

        ArrayDeque<ModToken> stack =
            MOD_STACK.get();

        if (stack.isEmpty()) return;

        ModToken token = stack.pop();

        long nanos =
            System.nanoTime() - token.startedNanos;

        PhaseState state = PHASE.get();

        if (state != null) {
            state.samples.add(
                new ModSample(
                    token.modId,
                    nanos));
        }
    }

    public static void spanEnter(String name) {
        if (!enabled()) return;

        SPAN_STACK.get().push(
            new SpanToken(
                name,
                System.nanoTime(),
                threadCpuNanos(),
                usedHeap()));
    }

    public static void spanExit(String name) {
        ArrayDeque<SpanToken> stack =
            SPAN_STACK.get();

        if (stack.isEmpty()) return;

        SpanToken token = stack.pop();

        long wall =
            System.nanoTime() - token.startedWallNanos;

        long cpuNow = threadCpuNanos();
        long cpu =
            token.startedCpuNanos >= 0L && cpuNow >= 0L
                ? Math.max(0L, cpuNow - token.startedCpuNanos)
                : -1L;

        LOG.info(
            "FMLPhaseAudit SPAN name={} observedAs={} wall={}ms cpu={}ms heapDelta={}KiB",
            token.name,
            name,
            Long.valueOf(wall / 1_000_000L),
            Long.valueOf(cpu < 0L ? -1L : cpu / 1_000_000L),
            Long.valueOf(
                (usedHeap() - token.startedHeapBytes)
                    / 1024L));
    }

    private static boolean enabled() {
        return Boolean.parseBoolean(
            System.getProperty(
                "akashic.fmlPhaseAudit",
                "true"));
    }

    private static String eventName(FMLEvent event) {
        if (event == null) return "null";

        try {
            String type = event.getEventType();
            if (type != null && !type.isEmpty()) {
                return type;
            }
        } catch (Throwable ignored) {}

        return event.getClass().getName();
    }

    private static String modId(ModContainer mod) {
        if (mod == null) return "unknown";

        try {
            String id = mod.getModId();
            return id == null ? "unknown" : id;
        } catch (Throwable ignored) {
            return "unknown";
        }
    }

    private static long usedHeap() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private static long threadCpuNanos() {
        try {
            if (!THREAD_MX.isCurrentThreadCpuTimeSupported()) {
                return -1L;
            }

            if (!THREAD_MX.isThreadCpuTimeEnabled()) {
                THREAD_MX.setThreadCpuTimeEnabled(true);
            }

            return THREAD_MX.getCurrentThreadCpuTime();
        } catch (Throwable ignored) {
            return -1L;
        }
    }

    static String summarizeForTest(
        String[] ids,
        long[] nanos,
        int limit) {

        if (ids == null
            || nanos == null
            || ids.length != nanos.length) {

            throw new IllegalArgumentException(
                "mismatched samples");
        }

        List<ModSample> samples =
            new ArrayList<ModSample>();

        for (int i = 0; i < ids.length; i++) {
            samples.add(
                new ModSample(
                    ids[i],
                    nanos[i]));
        }

        Collections.sort(
            samples,
            new Comparator<ModSample>() {
                @Override
                public int compare(
                    ModSample left,
                    ModSample right) {

                    if (left.nanos == right.nanos) return 0;
                    return left.nanos < right.nanos ? 1 : -1;
                }
            });

        StringBuilder result =
            new StringBuilder();

        int count =
            Math.min(limit, samples.size());

        for (int i = 0; i < count; i++) {
            if (i > 0) result.append(',');

            ModSample sample =
                samples.get(i);

            result.append(sample.modId)
                .append(':')
                .append(sample.nanos / 1_000_000L)
                .append("ms");
        }

        return result.toString();
    }

    private static final class PhaseState {
        final String name;
        final long startedWallNanos;
        final long startedCpuNanos;
        final long startedHeapBytes;
        final List<ModSample> samples =
            new ArrayList<ModSample>(256);

        PhaseState(
            String name,
            long startedWallNanos,
            long startedCpuNanos,
            long startedHeapBytes) {

            this.name = name;
            this.startedWallNanos = startedWallNanos;
            this.startedCpuNanos = startedCpuNanos;
            this.startedHeapBytes = startedHeapBytes;
        }
    }

    private static final class ModToken {
        final String phase;
        final String modId;
        final long startedNanos;

        ModToken(
            String phase,
            String modId,
            long startedNanos) {

            this.phase = phase;
            this.modId = modId;
            this.startedNanos = startedNanos;
        }
    }

    private static final class SpanToken {
        final String name;
        final long startedWallNanos;
        final long startedCpuNanos;
        final long startedHeapBytes;

        SpanToken(
            String name,
            long startedWallNanos,
            long startedCpuNanos,
            long startedHeapBytes) {

            this.name = name;
            this.startedWallNanos = startedWallNanos;
            this.startedCpuNanos = startedCpuNanos;
            this.startedHeapBytes = startedHeapBytes;
        }
    }

    private static final class ModSample {
        final String modId;
        final long nanos;

        ModSample(
            String modId,
            long nanos) {

            this.modId = modId;
            this.nanos = nanos;
        }
    }
}
