package com.akashic.neostates.water;

import net.minecraft.block.Block;

/** Test stub of Neo States' WaterHolders (the mixin shape only references it). Never shipped. */
public final class WaterHolders {
    private WaterHolders() {}

    public static boolean canHold(Block block) {
        return block.holder;
    }
}
