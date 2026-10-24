package dev.akashic.asynciconcompat.startup;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import cpw.mods.fml.common.ModContainer;
import cpw.mods.fml.common.event.FMLEvent;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class StartupLifecycleProfiler {

    private static final Logger LOG =
        LogManager.getLogger("AkashicAsyncCompat/StartupLifecycle");

    private static final int TOP_COUNT = 15;
    private static final long NANOS_PER_MILLI = 1_000_000L;

    private static final ThreadLocal<ArrayDeque<DispatchSample>> DISPATCH_STACK =
        new ThreadLocal<ArrayDeque<DispatchSample>>() {
            @Override
            protected ArrayDeque<DispatchSample> initialValue() {
                return new ArrayDeque<DispatchSample>();
            }
        };

    private static Phase phase;

    private StartupLifecycleProfiler() {}

    public static synchronized void beginPhase(FMLEvent event) {
        if (!enabled() || event == null) return;

        if (phase != null) {
            finishPhase("implicit-close");
        }

        phase = new Phase(
            event.getClass().getSimpleName(),
            System.nanoTime());
    }

    public static synchronized void endPhase(FMLEvent event) {
        if (!enabled() || event == null || phase == null) return;

        final String name = event.getClass().getSimpleName();
        if (!phase.eventName.equals(name)) {
            finishPhase("event-mismatch:" + name);
            return;
        }

        finishPhase("normal");
    }

    public static void beginDispatch(
        FMLEvent event,
        ModContainer mod) {

        if (!enabled() || event == null || mod == null) return;

        DISPATCH_STACK.get().push(
            new DispatchSample(
                event.getClass().getSimpleName(),
                safeModId(mod),
                safeSource(mod),
                System.nanoTime()));
    }

    public static void endDispatch(
        FMLEvent event,
        ModContainer mod) {

        if (!enabled() || event == null || mod == null) return;

        final ArrayDeque<DispatchSample> stack =
            DISPATCH_STACK.get();
        if (stack.isEmpty()) return;

        final DispatchSample sample = stack.pop();
        final long elapsed =
            Math.max(0L, System.nanoTime() - sample.startedNanos);

        record(
            sample.eventName,
            sample.modId,
            sample.source,
            elapsed);
    }

    private static synchronized void record(
        String eventName,
        String modId,
        String source,
        long nanos) {

        if (phase == null || !phase.eventName.equals(eventName)) {
            return;
        }

        ModTiming timing = phase.mods.get(modId);
        if (timing == null) {
            timing = new ModTiming(modId, source);
            phase.mods.put(modId, timing);
        }

        timing.nanos += nanos;
        timing.calls++;
        phase.dispatchNanos += nanos;
        phase.dispatchCalls++;
    }

    private static void finishPhase(String reason) {
        final Phase current = phase;
        phase = null;
        if (current == null) return;

        final long totalNanos =
            Math.max(0L, System.nanoTime() - current.startedNanos);
        final long outsideNanos =
            Math.max(0L, totalNanos - current.dispatchNanos);

        final List<ModTiming> top =
            new ArrayList<ModTiming>(current.mods.values());

        Collections.sort(
            top,
            new Comparator<ModTiming>() {
                @Override
                public int compare(ModTiming left, ModTiming right) {
                    if (left.nanos == right.nanos) {
                        return left.modId.compareTo(right.modId);
                    }
                    return left.nanos < right.nanos ? 1 : -1;
                }
            });

        final StringBuilder topText = new StringBuilder(512);
        final int limit = Math.min(TOP_COUNT, top.size());
        for (int index = 0; index < limit; index++) {
            if (index > 0) topText.append("; ");

            final ModTiming timing = top.get(index);
            topText
                .append(timing.modId)
                .append('=')
                .append(formatMillis(timing.nanos))
                .append("ms");

            if (timing.calls > 1) {
                topText
                    .append('x')
                    .append(timing.calls);
            }

            if (timing.source != null
                && !timing.source.isEmpty()) {
                topText
                    .append('[')
                    .append(timing.source)
                    .append(']');
            }
        }

        LOG.info(
            "StartupPhase event={} reason={} total={}ms dispatch={}ms outside={}ms modCalls={} mods={} top=[{}]",
            current.eventName,
            reason,
            formatMillis(totalNanos),
            formatMillis(current.dispatchNanos),
            formatMillis(outsideNanos),
            current.dispatchCalls,
            current.mods.size(),
            topText.toString());
    }

    private static boolean enabled() {
        return Boolean.parseBoolean(
            System.getProperty(
                "akashic.startupLifecycleProfiler",
                "true"));
    }

    private static String safeModId(ModContainer mod) {
        try {
            final String id = mod.getModId();
            return id == null ? "<null>" : id;
        } catch (Throwable ignored) {
            return "<error>";
        }
    }

    private static String safeSource(ModContainer mod) {
        try {
            if (mod.getSource() == null) return "";
            return mod.getSource().getName();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String formatMillis(long nanos) {
        final long whole = nanos / NANOS_PER_MILLI;
        final long fraction =
            (nanos % NANOS_PER_MILLI) / 1000L;
        return whole + "." + pad3(fraction);
    }

    private static String pad3(long value) {
        if (value < 10L) return "00" + value;
        if (value < 100L) return "0" + value;
        return Long.toString(value);
    }

    private static final class Phase {
        final String eventName;
        final long startedNanos;
        final Map<String, ModTiming> mods =
            new LinkedHashMap<String, ModTiming>();

        long dispatchNanos;
        int dispatchCalls;

        Phase(String eventName, long startedNanos) {
            this.eventName = eventName;
            this.startedNanos = startedNanos;
        }
    }

    private static final class ModTiming {
        final String modId;
        final String source;

        long nanos;
        int calls;

        ModTiming(String modId, String source) {
            this.modId = modId;
            this.source = source;
        }
    }

    private static final class DispatchSample {
        final String eventName;
        final String modId;
        final String source;
        final long startedNanos;

        DispatchSample(
            String eventName,
            String modId,
            String source,
            long startedNanos) {

            this.eventName = eventName;
            this.modId = modId;
            this.source = source;
            this.startedNanos = startedNanos;
        }
    }
}