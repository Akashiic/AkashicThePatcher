package dev.akashic.asynciconcompat;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.block.Block;
import net.minecraft.item.Item;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class CompatHooks {
    private static final Logger LOG = LogManager.getLogger("AkashicAsyncCompat");

    private static final String[] UNSAFE_ROOTS = {
        "team.chisel.",
        "com.cricketcraft.chisel.",
        "mekanism.",
        "powercrystals.minefactoryreloaded.",
        "cofh."
    };

    private static final boolean ASYNC_ITEM_ICONS_ENABLED = Boolean.parseBoolean(
        System.getProperty("akashic.asyncItemIcons", "false"));

    private static final Map<String, Boolean> REPORTED_ROOTS = new ConcurrentHashMap<>();
    private static final AtomicInteger SYNC_BLOCKS = new AtomicInteger();
    private static final AtomicInteger SYNC_ITEMS = new AtomicInteger();
    private static final AtomicInteger ASYNC_BLOCKS = new AtomicInteger();
    private static final AtomicInteger ASYNC_ITEMS = new AtomicInteger();

    private CompatHooks() {}

    public static void beginReload(String basePath) {
        REPORTED_ROOTS.clear();
        SYNC_BLOCKS.set(0);
        SYNC_ITEMS.set(0);
        ASYNC_BLOCKS.set(0);
        ASYNC_ITEMS.set(0);
        LOG.info("Hybrid async icon reload starting for {}.", basePath);
        if (!ASYNC_ITEM_ICONS_ENABLED) {
            LOG.info("Item icon registration forced to the client thread; set -Dakashic.asyncItemIcons=true only for controlled diagnostics.");
        }
    }

    public static boolean shouldRunBlockSynchronously(Block block) {
        boolean sync = shouldRunSynchronously(block);
        if (sync) {
            SYNC_BLOCKS.incrementAndGet();
        } else {
            ASYNC_BLOCKS.incrementAndGet();
        }
        return sync;
    }

    public static boolean shouldRunItemSynchronously(Item item) {
        if (!ASYNC_ITEM_ICONS_ENABLED) {
            SYNC_ITEMS.incrementAndGet();
            return true;
        }

        boolean sync = shouldRunSynchronously(item);
        if (sync) {
            SYNC_ITEMS.incrementAndGet();
        } else {
            ASYNC_ITEMS.incrementAndGet();
        }
        return sync;
    }

    static boolean isUnsafeClassName(String className) {
        return unsafeRoot(className) != null;
    }

    public static String telemetry() {
        return "asyncBlocks=" + ASYNC_BLOCKS.get()
            + ", syncBlocks=" + SYNC_BLOCKS.get()
            + ", asyncItems=" + ASYNC_ITEMS.get()
            + ", syncItems=" + SYNC_ITEMS.get();
    }

    private static boolean shouldRunSynchronously(Object value) {
        if (value == null) return false;
        String root = unsafeRoot(value.getClass().getName());
        if (root == null) return false;
        if (REPORTED_ROOTS.putIfAbsent(root, Boolean.TRUE) == null) {
            LOG.info("Main-thread icon fallback activated for compatibility root {}", root);
        }
        return true;
    }

    private static String unsafeRoot(String className) {
        if (className == null) return null;
        for (String root : UNSAFE_ROOTS) {
            if (className.startsWith(root)) return root;
        }
        return null;
    }
}