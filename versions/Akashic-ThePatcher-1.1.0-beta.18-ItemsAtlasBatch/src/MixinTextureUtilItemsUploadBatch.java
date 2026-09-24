package dev.akashic.asynciconcompat.mixins;

import net.minecraft.client.renderer.texture.TextureUtil;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.akashic.asynciconcompat.upload.ItemsAtlasUploadBatcher;

@Mixin(TextureUtil.class)
public abstract class MixinTextureUtilItemsUploadBatch {

    @Inject(
        method = "uploadTextureMipmap",
        at = @At("HEAD"),
        cancellable = true)
    private static void akashic$batchInitialItemsAtlasUpload(
        int[][] mipmaps,
        int width,
        int height,
        int originX,
        int originY,
        boolean blur,
        boolean clamp,
        CallbackInfo ci) {

        if (ItemsAtlasUploadBatcher.stage(
            mipmaps,
            width,
            height,
            originX,
            originY,
            blur,
            clamp)) {

            ci.cancel();
        }
    }
}