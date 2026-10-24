package dev.akashic.asynciconcompat.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.akashic.asynciconcompat.profile.TextureUploadProfiler;

@Pseudo
@Mixin(
    targets = "com.gtnewhorizons.angelica.glsm.GLStateManager",
    remap = false)
public abstract class MixinAngelicaMipmapProfiler {

    @Inject(
        method = "maybeGenerateMipmap(II)V",
        at = @At("HEAD"),
        remap = false)
    private static void akashic$countMipmapCheck(
        int target,
        int level,
        CallbackInfo ci) {

        TextureUploadProfiler.recordMipmapCheck();
    }

    @Inject(
        method = "maybeGenerateMipmap(II)V",
        at = @At(
            value = "INVOKE",
            target = "Lcom/gtnewhorizons/angelica/glsm/backend/RenderBackend;generateMipmap(I)V",
            shift = Shift.BEFORE,
            remap = false),
        remap = false)
    private static void akashic$beforeGenerateMipmap(
        int target,
        int level,
        CallbackInfo ci) {

        TextureUploadProfiler.mipmapGenerateEnter();
    }

    @Inject(
        method = "maybeGenerateMipmap(II)V",
        at = @At(
            value = "INVOKE",
            target = "Lcom/gtnewhorizons/angelica/glsm/backend/RenderBackend;generateMipmap(I)V",
            shift = Shift.AFTER,
            remap = false),
        remap = false)
    private static void akashic$afterGenerateMipmap(
        int target,
        int level,
        CallbackInfo ci) {

        TextureUploadProfiler.mipmapGenerateExit();
    }
}