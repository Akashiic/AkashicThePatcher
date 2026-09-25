package dev.akashic.asynciconcompat.mixins;

import dev.akashic.asynciconcompat.startup.StartupSpanAudit;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.kuba6000.mobsinfo.loader.MobRecipeLoader", remap = false)
public abstract class MixinMobRecipeLoaderStartupAudit {
    @Inject(method = "generateMobRecipeMap", at = @At("HEAD"), remap = false, require = 0)
    private static void akashic$beforeGenerate(CallbackInfo ci) {
        StartupSpanAudit.begin("MobsInfo.generateMobRecipeMap");
    }

    @Inject(method = "generateMobRecipeMap", at = @At("RETURN"), remap = false, require = 0)
    private static void akashic$afterGenerate(CallbackInfo ci) {
        StartupSpanAudit.end("MobsInfo.generateMobRecipeMap");
    }

    @Inject(method = "processMobRecipeMap", at = @At("HEAD"), remap = false, require = 0)
    private static void akashic$beforeProcess(CallbackInfo ci) {
        StartupSpanAudit.begin("MobsInfo.processMobRecipeMap");
    }

    @Inject(method = "processMobRecipeMap", at = @At("RETURN"), remap = false, require = 0)
    private static void akashic$afterProcess(CallbackInfo ci) {
        StartupSpanAudit.end("MobsInfo.processMobRecipeMap");
    }
}
