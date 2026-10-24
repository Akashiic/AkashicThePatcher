package dev.akashic.asynciconcompat.mixins;

import com.gtnewhorizons.angelica.rendering.celeritas.BlockRenderLayer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.akashic.asynciconcompat.graphics.TerrainSemanticBlendProbe;

@Pseudo
@Mixin(
    targets = "com.gtnewhorizons.angelica.rendering.celeritas.CeleritasWorldRenderer",
    remap = false)
public abstract class MixinCeleritasTerrainSemanticBlendProbe {

    @Inject(
        method = "drawChunkLayer(Lcom/gtnewhorizons/angelica/rendering/celeritas/BlockRenderLayer;DDD)V",
        at = @At("HEAD"),
        remap = false)
    private void akashic$probeTerrainBlend(
        BlockRenderLayer layer,
        double cameraX,
        double cameraY,
        double cameraZ,
        CallbackInfo ci) {

        TerrainSemanticBlendProbe.beforeLayer(layer);
    }
}
