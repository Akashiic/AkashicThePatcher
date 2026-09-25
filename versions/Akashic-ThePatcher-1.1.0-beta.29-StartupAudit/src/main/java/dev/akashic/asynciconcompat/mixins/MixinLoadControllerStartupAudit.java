package dev.akashic.asynciconcompat.mixins;

import cpw.mods.fml.common.LoadController;
import cpw.mods.fml.common.LoaderState;
import cpw.mods.fml.common.ModContainer;
import cpw.mods.fml.common.event.FMLEvent;
import dev.akashic.asynciconcompat.startup.StartupLifecycleAudit;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = LoadController.class, remap = false)
public abstract class MixinLoadControllerStartupAudit {
    @Inject(method = "sendEventToModContainer", at = @At("HEAD"), remap = false)
    private void akashic$beforeModEvent(FMLEvent event, ModContainer mod, CallbackInfo ci) {
        StartupLifecycleAudit.begin(
            event == null ? "null" : event.getClass().getSimpleName(),
            mod == null ? "unknown" : mod.getModId());
    }

    @Inject(method = "sendEventToModContainer", at = @At("RETURN"), remap = false)
    private void akashic$afterModEvent(FMLEvent event, ModContainer mod, CallbackInfo ci) {
        StartupLifecycleAudit.end(
            event == null ? "null" : event.getClass().getSimpleName(),
            mod == null ? "unknown" : mod.getModId());
    }

    @Inject(method = "transition", at = @At("RETURN"), remap = false)
    private void akashic$flushPhase(LoaderState desiredState, boolean forceState, CallbackInfo ci) {
        StartupLifecycleAudit.flush(
            "transition:" + (desiredState == null ? "null" : desiredState.name()));
    }
}
