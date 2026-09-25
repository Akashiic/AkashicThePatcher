package dev.akashic.asynciconcompat.mixins;

import dev.akashic.asynciconcompat.graphics.NuminaFovSafety;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraftforge.client.ForgeHooksClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Last-line finite guard after all Forge FOVUpdateEvent listeners.
 *
 * <p>Vanilla sanitizes its speed-derived FOV before ForgeHooksClient, but a
 * legacy event listener can reintroduce NaN/Infinity afterwards. This guard
 * preserves every finite positive listener result and only repairs invalid
 * output before EntityRenderer can latch it into fovModifierHand.
 */
@Mixin(value = ForgeHooksClient.class, remap = false)
public abstract class MixinForgeHooksClientFovFiniteGuard {
    private static final Logger AKASHIC_LOG =
        LogManager.getLogger("AkashicAsyncCompat/NuminaFovGuard");
    private static final AtomicInteger AKASHIC_WARNINGS = new AtomicInteger();

    @Inject(
        method = "getOffsetFOV(Lnet/minecraft/client/entity/EntityPlayerSP;F)F",
        at = @At("RETURN"),
        cancellable = true,
        require = 1,
        remap = false)
    private static void akashic$repairInvalidFov(
        EntityPlayerSP entity,
        float baseFov,
        CallbackInfoReturnable<Float> cir) {

        float result = cir.getReturnValueF();
        if (NuminaFovSafety.isFinitePositive(result)) {
            return;
        }

        float restored = NuminaFovSafety.safeBaseline(baseFov, 1.0F);

        cir.setReturnValue(Float.valueOf(restored));
        akashic$logGuard(
            result,
            baseFov,
            restored,
            entity == null ? "null" : entity.getClass().getName());
    }

    private static void akashic$logGuard(
        float invalidResult,
        float baseFov,
        float restoredFov,
        String entityClass) {

        int count = AKASHIC_WARNINGS.incrementAndGet();
        if (count <= 12) {
            AKASHIC_LOG.warn(
                "Invalid post-event FOV repaired: result={} baseFov={} "
                    + "restoredFov={} entityClass={} occurrence={}",
                Float.valueOf(invalidResult),
                Float.valueOf(baseFov),
                Float.valueOf(restoredFov),
                entityClass,
                Integer.valueOf(count));
        } else if (count == 13) {
            AKASHIC_LOG.warn(
                "Further invalid FOV repair messages suppressed for this session.");
        }
    }
}
