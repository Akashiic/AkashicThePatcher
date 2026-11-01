package dev.akashic.asynciconcompat;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
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

    /*
     * beta.49: blocks whose registerBlockIcons reads the icon of ANOTHER block (getIcon / getBlockTextureFromSide /
     * blockIcon of a different block). Run on a worker, they can read it before the other block's job has set it:
     * NullPointerException in BlockSandStone.getIcon from Et Futurum's BlockRedSandstoneSlab, "Parallel block icon
     * registration failed", texture reload aborted. Running them on the client thread fixes it because the hybrid
     * loop first waits for every job submitted before them (MixinTextureMapHybridAsync.akashic$awaitIfNeeded), and
     * the blocks they read come earlier in the registry, as they must in vanilla too.
     * List: bytecode scan of the TEOF 5.5 mods (registerBlockIcons and the same-class methods it reaches), see
     * CHECKPOINT-BETA49. A class matches itself or any subclass. -Dakashic.iconCrossDeps.disable=true: beta.48.
     */
    private static final Set<String> CROSS_DEPENDENT_BLOCKS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
        "Reika.ChromatiCraft.Block.BlockRFNode",
        "Reika.ChromatiCraft.Block.BlockSpawnerShutdown",
        "Reika.ChromatiCraft.Block.Dimension.BlockBedrockCrack",
        "Reika.ChromatiCraft.Block.Dimension.BlockLightedLeaf",
        "Reika.ChromatiCraft.Block.Dimension.Structure.Water.BlockEverFluid",
        "Reika.ChromatiCraft.Block.Worldgen.BlockTieredOre",
        "Reika.ReactorCraft.Blocks.BlockDuct",
        "Reika.ReactorCraft.Blocks.BlockReactorOre",
        "Reika.RotaryCraft.Blocks.BlockPiping",
        "com.kentington.thaumichorizons.common.blocks.BlockGatewayPortal",
        "com.rwtema.extrautils.block.BlockCobblestoneCompressed",
        "ganymedes01.etfuturum.blocks.BlockEndGateway",
        "ganymedes01.etfuturum.blocks.BlockNylium",
        "ganymedes01.etfuturum.blocks.BlockPurpurSlab",
        "ganymedes01.etfuturum.blocks.BlockRedSandstoneSlab",
        "ganymedes01.etfuturum.blocks.BlockSmoothQuartzSlab",
        "ganymedes01.etfuturum.blocks.BlockSmoothSandstoneSlab",
        "ganymedes01.etfuturum.blocks.BlockStoneSlab1",
        "ganymedes01.etfuturum.blocks.BlockStoneWall",
        "ganymedes01.etfuturum.blocks.BlockWoodBarkNew",
        "ganymedes01.etfuturum.blocks.BlockWoodBarkOld",
        "magicbees.block.BlockEffectJar",
        "magicbees.block.BlockVisAuraProvider",
        "twilightforest.block.BlockTFAuroraSlab",
        "twilightforest.block.BlockTFGiantBlock",
        "twilightforest.block.BlockTFPlant",
        "twilightforest.block.BlockTFTrollLjos",
        "vazkii.botania.common.block.BlockFelPumpkin")));

    private static final boolean CROSS_DEPS_ENABLED = !Boolean.parseBoolean(
        System.getProperty("akashic.iconCrossDeps.disable", "false"));

    private static final boolean ASYNC_ITEM_ICONS_ENABLED = Boolean.parseBoolean(
        System.getProperty("akashic.asyncItemIcons", "false"));

    private static final Map<String, Boolean> REPORTED_ROOTS = new ConcurrentHashMap<>();
    private static final Map<Class<?>, String> CROSS_DEPENDENT_MATCH = new ConcurrentHashMap<>();
    private static final AtomicInteger SYNC_BLOCKS = new AtomicInteger();
    private static final AtomicInteger SYNC_ITEMS = new AtomicInteger();
    private static final AtomicInteger ASYNC_BLOCKS = new AtomicInteger();
    private static final AtomicInteger ASYNC_ITEMS = new AtomicInteger();
    private static final AtomicInteger CROSS_DEPENDENT_SYNC_BLOCKS = new AtomicInteger();

    private CompatHooks() {}

    public static void beginReload(String basePath) {
        REPORTED_ROOTS.clear();
        SYNC_BLOCKS.set(0);
        SYNC_ITEMS.set(0);
        ASYNC_BLOCKS.set(0);
        ASYNC_ITEMS.set(0);
        CROSS_DEPENDENT_SYNC_BLOCKS.set(0);
        LOG.info("Hybrid async icon reload starting for {}.", basePath);
        if (!ASYNC_ITEM_ICONS_ENABLED) {
            LOG.info("Item icon registration forced to the client thread; set -Dakashic.asyncItemIcons=true only for controlled diagnostics.");
        }
        if (!CROSS_DEPS_ENABLED) {
            LOG.info("Cross-dependent block icon fallback disabled by -Dakashic.iconCrossDeps.disable=true.");
        }
    }

    public static boolean shouldRunBlockSynchronously(Block block) {
        boolean sync = shouldRunSynchronously(block) || isCrossDependentBlock(block);
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

    static Set<String> crossDependentBlockClassNames() {
        return CROSS_DEPENDENT_BLOCKS;
    }

    public static String telemetry() {
        return "asyncBlocks=" + ASYNC_BLOCKS.get()
            + ", syncBlocks=" + SYNC_BLOCKS.get()
            + ", crossDependentSyncBlocks=" + CROSS_DEPENDENT_SYNC_BLOCKS.get()
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

    private static boolean isCrossDependentBlock(Block block) {
        if (!CROSS_DEPS_ENABLED || block == null) return false;
        String match = CROSS_DEPENDENT_MATCH.computeIfAbsent(block.getClass(), CompatHooks::crossDependentAncestor);
        if (match.isEmpty()) return false;
        CROSS_DEPENDENT_SYNC_BLOCKS.incrementAndGet();
        if (REPORTED_ROOTS.putIfAbsent(match, Boolean.TRUE) == null) {
            LOG.info("Main-thread icon fallback activated for cross-dependent block {} (reads another block's icon while registering)", match);
        }
        return true;
    }

    /** Name of the listed class that is {@code type} or one of its superclasses, or "" when there is none. */
    private static String crossDependentAncestor(Class<?> type) {
        for (Class<?> k = type; k != null && k != Block.class && k != Object.class; k = k.getSuperclass()) {
            if (CROSS_DEPENDENT_BLOCKS.contains(k.getName())) return k.getName();
        }
        return "";
    }

    private static String unsafeRoot(String className) {
        if (className == null) return null;
        for (String root : UNSAFE_ROOTS) {
            if (className.startsWith(root)) return root;
        }
        return null;
    }
}
