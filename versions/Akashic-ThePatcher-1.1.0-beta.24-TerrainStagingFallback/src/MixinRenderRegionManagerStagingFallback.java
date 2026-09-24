package dev.akashic.asynciconcompat.mixins;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.embeddedt.embeddium.impl.gl.arena.staging.FallbackStagingBuffer;
import org.embeddedt.embeddium.impl.gl.arena.staging.MappedStagingBuffer;
import org.embeddedt.embeddium.impl.gl.arena.staging.StagingBuffer;
import org.embeddedt.embeddium.impl.gl.device.CommandList;
import org.embeddedt.embeddium.impl.gl.device.RenderDevice;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(
    targets = "org.embeddedt.embeddium.impl.render.chunk.region.RenderRegionManager",
    remap = false)
public abstract class MixinRenderRegionManagerStagingFallback {

    @Unique
    private static final Logger akashic$logger =
        LogManager.getLogger("AkashicAsyncCompat/TerrainStaging");

    @Inject(
        method = "createStagingBuffer(Lorg/embeddedt/embeddium/impl/gl/device/CommandList;)Lorg/embeddedt/embeddium/impl/gl/arena/staging/StagingBuffer;",
        at = @At("HEAD"),
        cancellable = true,
        remap = false)
    private static void akashic$forceTerrainFallbackStaging(
        CommandList commandList,
        CallbackInfoReturnable<StagingBuffer> cir) {

        final boolean mappedSupported =
            MappedStagingBuffer.isSupported(RenderDevice.INSTANCE);

        final boolean forceFallback =
            Boolean.parseBoolean(
                System.getProperty(
                    "akashic.forceTerrainFallbackStaging",
                    "true"));

        akashic$logger.info(
            "TerrainStaging mappedSupported={} forceFallback={} selected={}",
            mappedSupported,
            forceFallback,
            mappedSupported && forceFallback
                ? "FallbackStagingBuffer"
                : "AngelicaDefault");

        if (mappedSupported && forceFallback) {
            cir.setReturnValue(
                new FallbackStagingBuffer(commandList));
        }
    }
}