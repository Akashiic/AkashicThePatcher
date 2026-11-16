package com.akashic.neostates.mixin.early.client;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.WorldRenderer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.akashic.neostates.water.WaterHolders;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

/**
 * Vanilla's chunk renderer only runs the translucent pass over a section when some block of the first pass said it
 * draws there. A block that may hold water says so, or its water would be skipped in a section with no other
 * translucent block. Angelica does not use this method.
 */
@Mixin(WorldRenderer.class)
public abstract class MixinWorldRenderer_WaterPass {

    @WrapOperation(
        method = "updateRenderer",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/block/Block;getRenderBlockPass()I"))
    private int neostates$holdersNeedTheWaterPass(Block block, Operation<Integer> original) {
        int pass = original.call(block);
        return pass < 1 && WaterHolders.canHold(block) ? 1 : pass;
    }
}
