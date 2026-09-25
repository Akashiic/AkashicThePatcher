package dev.akashic.asynciconcompat.mixins;

import dev.akashic.asynciconcompat.graphics.NuminaFovSafety;
import java.util.concurrent.atomic.AtomicInteger;
import net.machinemuse.numina.basemod.NuminaConfig;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraftforge.client.event.FOVUpdateEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.machinemuse.numina.event.FOVUpdateEventHandler", remap = false)
public abstract class MixinNuminaFovFiniteGuard {
    private static final Logger AKASHIC_LOG =
        LogManager.getLogger("AkashicAsyncCompat/NuminaFovGuard");
    private static final AtomicInteger AKASHIC_WARNINGS = new AtomicInteger();

    private float akashic$preNuminaFov = 1.0F;

    @Inject(
        method = "onFOVUpdate(Lnet/minecraftforge/client/event/FOVUpdateEvent;)V",
        at = @At("HEAD"),
        cancellable = true,
        require = 1,
        remap = false)
    private void akashic$guardInvalidInputs(
        FOVUpdateEvent event,
        CallbackInfo ci) {

        if (event == null || event.entity == null || !NuminaConfig.useFOVFix()) {
            return;
        }

        this.akashic$preNuminaFov =
            NuminaFovSafety.safeBaseline(event.newfov, event.fov);

        IAttributeInstance attribute =
            event.entity.getEntityAttribute(SharedMonsterAttributes.movementSpeed);
        double movementSpeed =
            attribute == null ? Double.NaN : attribute.getAttributeValue();
        float walkSpeed = event.entity.capabilities.getWalkSpeed();

        if (NuminaFovSafety.shouldBypass(movementSpeed, walkSpeed)) {
            event.newfov = this.akashic$preNuminaFov;
            akashic$logGuard(
                "invalid-input",
                movementSpeed,
                walkSpeed,
                event.fov,
                event.newfov);
            ci.cancel();
        }
    }

    @Inject(
        method = "onFOVUpdate(Lnet/minecraftforge/client/event/FOVUpdateEvent;)V",
        at = @At("RETURN"),
        require = 1,
        remap = false)
    private void akashic$sanitizeResult(
        FOVUpdateEvent event,
        CallbackInfo ci) {

        if (event == null || !NuminaConfig.useFOVFix()) {
            return;
        }

        if (!NuminaFovSafety.isFinitePositive(event.newfov)) {
            float bad = event.newfov;

            event.newfov = NuminaFovSafety.safeBaseline(
                this.akashic$preNuminaFov,
                event.fov);
            akashic$logGuard(
                "invalid-result:" + bad,
                Double.NaN,
                event.entity == null
                    ? Float.NaN
                    : event.entity.capabilities.getWalkSpeed(),
                event.fov,
                event.newfov);
        }
    }

    private static void akashic$logGuard(
        String reason,
        double movementSpeed,
        float walkSpeed,
        float baseFov,
        float restoredFov) {

        int count = AKASHIC_WARNINGS.incrementAndGet();
        if (count <= 12) {
            AKASHIC_LOG.warn(
                "Numina FOV calculation bypassed: reason={} movementSpeed={} "
                    + "walkSpeed={} baseFov={} restoredFov={} occurrence={}",

                reason,
                Double.valueOf(movementSpeed),
                Float.valueOf(walkSpeed),
                Float.valueOf(baseFov),
                Float.valueOf(restoredFov),
                Integer.valueOf(count));
        } else if (count == 13) {
            AKASHIC_LOG.warn(
                "Further Numina FOV guard messages suppressed for this session.");
        }
    }
}
