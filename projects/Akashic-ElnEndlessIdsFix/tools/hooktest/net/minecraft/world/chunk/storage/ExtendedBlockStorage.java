package net.minecraft.world.chunk.storage;

import net.minecraft.block.Block;

/** Test double: one block and one metadata for every position; raw arrays throw, as with EndlessIDs. */
public class ExtendedBlockStorage {
    public Block block;
    public int meta;

    public Block func_150819_a(int x, int y, int z) {
        return block;
    }

    public int func_76665_b(int x, int y, int z) {
        return meta;
    }

    public byte[] func_76658_g() {
        throw new UnsupportedOperationException("EndlessIDs: raw block array");
    }
}
