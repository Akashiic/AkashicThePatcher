import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import dev.akashic.asynciconcompat.CompatHooks;
import ganymedes01.etfuturum.blocks.BlockMagma;
import ganymedes01.etfuturum.blocks.BlockRedSandstoneSlab;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import team.chisel.block.BlockCarvable;

/**
 * Drives the shipped CompatHooks (class path: the release JAR + stubs + log4j).
 *
 *   java -Xverify:all -cp ... CrossDepsGate bug|fixed
 *
 * 1. Decisions: which blocks go to the client thread.
 * 2. Race replay: the block loop of MixinTextureMapHybridAsync.akashic$registerBlockIconsHybrid (client-thread block:
 *    wait for every pending job, then run inline; other block: submit to the pool; at the end wait for all), with
 *    a vanilla sandstone job and the Et Futurum red sandstone slab reading sandstone's icon while registering.
 *    The sandstone job only sets its icon after the slab has read it or after 2 s, so the outcome is deterministic:
 *    a slab on a worker reads null (NullPointerException, "Parallel block icon registration failed" in the game);
 *    a slab on the client thread waits for sandstone and reads the icon.
 * expect=bug: beta.48 (or beta.49 with -Dakashic.iconCrossDeps.disable=true); expect=fixed: beta.49.
 */
public final class CrossDepsGate {
    static int failures;

    /** A modpack subclass of a listed block (matched through its superclass). */
    static final class CustomSlab extends BlockRedSandstoneSlab {
    }

    static void check(boolean ok, String what) {
        System.out.println("GATE " + (ok ? "ok  " : "FAIL") + " " + what);
        if (!ok) failures++;
    }

    public static void main(String[] args) throws Exception {
        final boolean fixed = "fixed".equals(args[0]);
        CompatHooks.beginReload("textures/blocks");

        check(CompatHooks.shouldRunBlockSynchronously(new BlockRedSandstoneSlab()) == fixed,
            "Et Futurum BlockRedSandstoneSlab on client thread = " + fixed);
        check(CompatHooks.shouldRunBlockSynchronously(new CustomSlab()) == fixed,
            "subclass of a listed block on client thread = " + fixed);
        check(!CompatHooks.shouldRunBlockSynchronously(new BlockMagma()), "unlisted Et Futurum block stays parallel");
        check(!CompatHooks.shouldRunBlockSynchronously(new Block()), "vanilla block stays parallel");
        check(CompatHooks.shouldRunBlockSynchronously(new BlockCarvable()), "Chisel root still on client thread");
        check(CompatHooks.shouldRunItemSynchronously(new Item()), "items still on client thread");
        check(!CompatHooks.shouldRunBlockSynchronously(null), "null block is not forced");

        Set<String> listed = listed();
        if (listed != null) {
            System.out.println("LIST size=" + listed.size() + " " + new TreeSet<>(listed));
            check(listed.size() == 28 && listed.contains("ganymedes01.etfuturum.blocks.BlockRedSandstoneSlab"),
                "cross-dependent list: 28 classes including BlockRedSandstoneSlab");
        } else {
            check(!fixed, "no cross-dependent list (beta.48)");
        }

        String race = replay();
        System.out.println("RACE " + race);
        check(fixed ? race.equals("ok") : race.startsWith("failed"),
            "race replay: " + (fixed ? "slab reads sandstone's icon" : "slab reads null (the defect)"));
        System.out.println("TELEMETRY " + CompatHooks.telemetry());

        if (failures != 0) {
            System.out.println("CROSS_DEPS_GATE FAIL failures=" + failures);
            System.exit(1);
        }
        System.out.println("CROSS_DEPS_GATE PASS expect=" + args[0]);
    }

    @SuppressWarnings("unchecked")
    static Set<String> listed() throws Exception {
        try {
            Method m = CompatHooks.class.getDeclaredMethod("crossDependentBlockClassNames");
            m.setAccessible(true);
            return (Set<String>) m.invoke(null);
        } catch (NoSuchMethodException absent) {
            return null;
        }
    }

    static final class Sandstone extends Block {
        volatile Object icon;
    }

    static String replay() throws Exception {
        final Sandstone sandstone = new Sandstone();
        final BlockRedSandstoneSlab slab = new BlockRedSandstoneSlab();
        final CountDownLatch slabRead = new CountDownLatch(1);
        final Runnable registerSandstone = () -> {
            try {
                slabRead.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            sandstone.icon = "minecraft:sandstone_normal";
        };
        final Runnable registerSlab = () -> {
            try {
                Object icon = sandstone.icon;
                if (icon == null) throw new NullPointerException("sandstone icon not registered yet");
            } finally {
                slabRead.countDown();
            }
        };

        ExecutorService pool = Executors.newFixedThreadPool(4);
        List<Future<?>> pending = new ArrayList<>();
        try {
            Block[] order = {sandstone, slab};
            Runnable[] jobs = {registerSandstone, registerSlab};
            for (int i = 0; i < order.length; i++) {
                if (CompatHooks.shouldRunBlockSynchronously(order[i])) {
                    awaitAll(pending);
                    jobs[i].run();
                } else {
                    pending.add(pool.submit(jobs[i]));
                }
            }
            awaitAll(pending);
            return "ok";
        } catch (ExecutionException failure) {
            return "failed: " + failure.getCause();
        } catch (RuntimeException failure) {
            return "failed: " + failure;
        } finally {
            pool.shutdownNow();
        }
    }

    static void awaitAll(List<Future<?>> pending) throws Exception {
        try {
            for (Future<?> f : pending) f.get();
        } finally {
            pending.clear();
        }
    }
}
