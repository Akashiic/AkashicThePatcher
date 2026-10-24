package dev.akashic.asynciconcompat.mixins;

import com.gtnewhorizons.angelica.rendering.celeritas.BlockRenderLayer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.akashic.asynciconcompat.graphics.WorldRenderStateProbe;

@Pseudo
@Mixin(
    targets = "com.gtnewhorizons.angelica.rendering.celeritas.CeleritasWorldRenderer",
    remap = false)
public abstract class MixinCeleritasWorldStateProbe {

    @Inject(
        method = "drawChunkLayer(Lcom/gtnewhorizons/angelica/rendering/celeritas/BlockRenderLayer;DDD)V",
        at = @At("HEAD"),
        remap = false)
    private void akashic$probeWorldState(
        BlockRenderLayer layer,
        double cameraX,
        double cameraY,
        double cameraZ,
        CallbackInfo ci) {

        WorldRenderStateProbe.beforeTerrainPass(
            layer.toVanillaPass());
    }
}