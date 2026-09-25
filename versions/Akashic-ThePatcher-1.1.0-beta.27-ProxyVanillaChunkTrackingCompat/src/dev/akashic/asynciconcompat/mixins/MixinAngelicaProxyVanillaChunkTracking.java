package dev.akashic.asynciconcompat.mixins;

import dev.akashic.asynciconcompat.graphics.ProxyVanillaChunkTrackingPolicy;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Makes Angelica's aggressive/vanilla chunk tracking network-aware.
 *
 * <p>Exactly four GETSTATIC reads exist in Angelica 2.2.18:
 * two in forEachReady and two in forEachEvent. require=4 is intentional so an
 * upstream target drift fails loudly instead of silently applying a partial policy.
 */
@Pseudo
@Mixin(targets = "com.gtnewhorizons.angelica.rendering.celeritas.AngelicaChunkTracker", remap = false)
public abstract class MixinAngelicaProxyVanillaChunkTracking {
    @Redirect(
        method = {
            "forEachReady(Lorg/embeddedt/embeddium/impl/render/chunk/RenderSectionManager;)V",
            "forEachEvent(Lorg/embeddedt/embeddium/impl/render/chunk/RenderSectionManager;)V"
        },
        at = @At(
            value = "FIELD",
            target = "Lcom/gtnewhorizons/angelica/config/AngelicaConfig;useVanillaChunkTracking:Z",
            opcode = Opcodes.GETSTATIC),
        require = 4,
        remap = false)
    private boolean akashic$effectiveVanillaChunkTracking() {
        return ProxyVanillaChunkTrackingPolicy.useFastTracking();
    }
}
