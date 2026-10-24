package dev.akashic.asynciconcompat.mixins;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.Stitcher;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.akashic.asynciconcompat.cache.StitchLayoutCache;
import dev.akashic.asynciconcompat.cache.StitchLayoutCache.Entry;
import dev.akashic.asynciconcompat.cache.StitchLayoutCache.Layout;

@Mixin(Stitcher.class)
public abstract class MixinStitcherLayoutCache {

    @Unique
    private static final Logger akashic$LOGGER =
        LogManager.getLogger("AkashicAsyncCompat/StitchCache");

    @Shadow
    @Final
    private int mipmapLevelStitcher;

    @Shadow
    @Final
    private Set<Stitcher.Holder> setStitchHolders;

    @Shadow
    @Final
    private List<Stitcher.Slot> stitchSlots;

    @Shadow
    private int currentWidth;

    @Shadow
    private int currentHeight;

    @Shadow
    @Final
    private int maxWidth;

    @Shadow
    @Final
    private int maxHeight;

    @Shadow
    @Final
    private boolean forcePowerOf2;

    @Shadow
    @Final
    private int maxTileDimension;

    @Unique
    private String akashic$fingerprint;

    @Unique
    private long akashic$normalStitchStartNanos;

    @Unique
    private boolean akashic$cacheHit;

    @Inject(method = "doStitch", at = @At("HEAD"), cancellable = true)
    private void akashic$restoreCachedLayout(CallbackInfo ci) {
        akashic$cacheHit = false;
        akashic$fingerprint = null;

        try {
            akashic$fingerprint = StitchLayoutCache.fingerprint(
                setStitchHolders,
                mipmapLevelStitcher,
                maxWidth,
                maxHeight,
                forcePowerOf2,
                maxTileDimension);

            File gameDirectory = Minecraft.getMinecraft().mcDataDir;
            long readStarted = System.nanoTime();
            Layout layout = StitchLayoutCache.load(
                gameDirectory,
                akashic$fingerprint,
                setStitchHolders,
                maxWidth,
                maxHeight);
            long readMillis = (System.nanoTime() - readStarted) / 1_000_000L;

            if (layout == null) {
                akashic$normalStitchStartNanos = System.nanoTime();
                akashic$LOGGER.info(
                    "StitchLayoutCache MISS sprites={} fingerprint={} read={}ms",
                    setStitchHolders.size(),
                    akashic$shortFingerprint(akashic$fingerprint),
                    readMillis);
                return;
            }

            Map<String, Stitcher.Holder> byName =
                StitchLayoutCache.validateLayout(layout, setStitchHolders);
            Map<Stitcher.Holder, Boolean> originalRotations =
                new HashMap<>(byName.size() * 2);

            stitchSlots.clear();
            try {
                for (Entry entry : layout.entries) {
                    Stitcher.Holder holder = byName.get(entry.name);
                    originalRotations.put(holder, holder.isRotated());

                    if (holder.isRotated() != entry.rotated) {
                        holder.rotate();
                    }

                    if (holder.getWidth() != entry.packedWidth
                        || holder.getHeight() != entry.packedHeight) {
                        throw new IllegalStateException(
                            "Packed dimensions changed while applying cache for " + entry.name);
                    }

                    Stitcher.Slot slot = new Stitcher.Slot(
                        entry.x,
                        entry.y,
                        entry.packedWidth,
                        entry.packedHeight);

                    if (!slot.addSlot(holder)) {
                        throw new IllegalStateException(
                            "Cached slot rejected holder " + entry.name);
                    }
                    stitchSlots.add(slot);
                }

                currentWidth = layout.atlasWidth;
                currentHeight = layout.atlasHeight;
                akashic$cacheHit = true;

                akashic$LOGGER.info(
                    "StitchLayoutCache HIT sprites={} atlas={}x{} fingerprint={} restore={}ms",
                    layout.entries.size(),
                    currentWidth,
                    currentHeight,
                    akashic$shortFingerprint(akashic$fingerprint),
                    readMillis);

                ci.cancel();
            } catch (Throwable applicationFailure) {
                stitchSlots.clear();
                for (Map.Entry<Stitcher.Holder, Boolean> entry : originalRotations.entrySet()) {
                    Stitcher.Holder holder = entry.getKey();
                    if (holder.isRotated() != entry.getValue()) {
                        holder.rotate();
                    }
                }

                akashic$cacheHit = false;
                akashic$normalStitchStartNanos = System.nanoTime();
                akashic$LOGGER.warn(
                    "StitchLayoutCache application rejected; falling back to normal stitch: {}",
                    applicationFailure.toString());
            }
        } catch (Throwable cacheFailure) {
            stitchSlots.clear();
            akashic$cacheHit = false;
            akashic$normalStitchStartNanos = System.nanoTime();
            akashic$LOGGER.warn(
                "StitchLayoutCache unavailable; falling back to normal stitch: {}",
                cacheFailure.toString());
        }
    }

    @Inject(method = "doStitch", at = @At("RETURN"))
    private void akashic$storeNormalLayout(CallbackInfo ci) {
        if (akashic$cacheHit || akashic$fingerprint == null) {
            return;
        }

        long stitchMillis =
            Math.max(0L, System.nanoTime() - akashic$normalStitchStartNanos) / 1_000_000L;

        File gameDirectory = Minecraft.getMinecraft().mcDataDir;
        long saveStarted = System.nanoTime();
        boolean saved = StitchLayoutCache.save(
            gameDirectory,
            akashic$fingerprint,
            currentWidth,
            currentHeight,
            stitchSlots,
            setStitchHolders);
        long saveMillis = (System.nanoTime() - saveStarted) / 1_000_000L;

        akashic$LOGGER.info(
            "StitchLayoutCache SEEDED sprites={} atlas={}x{} fingerprint={} stitch={}ms save={}ms saved={}",
            setStitchHolders.size(),
            currentWidth,
            currentHeight,
            akashic$shortFingerprint(akashic$fingerprint),
            stitchMillis,
            saveMillis,
            saved);
    }

    @Unique
    private static String akashic$shortFingerprint(String fingerprint) {
        if (fingerprint == null) return "null";
        return fingerprint.length() <= 12 ? fingerprint : fingerprint.substring(0, 12);
    }
}