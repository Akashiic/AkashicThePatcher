package com.akashic.elnendlessids.hook;

import net.minecraft.block.Block;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

/**
 * Called from Electrical Age's bytecode after {@code ElnEndlessIdsTransformer} rewrites it.
 *
 * <p>Electrical Age keys its ore map by {@code blockId | meta << 12} in a 65536-entry array, i.e. it assumes vanilla's
 * 12-bit block ids (0..4095). EndlessIDs allows larger ids and forbids reading the raw chunk arrays.
 */
public final class ElnEndlessIdsHooks {
    /** Size of {@code OreColorMapping.map} in Electrical Age (1024 * 64). */
    private static final int ORE_MAP_SIZE = 1024 * 64;
    /** Stone, meta 0: drawn as an opaque, non-ore block. */
    private static final int KEY_OPAQUE = 1;
    /** Glass, meta 0: drawn as a translucent, non-ore block. */
    private static final int KEY_TRANSLUCENT = 20;

    private ElnEndlessIdsHooks() {
    }

    /**
     * Replaces the block-key computation in {@code PortableOreScannerItem$RenderStorage.generate} (Portable Ore
     * Scanner and Auto Miner screen), which read {@code getBlockLSBArray()} / {@code getBlockMSBArray()} directly.
     * Reads the block through {@code getBlockByExtId}, which EndlessIDs supports.
     *
     * <p>Same key format as Electrical Age: {@code id | (meta & 15) << 12}. A block whose id does not fit in 12
     * bits (only possible with EndlessIDs) cannot have an entry in Electrical Age's ore map, so it is drawn as an
     * opaque (stone) or translucent (glass) non-ore block instead of being mistaken for another block.
     */
    public static int scannerBlockKey(ExtendedBlockStorage storage, int x, int y, int z) {
        Block block = storage.func_150819_a(x, y, z);
        int id = block == null ? 0 : Block.func_149682_b(block);
        if (id <= 0) {
            return 0;
        }
        if (id > 0xFFF) {
            return block.func_149662_c() ? KEY_OPAQUE : KEY_TRANSLUCENT;
        }
        return id | (storage.func_76665_b(x, y, z) & 0xF) << 12;
    }

    /**
     * Replaces {@code Block.getIdFromBlock(block)} in {@code AutoMinerSlowProcess.checkIsOre} (server side), which
     * then computes {@code id + (meta << 12)} as an index into the 65536-entry ore map. An id above 4095 (EndlessIDs)
     * would either read another block's entry or run past the end of the array; it becomes a large negative number
     * instead, so the index stays negative and {@link #oreMapIndex} sends it to entry 0.
     */
    public static int oreMapBlockId(Block block) {
        int id = Block.func_149682_b(block);
        return id >= 0 && id <= 0xFFF ? id : Integer.MIN_VALUE / 2;
    }

    /**
     * Guards the ore-map lookup in {@code AutoMinerSlowProcess.checkIsOre}: without it, an EndlessIDs block id above
     * 4095 (or a metadata above 15) makes the index pass the end of the array, an
     * {@code ArrayIndexOutOfBoundsException} in the Auto Miner's server tick. Out-of-range keys map to entry 0 (air,
     * never an ore).
     */
    public static int oreMapIndex(int key) {
        return key >= 0 && key < ORE_MAP_SIZE ? key : 0;
    }
}
