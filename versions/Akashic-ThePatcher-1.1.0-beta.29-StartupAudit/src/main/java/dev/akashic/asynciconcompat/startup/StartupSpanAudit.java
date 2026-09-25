package dev.akashic.asynciconcompat.startup;

import java.util.ArrayDeque;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class StartupSpanAudit {
    private static final Logger LOG = LogManager.getLogger("Akashic-ThePatcher/StartupSpan");
    private static final ThreadLocal<ArrayDeque<Token>> STACK = new ThreadLocal<ArrayDeque<Token>>() {
        @Override protected ArrayDeque<Token> initialValue() { return new ArrayDeque<Token>(); }
    };

    private StartupSpanAudit() {}

    public static void begin(String name) {
        if (!Boolean.parseBoolean(System.getProperty("akashic.startupAudit", "true"))) return;
        Runtime r = Runtime.getRuntime();
        long used = r.totalMemory() - r.freeMemory();
        STACK.get().push(new Token(name, System.nanoTime(), used));
    }

    public static void end(String name) {
        ArrayDeque<Token> stack = STACK.get();
        if (stack.isEmpty()) return;
        Token t = stack.pop();
        long elapsed = System.nanoTime() - t.started;
        Runtime r = Runtime.getRuntime();
        long used = r.totalMemory() - r.freeMemory();
        LOG.info("StartupSpan name={} elapsed={}ms heapDelta={}KiB heapUsed={}MiB thread={}",
            t.name, Long.valueOf(elapsed / 1_000_000L),
            Long.valueOf((used - t.usedBytes) / 1024L),
            Long.valueOf(used / (1024L * 1024L)),
            Thread.currentThread().getName());
    }

    private static final class Token {
        final String name;
        final long started;
        final long usedBytes;
        Token(String name, long started, long usedBytes) {
            this.name = name;
            this.started = started;
            this.usedBytes = usedBytes;
        }
    }
}
