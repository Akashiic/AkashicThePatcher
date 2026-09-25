package dev.akashic.asynciconcompat.mixins;

import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.resources.IResourceManager;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import dev.akashic.asynciconcompat.upload.BlockAtlasUploadBatcher;
import dev.akashic.asynciconcompat.upload.ItemsAtlasUploadBatcher;

@Mixin(TextureMap.class)
public abstract class MixinTextureMapItemsUploadBatch {

    @Shadow(remap = false)
    @Final
    private String field_94254_c;

    @WrapOperation(
        method = "loadTextureAtlas",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/texture/TextureUtil;allocateTextureImpl(IIIIF)V"))
    private void akashic$armStartupAtlasBatch(
        int textureId,
        int mipmapLevels,
        int width,
        int height,
        float anisotropicFiltering,
        Operation<Void> original) {

        original.call(
            textureId,
            mipmapLevels,
            width,
            height,
            anisotropicFiltering);

        ItemsAtlasUploadBatcher.begin(
            field_94254_c,
            textureId,
            mipmapLevels,
            width,
            height);

        BlockAtlasUploadBatcher.begin(
            field_94254_c,
            textureId,
            mipmapLevels,
            width,
            height);
    }

    @Inject(
        method = "loadTextureAtlas",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraftforge/client/ForgeHooksClient;onTextureStitchedPost(Lnet/minecraft/client/renderer/texture/TextureMap;)V",
            shift = Shift.BEFORE,
            remap = false))
    private void akashic$flushStartupAtlasBatch(
        IResourceManager resourceManager,
        CallbackInfo ci) {

        ItemsAtlasUploadBatcher.flushIfActive(
            field_94254_c);

        BlockAtlasUploadBatcher.flushIfActive(
            field_94254_c);
    }

    @Inject(
        method = "loadTextureAtlas",
        at = @At("RETURN"))
    private void akashic$cleanupStartupAtlasBatch(
        IResourceManager resourceManager,
        CallbackInfo ci) {

        ItemsAtlasUploadBatcher.abortIfActive(
            field_94254_c);

        BlockAtlasUploadBatcher.abortIfActive(
            field_94254_c);
    }
}
