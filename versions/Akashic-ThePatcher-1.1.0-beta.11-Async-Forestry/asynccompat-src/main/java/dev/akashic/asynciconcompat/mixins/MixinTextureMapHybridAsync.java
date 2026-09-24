package dev.akashic.asynciconcompat.mixins;

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
import net.minecraft.client.renderer.texture.TextureMap;
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

    @Inject(method = "registerIcons", at = @At("HEAD"))
    private void akashic$startHybridIconLoading(CallbackInfo ci) {
        CompatState.markSchedulerObserved();
        CompatHooks.beginReload(basePath);

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
            original.call(block, register);
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
            original.call(item, register);
            return;
        }

        akashic$pending.add(akashic$executor.submit(() -> original.call(item, register)));
    }

    @Inject(method = "registerIcons", at = @At("RETURN"))
    private void akashic$finishHybridIconLoading(CallbackInfo ci) {
        akashic$await("item");
        akashic$LOGGER.info("Item icons loaded.");

        akashic$processingIcons.clear();
        akashic$executor.shutdown();
        akashic$executor = null;
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
            akashic$cancelPending();
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                "Interrupted while waiting for parallel " + kind + " icon registration",
                interrupted);
        } catch (ExecutionException failure) {
            akashic$cancelPending();
            Throwable cause = failure.getCause() == null ? failure : failure.getCause();
            akashic$LOGGER.error(
                "Parallel {} icon registration failed; aborting texture reload.",
                kind,
                cause);
            throw new IllegalStateException(
                "Parallel " + kind + " icon registration failed",
                cause);
        } finally {
            akashic$pending.clear();
        }
    }

    @Unique
    private void akashic$cancelPending() {
        if (akashic$pending == null) return;
        for (Future<?> future : akashic$pending) {
            future.cancel(true);
        }
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
            throw new IllegalStateException("Missing processing future for texture " + key);
        }
        future.complete((IIcon) object);
    }
}