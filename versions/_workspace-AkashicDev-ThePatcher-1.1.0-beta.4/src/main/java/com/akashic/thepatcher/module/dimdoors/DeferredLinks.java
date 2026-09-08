package com.akashic.thepatcher.module.dimdoors;

import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.Iterator;

/** Bounded client mirror notifications. Does not own or initialize dimensional state. */
final class DeferredLinks {
    interface Access {
        Object world(Object tile);
        boolean current(Object tile, Object world, Object network);
        boolean ready(Object watcher, Object link);
        boolean apply(Object watcher, Object link);
    }
    static final int CAPACITY = 512, TTL = 600, BUDGET = 64;
    private final ArrayDeque<Pending> queue = new ArrayDeque<>();
    private long tick, generation;
    long deferred, replayed, stale, expired, overflow, replaced;
    synchronized void submit(Object watcher, Object link, Object tile, Object net, Access access, boolean mainThread) {
        Object world = access.world(tile);
        if (world == null || net == null) { stale++; return; }
        if (mainThread && !access.current(tile, world, net)) { stale++; return; }
        // A newer update always supersedes an older pending notification from this tile.
        for (Iterator<Pending> it = queue.iterator(); it.hasNext();) {
            Pending old = it.next();
            if (old.tile.get() == tile && old.network.get() == net) { it.remove(); replaced++; }
        }
        if (mainThread && access.ready(watcher, link) && access.apply(watcher, link)) return;
        if (queue.size() == CAPACITY) { queue.removeFirst(); overflow++; }
        queue.addLast(new Pending(watcher, link, tile, world, net, generation, tick));
        deferred++;
    }
    synchronized void tick(Access access) {
        tick++;
        int remaining = Math.min(BUDGET, queue.size());
        while (remaining-- > 0) {
            Pending p = queue.removeFirst();
            Object tile = p.tile.get(), world = p.world.get(), net = p.network.get();
            if (p.generation != generation || tile == null || world == null || net == null
                || !access.current(tile, world, net)) { stale++; continue; }
            if (tick - p.created >= TTL) { expired++; continue; }
            if (access.ready(p.watcher, p.link) && access.apply(p.watcher, p.link)) replayed++;
            else queue.addLast(p);
        }
    }
    synchronized void invalidate() { stale += queue.size(); queue.clear(); generation++; }
    synchronized int size() { return queue.size(); }
    synchronized String status() {
        return "pending=" + queue.size() + ", deferred=" + deferred + ", replayed=" + replayed
            + ", stale=" + stale + ", expired=" + expired + ", overflow=" + overflow + ", superseded=" + replaced;
    }
    private static final class Pending {
        final Object watcher, link;
        final WeakReference<Object> tile, world, network;
        final long generation, created;
        Pending(Object watcher, Object link, Object tile, Object world, Object net, long gen, long tick) {
            this.watcher = watcher; this.link = link;
            this.tile = new WeakReference<>(tile); this.world = new WeakReference<>(world);
            this.network = new WeakReference<>(net); generation = gen; created = tick;
        }
    }
}
