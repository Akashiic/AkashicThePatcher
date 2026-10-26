package com.akashic.thepatcher.module.mirai;

import com.akashic.thepatcher.util.BundleLog;
import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.interfaces.render.IDhApiRenderProxy;
import com.seibel.distanthorizons.forge17.RenderHelper;

/**
 * Called from the handler MiraiShaderBridgeTransformer adds to Mirai's MixinRenderGlobal
 * (merged into RenderGlobal.sortAndRender, right after EntityRenderer.enableLightmap).
 * Only referenced when Mirai is installed, so the DH classes always resolve.
 */
public final class MiraiShaderBridgeHooks {

    private static volatile boolean firstPassLogged;

    private MiraiShaderBridgeHooks() {}

    /**
     * Upstream's renderDeferredLodsDuringTranslucentSetup, limited to frames where DH
     * actually deferred its translucent LODs (Angelica's DH support sets that flag each
     * frame while a DH-capable shaderpack is active), so frames without shaders run
     * exactly as before.
     */
    public static void afterTranslucentLightmap(int renderPass) {
        if (renderPass != 1) {
            return;
        }
        final IDhApiRenderProxy proxy = DhApi.Delayed.renderProxy;
        if (proxy == null || !proxy.getDeferTransparentRendering()) {
            return;
        }
        if (!firstPassLogged) {
            firstPassLogged = true;
            BundleLog.info("Mirai x Angelica shader bridge: first deferred translucent LOD pass "
                + "(the shaderpack's DH water program draws the distant translucent terrain).");
        }
        RenderHelper.renderDeferredLods();
    }
}
