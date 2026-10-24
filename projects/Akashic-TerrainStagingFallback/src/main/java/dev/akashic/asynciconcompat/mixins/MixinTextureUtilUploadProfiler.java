package dev.akashic.asynciconcompat.mixins;

import java.nio.IntBuffer;

import net.minecraft.client.renderer.texture.TextureUtil;

import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import dev.akashic.asynciconcompat.profile.TextureUploadProfiler;

@Mixin(TextureUtil.class)
public abstract class MixinTextureUtilUploadProfiler {

    @Inject(method = "uploadTextureSub", at = @At("HEAD"))
    private static void akashic$uploadSubEnter(
        int level,
        int[] pixels,
        int width,
        int height,
        int originX,
        int originY,
        boolean blur,
        boolean clamp,
        boolean mipmap,
        CallbackInfo ci) {

        TextureUploadProfiler.uploadSubEnter(level);
    }

    @Inject(method = "uploadTextureSub", at = @At("RETURN"))
    private static void akashic$uploadSubExit(
        int level,
        int[] pixels,
        int width,
        int height,
        int originX,
        int originY,
        boolean blur,
        boolean clamp,
        boolean mipmap,
        CallbackInfo ci) {

        TextureUploadProfiler.uploadSubExit();
    }

    @WrapOperation(
        method = "uploadTextureSub",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/texture/TextureUtil;func_147954_b(ZZ)V"))
    private static void akashic$profileFilterState(
        boolean blur,
        boolean mipmap,
        Operation<Void> original) {

        long started = TextureUploadProfiler.start();
        try {
            original.call(blur, mipmap);
        } finally {
            TextureUploadProfiler.recordState(started);
        }
    }

    @WrapOperation(
        method = "uploadTextureSub",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/texture/TextureUtil;setTextureClamped(Z)V"))
    private static void akashic$profileClampState(
        boolean clamp,
        Operation<Void> original) {

        long started = TextureUploadProfiler.start();
        try {
            original.call(clamp);
        } finally {
            TextureUploadProfiler.recordState(started);
        }
    }

    @WrapOperation(
        method = "uploadTextureSub",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/texture/TextureUtil;copyToBufferPos([III)V"))
    private static void akashic$profileCopy(
        int[] pixels,
        int offset,
        int length,
        Operation<Void> original) {

        long started = TextureUploadProfiler.start();
        try {
            original.call(pixels, offset, length);
        } finally {
            TextureUploadProfiler.recordCopy(started, length);
        }
    }

    @WrapOperation(
        method = "uploadTextureSub",
        at = @At(
            value = "INVOKE",
            target = "Lorg/lwjgl/opengl/GL11;glTexSubImage2D(IIIIIIIILjava/nio/IntBuffer;)V",
            remap = false))
    private static void akashic$profileGlUpload(
        int target,
        int level,
        int xOffset,
        int yOffset,
        int width,
        int height,
        int format,
        int type,
        IntBuffer pixels,
        Operation<Void> original) {

        long started = TextureUploadProfiler.start();
        try {
            original.call(
                target,
                level,
                xOffset,
                yOffset,
                width,
                height,
                format,
                type,
                pixels);
        } finally {
            TextureUploadProfiler.recordGl(started, width, height);
        }
    }
}