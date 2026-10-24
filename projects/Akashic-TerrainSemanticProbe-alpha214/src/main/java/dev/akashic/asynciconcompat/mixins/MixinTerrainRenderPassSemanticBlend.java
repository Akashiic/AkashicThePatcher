package dev.akashic.asynciconcompat.mixins;

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.backend.BackendManager;
import com.gtnewhorizons.angelica.glsm.states.BlendState;
import com.gtnewhorizons.angelica.glsm.states.Color4;

@Pseudo
@Mixin(
    targets = "org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass",
    remap = false)
public abstract class MixinTerrainRenderPassSemanticBlend {

    @Unique
    private static final Logger akashic$logger =
        LogManager.getLogger("AkashicAsyncCompat/TerrainSemantic");
    @Unique
    private static final AtomicInteger akashic$samples = new AtomicInteger();

    @Unique
    private boolean akashic$restoreBlend;

    @Inject(method = "startDrawing()V", at = @At("TAIL"), remap = false)
    private void akashic$afterPipelineSetup(CallbackInfo ci) {
        final TerrainRenderPass pass = (TerrainRenderPass) (Object) this;
        final boolean sorted = pass.isSorted();
        final boolean cacheBlend = GLStateManager.getBlendMode().isEnabled();
        final boolean effectiveBlend = GLStateManager.isEffectiveBlendEnabled();

        boolean actualBlend = cacheBlend;
        try {
            actualBlend = BackendManager.RENDER_BACKEND.getBoolean(3042);
        } catch (Throwable ignored) {
            // Diagnostic only; cache/effective state remains authoritative fallback.
        }

        final BlendState blend = GLStateManager.getBlendState();
        final Color4 color = GLStateManager.getColor();
        final int sample = akashic$samples.incrementAndGet();
        if (sample <= 32) {
            akashic$logger.info(
                "TerrainSemantic sample={} pass={} sorted={} blend(cache/effective/gl)={}/{}/{} "
                    + "blendFunc={}/{}/{}/{} color={}/{}/{}/{}",
                sample,
                pass.name(),
                sorted,
                cacheBlend,
                effectiveBlend,
                actualBlend,
                blend.getSrcRgb(),
                blend.getDstRgb(),
                blend.getSrcAlpha(),
                blend.getDstAlpha(),
                color.getRed(),
                color.getGreen(),
                color.getBlue(),
                color.getAlpha());
        }

        final boolean forceOpaqueBlendOff = Boolean.parseBoolean(
            System.getProperty("akashic.forceOpaqueTerrainBlendOff", "true"));
        if (!sorted && forceOpaqueBlendOff
            && (cacheBlend || effectiveBlend || actualBlend)) {

            akashic$restoreBlend = cacheBlend;
            GLStateManager.disableBlend();

            if (sample <= 32) {
                akashic$logger.warn(
                    "TerrainSemantic forced BLEND OFF for non-sorted pass={} "
                        + "restoreCacheBlend={}",
                    pass.name(),
                    akashic$restoreBlend);
            }
        } else {
            akashic$restoreBlend = false;
        }
    }

    @Inject(method = "endDrawing()V", at = @At("HEAD"), remap = false)
    private void akashic$beforePipelineClear(CallbackInfo ci) {
        if (akashic$restoreBlend) {
            GLStateManager.enableBlend();
            akashic$restoreBlend = false;
        }
    }
}
