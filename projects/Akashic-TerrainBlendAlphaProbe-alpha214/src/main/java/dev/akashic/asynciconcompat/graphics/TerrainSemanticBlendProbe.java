package dev.akashic.asynciconcompat.graphics;

import net.minecraft.client.Minecraft;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.backend.BackendManager;
import com.gtnewhorizons.angelica.glsm.backend.RenderBackend;
import com.gtnewhorizons.angelica.glsm.states.BlendState;
import com.gtnewhorizons.angelica.rendering.celeritas.BlockRenderLayer;

public final class TerrainSemanticBlendProbe {

    private static final Logger LOG =
        LogManager.getLogger("AkashicAsyncCompat/TerrainBlend");

    private static final int GL_BLEND = 3042;
    private static final int GL_BLEND_DST_RGB = 32968;
    private static final int GL_BLEND_SRC_RGB = 32969;
    private static final int GL_BLEND_DST_ALPHA = 32970;
    private static final int GL_BLEND_SRC_ALPHA = 32971;
    private static final int MAX_LOG_SAMPLES = 12;

    private static final int[] SAMPLES = new int[4];
    private static Object lastWorld;

    private TerrainSemanticBlendProbe() {}

    public static void beforeLayer(BlockRenderLayer layer) {
        if (layer == null) {
            return;
        }

        final Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.theWorld == null) {
            return;
        }

        if (mc.theWorld != lastWorld) {
            lastWorld = mc.theWorld;
            for (int i = 0; i < SAMPLES.length; i++) {
                SAMPLES[i] = 0;
            }
            TerrainVertexAlphaProbe.reset();
            LOG.info(
                "TerrainBlend new-world guard={} backend={} cache={}",
                guardEnabled(),
                GLStateManager.getRenderBackendName(),
                GLStateManager.isCachingEnabled());
        }

        final int index = layer.ordinal();
        final RenderBackend backend = BackendManager.RENDER_BACKEND;
        final boolean cacheBefore =
            GLStateManager.getBlendMode().isEnabled();
        final boolean glBefore =
            backend.getBoolean(GL_BLEND);
        final BlendState blend =
            GLStateManager.getBlendState();

        final int glSrcRgb = backend.getInteger(GL_BLEND_SRC_RGB);
        final int glDstRgb = backend.getInteger(GL_BLEND_DST_RGB);
        final int glSrcAlpha = backend.getInteger(GL_BLEND_SRC_ALPHA);
        final int glDstAlpha = backend.getInteger(GL_BLEND_DST_ALPHA);

        final boolean opaqueLayer =
            layer != BlockRenderLayer.TRANSLUCENT;
        final boolean guard = guardEnabled();
        boolean forced = false;

        if (opaqueLayer && guard && (cacheBefore || glBefore)) {
            GLStateManager.glDisable(GL_BLEND);
            forced = true;
        }

        final boolean cacheAfter =
            GLStateManager.getBlendMode().isEnabled();
        final boolean glAfter =
            backend.getBoolean(GL_BLEND);

        if (index >= 0 && index < SAMPLES.length
            && SAMPLES[index] < MAX_LOG_SAMPLES) {

            final int sample = ++SAMPLES[index];
            LOG.info(
                "TerrainBlend layer={} pass={} sample={} guard={} forced={} "
                    + "blendBefore={}/{} blendAfter={}/{} "
                    + "cacheFunc={}/{}/{}/{} glFunc={}/{}/{}/{}",
                layer,
                layer.toVanillaPass(),
                sample,
                guard,
                forced,
                cacheBefore,
                glBefore,
                cacheAfter,
                glAfter,

                blend.getSrcRgb(),
                blend.getDstRgb(),
                blend.getSrcAlpha(),
                blend.getDstAlpha(),
                glSrcRgb,
                glDstRgb,
                glSrcAlpha,
                glDstAlpha);
        }
    }

    private static boolean guardEnabled() {
        return Boolean.parseBoolean(
            System.getProperty(
                "akashic.forceOpaqueTerrainBlendOff",
                "true"));
    }
}
