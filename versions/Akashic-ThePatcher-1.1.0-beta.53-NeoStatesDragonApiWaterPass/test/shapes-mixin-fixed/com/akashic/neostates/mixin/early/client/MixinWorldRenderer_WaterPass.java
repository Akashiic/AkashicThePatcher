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
 * <p>
 * DragonAPI rewrites the same call into its own hook, {@code RenderBlockAtPosEvent.getMaxRenderPass}, which asks the
 * block and posts an event. Both forms are wrapped and only the one present applies; neither is required, so a
 * renderer rewritten some other way still starts (the holder's water would then wait for another translucent block in
 * its section, as in vanilla).
 */
@Mixin(WorldRenderer.class)
public abstract class MixinWorldRenderer_WaterPass {

    @WrapOperation(
        method = "updateRenderer",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/block/Block;getRenderBlockPass()I"),
        require = 0)
    private int neostates$holdersNeedTheWaterPass(Block block, Operation<Integer> original) {
        return withWaterPass(block, original.call(block));
    }

    @WrapOperation(
        method = "updateRenderer",
        at = @At(
            value = "INVOKE",
            target = "LReika/DragonAPI/Instantiable/Event/Client/RenderBlockAtPosEvent;getMaxRenderPass(Lnet/minecraft/block/Block;III)I",
            remap = false),
        require = 0)
    private int neostates$holdersNeedTheWaterPassDragonApi(Block block, int x, int y, int z,
        Operation<Integer> original) {
        return withWaterPass(block, original.call(block, x, y, z));
    }

    private static int withWaterPass(Block block, int pass) {
        return pass < 1 && WaterHolders.canHold(block) ? 1 : pass;
    }
}
