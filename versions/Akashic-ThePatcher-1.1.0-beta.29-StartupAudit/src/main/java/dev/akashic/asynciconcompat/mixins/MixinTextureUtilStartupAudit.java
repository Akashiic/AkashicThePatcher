package dev.akashic.asynciconcompat.mixins;

import dev.akashic.asynciconcompat.startup.StartupAtlasAudit;
import net.minecraft.client.renderer.texture.TextureUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = TextureUtil.class, remap = false)
public abstract class MixinTextureUtilStartupAudit {
    @Inject(method = "func_147946_a", at = @At("HEAD"), remap = false)
    private static void akashic$startupAuditAllocation(
        int textureId,
        int mipmapLevels,
        int width,
        int height,
        float anisotropic,
        CallbackInfo ci) {

        StartupAtlasAudit.allocation(textureId, mipmapLevels, width, height, anisotropic);
    }

    @Inject(method = "func_147955_a", at = @At("HEAD"), remap = false)
    private static void akashic$startupAuditUpload(
        int[][] mipmaps,
        int width,
        int height,
        int originX,
        int originY,
        boolean blur,
        boolean clamp,
        CallbackInfo ci) {

        StartupAtlasAudit.upload(mipmaps, width, height, originX, originY, blur, clamp);
    }
}
