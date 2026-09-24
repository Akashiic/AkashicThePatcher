package dev.akashic.asynciconcompat.mixins;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.client.renderer.texture.Stitcher;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.item.Item;
import net.minecraft.util.IIcon;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

import dev.akashic.asynciconcompat.CompatHooks;
import dev.akashic.asynciconcompat.CompatState;

@Mixin(value = TextureMap.class, priority = 1100)
public abstract class MixinTextureMapHybridAsync {

    @Unique
    private static final Logger akashic$LOGGER = LogManager.getLogger("AkashicAsyncCompat/Hybrid");

    @Shadow
    @Final
    @Mutable
    private Map<?, ?> mapRegisteredSprites = new ConcurrentHashMap<>();

    @Shadow
    @Final
    private String basePath;

    @Unique
    private final Map<String, CompletableFuture<IIcon>> akashic$processingIcons = new ConcurrentHashMap<>();

    @Unique
    private ExecutorService akashic$executor;

    @Unique
    private List<Future<?>> akashic$pending;

    @Unique
    private long akashic$startTime;

    @Unique
    private long akashic$atlasStartNanos;

    @Unique
    private long akashic$iconRegisterStartNanos;

    @Unique
    private long akashic$iconRegisterNanos;

    @Unique
    private long akashic$imageDecodeNanos;

    @Unique
    private long akashic$stitchNanos;

    @Unique
    private long akashic$allocateNanos;

    @Unique
    private long akashic$uploadNanos;

    @Unique
    private long akashic$preHookNanos;

    @Unique
    private long akashic$postHookNanos;

    @Unique
    private int akashic$imageDecodeCalls;

    @Unique
    private int akashic$uploadCalls;

    @Unique
    private volatile boolean akashic$aborted;

    @Inject(method = "loadTextureAtlas", at = @At("HEAD"))
    private void akashic$startAtlasProfile(IResourceManager resourceManager, CallbackInfo ci) {
        akashic$atlasStartNanos = System.nanoTime();
        akashic$iconRegisterNanos = 0L;
        akashic$imageDecodeNanos = 0L;
        akashic$stitchNanos = 0L;
        akashic$allocateNanos = 0L;
        akashic$uploadNanos = 0L;
        akashic$preHookNanos = 0L;
        akashic$postHookNanos = 0L;
        akashic$imageDecodeCalls = 0;
        akashic$uploadCalls = 0;
    }

    @Inject(method = "loadTextureAtlas", at = @At("RETURN"))
    private void akashic$finishAtlasProfile(IResourceManager resourceManager, CallbackInfo ci) {
        final long totalNanos = System.nanoTime() - akashic$atlasStartNanos;
        final long measuredNanos = akashic$iconRegisterNanos
            + akashic$imageDecodeNanos
            + akashic$stitchNanos
            + akashic$allocateNanos
            + akashic$uploadNanos
            + akashic$preHookNanos
            + akashic$postHookNanos;
        final long residualNanos = Math.max(0L, totalNanos - measuredNanos);

        akashic$LOGGER.info(
            "AtlasProfile path={} total={}ms icons={}ms decode={}ms/{} stitch={}ms alloc={}ms upload={}ms/{} preHook={}ms postHook={}ms residual={}ms",
            basePath,
            akashic$millis(totalNanos),
            akashic$millis(akashic$iconRegisterNanos),
            akashic$millis(akashic$imageDecodeNanos),
            akashic$imageDecodeCalls,
            akashic$millis(akashic$stitchNanos),
            akashic$millis(akashic$allocateNanos),
            akashic$millis(akashic$uploadNanos),
            akashic$uploadCalls,
            akashic$millis(akashic$preHookNanos),
            akashic$millis(akashic$postHookNanos),
            akashic$millis(residualNanos));
    }

    @WrapOperation(
        method = "loadTextureAtlas",
        at = @At(
            value = "INVOKE",
            target = "Ljavax/imageio/ImageIO;read(Ljava/io/InputStream;)Ljava/awt/image/BufferedImage;"))
    private BufferedImage akashic$profileImageDecode(
        InputStream input,
        Operation<BufferedImage> original) {

        final long started = System.nanoTime();
        try {
            return original.call(input);
        } finally {
            akashic$imageDecodeNanos += System.nanoTime() - started;
            akashic$imageDecodeCalls++;
        }
    }

    @WrapOperation(
        method = "loadTextureAtlas",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/texture/Stitcher;doStitch()V"))
    private void akashic$profileStitch(
        Stitcher stitcher,
        Operation<Void> original) {

        final long started = System.nanoTime();
        try {
            original.call(stitcher);
        } finally {
            akashic$stitchNanos += System.nanoTime() - started;
        }
    }

    @WrapOperation(
        method = "loadTextureAtlas",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/texture/TextureUtil;allocateTextureImpl(IIIIF)V"))
    private void akashic$profileAllocation(
        int textureId,
        int mipmapLevels,
        int width,
        int height,
        float anisotropic,
        Operation<Void> original) {

        final long started = System.nanoTime();
        try {
            original.call(textureId, mipmapLevels, width, height, anisotropic);
        } finally {
            akashic$allocateNanos += System.nanoTime() - started;
        }
    }

    @WrapOperation(
        method = "loadTextureAtlas",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/texture/TextureUtil;uploadTextureMipmap([[IIIIIZZ)V"))
    private void akashic$profileUpload(
        int[][] data,
        int width,
        int height,
        int originX,
        int originY,
        boolean blur,
        boolean clamp,
        Operation<Void> original) {

        final long started = System.nanoTime();
        try {
            original.call(data, width, height, originX, originY, blur, clamp);
        } finally {
            akashic$uploadNanos += System.nanoTime() - started;
            akashic$uploadCalls++;
        }
    }

    @WrapOperation(
        method = "loadTextureAtlas",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraftforge/client/ForgeHooksClient;onTextureStitchedPre(Lnet/minecraft/client/renderer/texture/TextureMap;)V",
            remap = false))
    private void akashic$profilePreHook(
        TextureMap map,
        Operation<Void> original) {

        final long started = System.nanoTime();
        try {
            original.call(map);
        } finally {
            akashic$preHookNanos += System.nanoTime() - started;
        }
    }

    @WrapOperation(
        method = "loadTextureAtlas",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraftforge/client/ForgeHooksClient;onTextureStitchedPost(Lnet/minecraft/client/renderer/texture/TextureMap;)V",
            remap = false))
    private void akashic$profilePostHook(
        TextureMap map,
        Operation<Void> original) {

        final long started = System.nanoTime();
        try {
            original.call(map);
        } finally {
            akashic$postHookNanos += System.nanoTime() - started;
        }
    }

    @Unique
    private static long akashic$millis(long nanos) {
        return nanos / 1_000_000L;
    }

    @Inject(method = "registerIcons", at = @At("HEAD"))
    private void akashic$startHybridIconLoading(CallbackInfo ci) {
        akashic$iconRegisterStartNanos = System.nanoTime();
        if (akashic$executor != null || akashic$pending != null) {
            akashic$LOGGER.warn("Cleaning stale hybrid icon executor before a new resource reload.");
            akashic$abort(new IllegalStateException("stale hybrid icon executor"));
        }

        CompatState.markSchedulerObserved();
        CompatHooks.beginReload(basePath);

        akashic$aborted = false;
        final int threadCount = Runtime.getRuntime().availableProcessors();
        akashic$startTime = System.currentTimeMillis();
        akashic$pending = new ArrayList<>();

        akashic$LOGGER.info(
            "Starting hybrid async icon loading with {} threads for {}",
            threadCount,
            basePath);

        akashic$executor = Executors.newFixedThreadPool(threadCount, runnable -> {
            Thread thread = new Thread(runnable, "AkashicIconLoader");
            thread.setDaemon(true);
            return thread;
        });
    }

    @WrapOperation(
        method = "registerIcons",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/block/Block;registerBlockIcons(Lnet/minecraft/client/renderer/texture/IIconRegister;)V"))
    private void akashic$registerBlockIconsHybrid(
        Block block,
        IIconRegister register,
        Operation<Void> original) {

        if (CompatHooks.shouldRunBlockSynchronously(block)) {
            akashic$awaitIfNeeded("block");
            try {
                original.call(block, register);
            } catch (Throwable failure) {
                akashic$abort(failure);
                throw akashic$propagate(failure);
            }
            return;
        }

        akashic$pending.add(akashic$executor.submit(() -> original.call(block, register)));
    }

    @Inject(
        method = "registerIcons",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/RenderGlobal;registerDestroyBlockIcons(Lnet/minecraft/client/renderer/texture/IIconRegister;)V"))
    private void akashic$awaitBlockIcons(CallbackInfo ci) {
        akashic$await("block");
        akashic$LOGGER.info("Block icons loaded.");
    }

    @WrapOperation(
        method = "registerIcons",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/item/Item;registerIcons(Lnet/minecraft/client/renderer/texture/IIconRegister;)V"))
    private void akashic$registerItemIconsHybrid(
        Item item,
        IIconRegister register,
        Operation<Void> original) {

        if (CompatHooks.shouldRunItemSynchronously(item)) {
            akashic$awaitIfNeeded("item");
            try {
                original.call(item, register);
            } catch (Throwable failure) {
                akashic$abort(failure);
                throw akashic$propagate(failure);
            }
            return;
        }

        akashic$pending.add(akashic$executor.submit(() -> original.call(item, register)));
    }

    @Inject(method = "registerIcons", at = @At("RETURN"))
    private void akashic$finishHybridIconLoading(CallbackInfo ci) {
        akashic$await("item");
        akashic$iconRegisterNanos += System.nanoTime() - akashic$iconRegisterStartNanos;
        akashic$LOGGER.info("Item icons loaded.");

        akashic$processingIcons.clear();
        if (akashic$executor != null) {
            akashic$executor.shutdown();
        }
        akashic$executor = null;
        if (akashic$pending != null) {
            akashic$pending.clear();
        }
        akashic$pending = null;

        akashic$LOGGER.info(
            "Finished hybrid async icon loading in {}ms; {}",
            System.currentTimeMillis() - akashic$startTime,
            CompatHooks.telemetry());
    }

    @Unique
    private void akashic$awaitIfNeeded(String kind) {
        if (akashic$pending != null && !akashic$pending.isEmpty()) {
            akashic$await(kind);
        }
    }

    @Unique
    private void akashic$await(String kind) {
        if (akashic$pending == null) {
            throw new IllegalStateException("Hybrid icon pending queue is not initialized");
        }

        akashic$LOGGER.info("Waiting for {} parallel {} icon jobs", akashic$pending.size(), kind);

        try {
            for (Future<?> future : akashic$pending) {
                future.get();
            }
        } catch (InterruptedException interrupted) {
            akashic$abort(interrupted);
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                "Interrupted while waiting for parallel " + kind + " icon registration",
                interrupted);
        } catch (ExecutionException failure) {
            Throwable cause = failure.getCause() == null ? failure : failure.getCause();
            akashic$LOGGER.error(
                "Parallel {} icon registration failed; aborting texture reload.",
                kind,
                cause);
            akashic$abort(cause);
            throw new IllegalStateException(
                "Parallel " + kind + " icon registration failed",
                cause);
        } finally {
            if (akashic$pending != null) {
                akashic$pending.clear();
            }
        }
    }

    @Unique
    private void akashic$abort(Throwable failure) {
        akashic$aborted = true;

        if (akashic$pending != null) {
            for (Future<?> future : akashic$pending) {
                future.cancel(true);
            }
        }

        for (CompletableFuture<IIcon> future : akashic$processingIcons.values()) {
            future.completeExceptionally(failure);
        }
        akashic$processingIcons.clear();

        if (akashic$executor != null) {
            akashic$executor.shutdownNow();
            akashic$executor = null;
        }

        if (akashic$pending != null) {
            akashic$pending.clear();
            akashic$pending = null;
        }
    }

    @Unique
    private RuntimeException akashic$propagate(Throwable failure) {
        if (failure instanceof RuntimeException) {
            return (RuntimeException) failure;
        }
        if (failure instanceof Error) {
            throw (Error) failure;
        }
        return new RuntimeException(failure);
    }

    @Redirect(
        method = "registerIcon",
        at = @At(
            value = "INVOKE",
            target = "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;"))
    private Object akashic$checkProcessing(Map<?, ?> instance, Object key) {
        Object existing = instance.get(key);
        if (existing != null) return existing;

        boolean[] newlyCreated = { false };
        CompletableFuture<IIcon> future = akashic$processingIcons.computeIfAbsent((String) key, ignored -> {
            newlyCreated[0] = true;
            return new CompletableFuture<>();
        });

        if (!newlyCreated[0]) {
            try {
                return future.get();
            } catch (Exception failure) {
                throw new RuntimeException(failure);
            }
        }

        return null;
    }

    @Inject(
        method = "registerIcon",
        at = @At(
            value = "INVOKE",
            target = "Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
            shift = Shift.AFTER))
    private void akashic$finishProcessing(
        String key,
        CallbackInfoReturnable<IIcon> cir,
        @Local Object object) {

        CompletableFuture<IIcon> future = akashic$processingIcons.remove(key);
        if (future == null) {
            if (akashic$aborted) {
                akashic$LOGGER.debug("Ignoring late icon completion after aborted reload: {}", key);
                return;
            }
            throw new IllegalStateException("Missing processing future for texture " + key);
        }
        future.complete((IIcon) object);
    }
}