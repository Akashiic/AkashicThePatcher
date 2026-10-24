package dev.akashic.asynciconcompat;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CompatClassifierTest {

    @Test
    public void knownUnsafeRootsAreSynchronous() {
        assertTrue(CompatHooks.isUnsafeClassName("team.chisel.block.BlockCarvable"));
        assertTrue(CompatHooks.isUnsafeClassName("com.cricketcraft.chisel.SomeCompat"));
        assertTrue(CompatHooks.isUnsafeClassName("mekanism.common.block.BlockMachine"));
        assertTrue(CompatHooks.isUnsafeClassName(
            "powercrystals.minefactoryreloaded.block.transport.BlockRedNetCable"));
    }

    @Test
    public void unrelatedRootsRemainParallel() {
        assertFalse(CompatHooks.isUnsafeClassName("net.minecraft.block.BlockStone"));
        assertFalse(CompatHooks.isUnsafeClassName("thaumcraft.common.blocks.BlockCustomOre"));
        assertFalse(CompatHooks.isUnsafeClassName("vazkii.botania.common.block.BlockSpecialFlower"));
        assertFalse(CompatHooks.isUnsafeClassName("appeng.block.AEBaseBlock"));
    }
}