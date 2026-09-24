package dev.akashic.asynciconcompat.upload;

import java.nio.IntBuffer;

import net.minecraft.client.Minecraft;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

public final class ItemsAtlasUploadBatcher {

    private static final Logger LOG =
        LogManager.getLogger("AkashicAsyncCompat/ItemsAtlasBatch");

    private static final String TARGET_PATH = "textures/items";
    private static final int MIN_DIMENSION = 2048;
    private static final int BUFFER_INTS = 1_048_576;

    private static final ThreadLocal<State> STATE =
        new ThreadLocal<State>();

    private static IntBuffer uploadBuffer;
    private static volatile boolean startupBatchConsumed;

    private ItemsAtlasUploadBatcher() {}

    public static void begin(
        String atlasPath,
        int textureId,
        int mipmapLevels,
        int atlasWidth,
        int atlasHeight) {

        abortStale("new-atlas");

        if (!Boolean.parseBoolean(
            System.getProperty("akashic.itemsAtlasBatch", "true"))) {
            return;
        }

        if (!TARGET_PATH.equals(atlasPath)) return;
        if (startupBatchConsumed) {
            LOG.info(
                "ItemsAtlasBatch bypass path={} reason=already-consumed",
                atlasPath);
            return;
        }
        if (Minecraft.getMinecraft().theWorld != null) {
            LOG.info(
                "ItemsAtlasBatch bypass path={} reason=world-loaded",
                atlasPath);
            return;
        }
        if (mipmapLevels != 0) {
            LOG.info(
                "ItemsAtlasBatch bypass path={} reason=mipmap-levels levels={}",
                atlasPath,
                mipmapLevels);
            return;
        }
        if (atlasWidth < MIN_DIMENSION || atlasHeight < MIN_DIMENSION) {
            LOG.info(
                "ItemsAtlasBatch bypass path={} reason=small-atlas atlas={}x{}",
                atlasPath,
                atlasWidth,
                atlasHeight);
            return;
        }

        if (Minecraft.getMinecraft().gameSettings.anaglyph) {
            LOG.info(
                "ItemsAtlasBatch bypass path={} reason=anaglyph",
                atlasPath);
            return;
        }

        final long pixels =
            (long) atlasWidth * (long) atlasHeight;
        if (pixels <= 0L || pixels > Integer.MAX_VALUE) {
            LOG.warn(
                "ItemsAtlasBatch bypass path={} reason=invalid-size atlas={}x{}",
                atlasPath,
                atlasWidth,
                atlasHeight);
            return;
        }

        try {
            final State state = new State(
                atlasPath,
                textureId,
                atlasWidth,
                atlasHeight,
                new int[(int) pixels]);
            STATE.set(state);

            LOG.info(
                "ItemsAtlasBatch armed path={} texture={} atlas={}x{} stageBytes={}",
                atlasPath,
                textureId,
                atlasWidth,
                atlasHeight,
                pixels * 4L);
        } catch (OutOfMemoryError oom) {
            LOG.warn(
                "ItemsAtlasBatch disabled path={} reason=stage-allocation-failed atlas={}x{}",
                atlasPath,
                atlasWidth,
                atlasHeight);
        }
    }

    /**
     * @return true when the caller must cancel the vanilla upload.
     * The first staged upload is deliberately allowed through so vanilla
     * establishes the exact filter/wrap state before batched flushing.
     */
    public static boolean stage(
        int[][] mipmaps,
        int width,
        int height,
        int originX,
        int originY,
        boolean blur,
        boolean clamp) {

        final State state = STATE.get();
        if (state == null) return false;

        if (blur || clamp) {
            return failOpen(
                state,
                "nonstandard-flags blur=" + blur + " clamp=" + clamp);
        }

        if (mipmaps == null
            || mipmaps.length != 1
            || mipmaps[0] == null) {
            return failOpen(
                state,
                "unexpected-mipmap-shape");
        }

        if (width <= 0 || height <= 0) {
            return failOpen(
                state,
                "invalid-sprite-size " + width + "x" + height);
        }

        final long required =
            (long) width * (long) height;
        if (required > mipmaps[0].length) {
            return failOpen(
                state,
                "short-pixel-array required="
                    + required + " actual=" + mipmaps[0].length);
        }

        if (originX < 0
            || originY < 0
            || originX + width > state.width
            || originY + height > state.height) {
            return failOpen(
                state,
                "out-of-bounds rect="
                    + originX + "," + originY + " "
                    + width + "x" + height);
        }

        final long started = System.nanoTime();
        copyRect(
            mipmaps[0],
            width,
            height,
            state.pixels,
            state.width,
            state.height,
            originX,
            originY);
        state.stageNanos += System.nanoTime() - started;
        state.stagedCalls++;
        state.stagedPixels += required;

        if (!state.firstVanillaUploadPassed) {
            state.firstVanillaUploadPassed = true;
            return false;
        }

        state.cancelledUploads++;
        return true;
    }

    public static void flushIfActive(String atlasPath) {
        final State state = STATE.get();
        if (state == null) return;
        if (!state.path.equals(atlasPath)) {
            failOpen(
                state,
                "flush-path-mismatch actual=" + atlasPath);
            return;
        }

        flush(state, "normal");
    }

    public static void abortIfActive(String atlasPath) {
        final State state = STATE.get();
        if (state == null) return;
        if (!state.path.equals(atlasPath)) return;

        LOG.warn(
            "ItemsAtlasBatch stale-at-return path={} stagedCalls={} cancelled={}; flushing defensively",
            state.path,
            state.stagedCalls,
            state.cancelledUploads);
        flush(state, "return-fallback");
    }

    static void copyRect(
        int[] source,
        int width,
        int height,
        int[] destination,
        int destinationWidth,
        int destinationHeight,
        int originX,
        int originY) {

        if (source == null || destination == null) {
            throw new IllegalArgumentException("null pixel array");
        }
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("invalid source dimensions");
        }
        if (destinationWidth <= 0 || destinationHeight <= 0) {
            throw new IllegalArgumentException("invalid destination dimensions");
        }
        if (originX < 0
            || originY < 0
            || originX + width > destinationWidth
            || originY + height > destinationHeight) {
            throw new IllegalArgumentException("rectangle outside atlas");
        }
        if ((long) width * (long) height > source.length) {
            throw new IllegalArgumentException("source array too short");
        }
        if ((long) destinationWidth * (long) destinationHeight
            > destination.length) {
            throw new IllegalArgumentException("destination array too short");
        }

        for (int row = 0; row < height; row++) {
            System.arraycopy(
                source,
                row * width,
                destination,
                (originY + row) * destinationWidth + originX,
                width);
        }
    }

    private static boolean failOpen(
        State state,
        String reason) {

        LOG.warn(
            "ItemsAtlasBatch fail-open path={} reason={} stagedCalls={} cancelled={}",
            state.path,
            reason,
            state.stagedCalls,
            state.cancelledUploads);

        if (state.cancelledUploads > 0) {
            flush(state, "fail-open:" + reason);
        } else {
            STATE.remove();
        }
        return false;
    }

    private static void flush(
        State state,
        String reason) {

        if (STATE.get() != state) return;

        final long flushStarted = System.nanoTime();
        long bufferNanos = 0L;
        long glNanos = 0L;
        int glCalls = 0;
        long uploadedBytes = 0L;

        try {
            startupBatchConsumed = true;
            GL11.glBindTexture(
                GL11.GL_TEXTURE_2D,
                state.textureId);

            final IntBuffer buffer = getUploadBuffer();
            final int rowsPerUpload =
                Math.max(1, BUFFER_INTS / state.width);

            for (int row = 0;
                 row < state.height;
                 row += rowsPerUpload) {

                final int rows =
                    Math.min(rowsPerUpload, state.height - row);
                final int count = state.width * rows;

                long started = System.nanoTime();
                buffer.clear();
                buffer.put(
                    state.pixels,
                    row * state.width,
                    count);
                buffer.flip();
                bufferNanos += System.nanoTime() - started;

                started = System.nanoTime();
                GL11.glTexSubImage2D(
                    GL11.GL_TEXTURE_2D,
                    0,
                    0,
                    row,
                    state.width,
                    rows,
                    GL12.GL_BGRA,
                    GL12.GL_UNSIGNED_INT_8_8_8_8_REV,
                    buffer);
                glNanos += System.nanoTime() - started;

                glCalls++;
                uploadedBytes += (long) count * 4L;
            }
        } finally {
            STATE.remove();
        }

        final long flushNanos =
            System.nanoTime() - flushStarted;

        LOG.info(
            "ItemsAtlasBatch FLUSH path={} reason={} atlas={}x{} stagedCalls={} cancelled={} firstVanilla={} stagedPixels={} batchGlCalls={} uploadedBytes={} stage={}ms bufferCopy={}ms gl={}ms flush={}ms savedCalls~{}",
            state.path,
            reason,
            state.width,
            state.height,
            state.stagedCalls,
            state.cancelledUploads,
            state.firstVanillaUploadPassed,
            state.stagedPixels,
            glCalls,
            uploadedBytes,
            millis(state.stageNanos),
            millis(bufferNanos),
            millis(glNanos),
            millis(flushNanos),
            Math.max(0, state.cancelledUploads - glCalls));
    }

    private static IntBuffer getUploadBuffer() {
        if (uploadBuffer == null
            || uploadBuffer.capacity() < BUFFER_INTS) {
            uploadBuffer =
                BufferUtils.createIntBuffer(BUFFER_INTS);
        }
        return uploadBuffer;
    }

    private static void abortStale(String reason) {
        final State stale = STATE.get();
        if (stale == null) return;

        LOG.warn(
            "ItemsAtlasBatch stale-state path={} reason={} stagedCalls={} cancelled={}; flushing before new atlas",
            stale.path,
            reason,
            stale.stagedCalls,
            stale.cancelledUploads);
        flush(stale, "stale:" + reason);
    }

    private static long millis(long nanos) {
        return nanos / 1_000_000L;
    }

    private static final class State {
        final String path;
        final int textureId;
        final int width;
        final int height;
        final int[] pixels;

        boolean firstVanillaUploadPassed;
        int stagedCalls;
        int cancelledUploads;
        long stagedPixels;
        long stageNanos;

        State(
            String path,
            int textureId,
            int width,
            int height,
            int[] pixels) {

            this.path = path;
            this.textureId = textureId;
            this.width = width;
            this.height = height;
            this.pixels = pixels;
        }
    }
}