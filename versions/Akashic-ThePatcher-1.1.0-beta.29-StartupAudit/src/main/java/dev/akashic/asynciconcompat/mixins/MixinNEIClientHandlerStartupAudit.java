package dev.akashic.asynciconcompat.mixins;

import dev.akashic.asynciconcompat.startup.StartupSpanAudit;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "codechicken.nei.ClientHandler", remap = false)
public abstract class MixinNEIClientHandlerStartupAudit {
    @Inject(method = "loadPluginsList", at = @At("HEAD"), remap = false, require = 0)
    private static void akashic$beforePluginDiscovery(CallbackInfo ci) {
        StartupSpanAudit.begin("NEI.ClientHandler.loadPluginsList");
    }

    @Inject(method = "loadPluginsList", at = @At("RETURN"), remap = false, require = 0)
    private static void akashic$afterPluginDiscovery(CallbackInfo ci) {
        StartupSpanAudit.end("NEI.ClientHandler.loadPluginsList");
    }
}
