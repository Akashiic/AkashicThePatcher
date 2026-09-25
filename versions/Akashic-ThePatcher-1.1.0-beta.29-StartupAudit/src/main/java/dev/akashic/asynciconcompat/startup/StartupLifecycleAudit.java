package dev.akashic.asynciconcompat.startup;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class StartupLifecycleAudit {
    private static final Logger LOG = LogManager.getLogger("Akashic-ThePatcher/FMLLifecycle");
    private static final long SLOW_MS = Long.getLong("akashic.startupAudit.fmlSlowMs", 50L).longValue();
    private static final ThreadLocal<ArrayDeque<Token>> STACK = new ThreadLocal<ArrayDeque<Token>>() {
        @Override protected ArrayDeque<Token> initialValue() { return new ArrayDeque<Token>(); }
    };
    private static String currentPhase;
    private static final List<Entry> entries = new ArrayList<Entry>();
    private static long phaseTotalNanos;

    private StartupLifecycleAudit() {}

    public static void begin(String phase, String modId) {
        if (!Boolean.parseBoolean(System.getProperty("akashic.startupAudit", "true"))) return;
        STACK.get().push(new Token(phase, modId, System.nanoTime()));
    }

    public static void end(String phase, String modId) {
        ArrayDeque<Token> stack = STACK.get();
        if (stack.isEmpty()) return;
        Token t = stack.pop();
        long elapsed = System.nanoTime() - t.started;
        record(t.phase, t.modId, elapsed);
    }

    public static synchronized void flush(String reason) {
        emitCurrent(reason);
    }

    private static synchronized void record(String phase, String modId, long nanos) {
        if (currentPhase == null) currentPhase = phase;
        if (!currentPhase.equals(phase)) {
            emitCurrent("phase-change");
            currentPhase = phase;
        }
        entries.add(new Entry(modId, nanos));
        phaseTotalNanos += nanos;
        long ms = nanos / 1_000_000L;
        if (ms >= SLOW_MS) {
            LOG.info("FMLLifecycle slow phase={} mod={} elapsed={}ms threshold={}ms",
                phase, modId, Long.valueOf(ms), Long.valueOf(SLOW_MS));
        }
    }

    private static void emitCurrent(String reason) {
        if (currentPhase == null || entries.isEmpty()) return;
        List<Entry> sorted = new ArrayList<Entry>(entries);
        Collections.sort(sorted, new Comparator<Entry>() {
            @Override public int compare(Entry a, Entry b) {
                return a.nanos == b.nanos ? 0 : (a.nanos < b.nanos ? 1 : -1);
            }
        });
        StringBuilder top = new StringBuilder();
        int limit = Math.min(12, sorted.size());
        int slow = 0;
        for (Entry e : sorted) {
            if (e.nanos / 1_000_000L >= SLOW_MS) slow++;
        }
        for (int i = 0; i < limit; i++) {
            if (i > 0) top.append(',');
            Entry e = sorted.get(i);
            top.append(e.modId).append(':').append(e.nanos / 1_000_000L).append("ms");
        }
        LOG.info("FMLLifecycle SUMMARY phase={} reason={} callbacks={} total={}ms slow={} threshold={}ms top={}",
            currentPhase, reason, Integer.valueOf(entries.size()),
            Long.valueOf(phaseTotalNanos / 1_000_000L), Integer.valueOf(slow),
            Long.valueOf(SLOW_MS), top.toString());
        entries.clear();
        phaseTotalNanos = 0L;
        currentPhase = null;
    }

    private static final class Token {
        final String phase;
        final String modId;
        final long started;
        Token(String phase, String modId, long started) {
            this.phase = phase == null ? "null" : phase;
            this.modId = modId == null ? "unknown" : modId;
            this.started = started;
        }
    }

    private static final class Entry {
        final String modId;
        final long nanos;
        Entry(String modId, long nanos) {
            this.modId = modId;
            this.nanos = nanos;
        }
    }
}
