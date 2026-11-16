package com.akashic.neostates.api;

import net.minecraft.block.Block;

/** Test stand-in for Neo States' public API: the test block says whether it may hold water. Never shipped. */
public final class NeoStates {
    public static int calls;

    private NeoStates() {}

    public static boolean canHoldWater(Block block) {
        calls++;
        return block.holder;
    }
}
