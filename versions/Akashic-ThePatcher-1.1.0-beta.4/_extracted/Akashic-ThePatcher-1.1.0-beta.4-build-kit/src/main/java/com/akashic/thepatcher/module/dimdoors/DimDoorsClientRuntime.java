package com.akashic.thepatcher.module.dimdoors;

import com.akashic.thepatcher.util.BundleLog;
import java.util.Map;

/** No native dimension loading, network requests, packet rewriting or server save access. */
public final class DimDoorsClientRuntime {
    static final DeferredLinks QUEUE = new DeferredLinks();
    static DeferredLinks.Access access = new DimDoorsReflectionAccess();
    private static volatile Thread clientThread;
    private static long ticks, reportedExpired, reportedOverflow, reportedReplayed;
    private DimDoorsClientRuntime() {}
    public static Object safeMapGet(Map<?, ?> map, Object key) { return map == null ? null : map.get(key); }
    public static void noteClientThread() { clientThread = Thread.currentThread(); }
    public static void onTileLink(Object watcher, Object link, Object tile, Object network) {
        if (watcher == null || link == null || tile == null) throw new IllegalArgumentException("null DimDoors notification");
        long before = QUEUE.deferred;
        QUEUE.submit(watcher, link, tile, network, access, Thread.currentThread() == clientThread);
        if (before == 0 && QUEUE.deferred > 0)
            BundleLog.info("DimDoors S35 link deferred until authoritative dimension data is ready; tile NBT retained.");
    }
    public static void tick() {
        if (Thread.currentThread() != clientThread) return;
        QUEUE.tick(access);
        if (++ticks % 100 != 0) return;
        if (QUEUE.expired != reportedExpired || QUEUE.overflow != reportedOverflow) {
            BundleLog.info("WARN: DimDoors pending links not synchronized: " + QUEUE.status()
                + ". Missing server metadata is not fabricated.");
            reportedExpired = QUEUE.expired; reportedOverflow = QUEUE.overflow;
        }
        if (QUEUE.replayed != reportedReplayed) {
            BundleLog.info("DimDoors deferred links replayed through native watcher: " + QUEUE.status());
            reportedReplayed = QUEUE.replayed;
        }
    }
    public static void invalidate() { QUEUE.invalidate(); }
}
