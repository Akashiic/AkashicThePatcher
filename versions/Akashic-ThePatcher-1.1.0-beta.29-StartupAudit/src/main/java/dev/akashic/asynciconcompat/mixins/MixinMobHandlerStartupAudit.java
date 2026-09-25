package dev.akashic.asynciconcompat.mixins;

import dev.akashic.asynciconcompat.startup.StartupSpanAudit;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.kuba6000.mobsinfo.nei.MobHandler", remap = false)
public abstract class MixinMobHandlerStartupAudit {
    @Inject(method = "sortCachedRecipes", at = @At("HEAD"), remap = false, require = 0)
    private static void akashic$beforeSort(CallbackInfo ci) {
        StartupSpanAudit.begin("MobsInfo.MobHandler.sortCachedRecipes");
    }

    @Inject(method = "sortCachedRecipes", at = @At("RETURN"), remap = false, require = 0)
    private static void akashic$afterSort(CallbackInfo ci) {
        StartupSpanAudit.end("MobsInfo.MobHandler.sortCachedRecipes");
    }
}
