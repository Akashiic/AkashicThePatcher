package dev.akashic.asynciconcompat.upload;

import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import net.minecraft.client.Minecraft;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

public final class BlockAtlasUploadBatcher {

    private static final Logger LOG =
        LogManager.getLogger("AkashicAsyncCompat/BlocksAtlasBatch");

    private static final String TARGET_PATH = "textures/blocks";
    private static final int MIN_DIMENSION = 2048;
    private static final int BUFFER_INTS = 1_048_576;

    private static final ThreadLocal<State> STATE =
        new ThreadLocal<State>();

    private static IntBuffer uploadBuffer;
    private static volatile boolean startupBatchConsumed;
    private static volatile boolean disabledForSession;

    private BlockAtlasUploadBatcher() {}

    public static void begin(
        String atlasPath,
        int textureId,
        int mipmapLevels,
        int atlasWidth,
        int atlasHeight) {

        abortStale("new-atlas");

        if (!Boolean.parseBoolean(
            System.getProperty("akashic.blocksAtlasBatch", "true"))) {
            return;
        }

        if (!TARGET_PATH.equals(atlasPath)) return;

        if (startupBatchConsumed) {
            LOG.info(
                "BlocksAtlasBatch bypass path={} reason=already-consumed",
                atlasPath);
            return;
        }

        if (disabledForSession) {
            LOG.info(
                "BlocksAtlasBatch bypass path={} reason=disabled-for-session",
                atlasPath);
            return;
        }

        if (Minecraft.getMinecraft().theWorld != null) {
            LOG.info(
                "BlocksAtlasBatch bypass path={} reason=world-loaded",
                atlasPath);
            return;
        }

        if (mipmapLevels != 0) {
            LOG.info(
                "BlocksAtlasBatch bypass path={} reason=mipmap-levels levels={}",
                atlasPath,
                Integer.valueOf(mipmapLevels));
            return;
        }

        if (atlasWidth < MIN_DIMENSION || atlasHeight < MIN_DIMENSION) {
            LOG.info(
                "BlocksAtlasBatch bypass path={} reason=small-atlas atlas={}x{}",
                atlasPath,
                Integer.valueOf(atlasWidth),
                Integer.valueOf(atlasHeight));
            return;
        }

        if (Minecraft.getMinecraft().gameSettings.anaglyph) {
            LOG.info(
                "BlocksAtlasBatch bypass path={} reason=anaglyph",
                atlasPath);
            return;
        }

        final long pixels =
            (long) atlasWidth * (long) atlasHeight;

        if (pixels <= 0L || pixels > Integer.MAX_VALUE) {
            LOG.warn(
                "BlocksAtlasBatch bypass path={} reason=invalid-size atlas={}x{}",
                atlasPath,
                Integer.valueOf(atlasWidth),
                Integer.valueOf(atlasHeight));
            return;
        }

        try {
            State state = new State(
                atlasPath,
                textureId,
                atlasWidth,
                atlasHeight,
                new int[(int) pixels],
                new BitSet((int) pixels),
                new ArrayList<Rect>(20000));

            STATE.set(state);

            LOG.info(
                "BlocksAtlasBatch armed path={} texture={} atlas={}x{} stageBytes={} policy=exact-regions",
                atlasPath,
                Integer.valueOf(textureId),
                Integer.valueOf(atlasWidth),
                Integer.valueOf(atlasHeight),
                Long.valueOf(pixels * 4L));
        } catch (OutOfMemoryError oom) {
            disabledForSession = true;
            LOG.warn(
                "BlocksAtlasBatch disabled path={} reason=stage-allocation-failed atlas={}x{}",
                atlasPath,
                Integer.valueOf(atlasWidth),
                Integer.valueOf(atlasHeight));
        }
    }

    /**
     * @return true when the caller must cancel the vanilla upload.
     *
     * The first valid upload deliberately passes through to vanilla so the
     * exact filter/wrap state is established by TextureUtil itself. Every
     * later staged upload is cancelled only after all safety checks pass.
     */
    public static boolean stage(
        int[][] mipmaps,
        int width,
        int height,
        int originX,
        int originY,
        boolean blur,
        boolean clamp) {

        State state = STATE.get();
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

        long required =
            (long) width * (long) height;

        if (required <= 0L || required > mipmaps[0].length) {
            return failOpen(
                state,
                "short-pixel-array required="
                    + required
                    + " actual="
                    + mipmaps[0].length);
        }

        long right = (long) originX + (long) width;
        long bottom = (long) originY + (long) height;

        if (originX < 0
            || originY < 0
            || right > state.width
            || bottom > state.height) {

            return failOpen(
                state,
                "out-of-bounds rect="
                    + originX
                    + ","
                    + originY
                    + " "
                    + width
                    + "x"
                    + height);
        }

        long overlapStarted = System.nanoTime();

        for (int row = 0; row < height; row++) {
            int start =
                (originY + row) * state.width + originX;
            int end = start + width;
            int hit = state.occupied.nextSetBit(start);

            if (hit >= 0 && hit < end) {
                state.overlapCheckNanos +=
                    System.nanoTime() - overlapStarted;

                return failOpen(
                    state,
                    "overlap rect="
                        + originX
                        + ","
                        + originY
                        + " "
                        + width
                        + "x"
                        + height
                        + " hit="
                        + hit);
            }
        }

        state.overlapCheckNanos +=
            System.nanoTime() - overlapStarted;

        long stageStarted = System.nanoTime();

        for (int row = 0; row < height; row++) {
            int sourceOffset = row * width;
            int destinationOffset =
                (originY + row) * state.width + originX;

            System.arraycopy(
                mipmaps[0],
                sourceOffset,
                state.pixels,
                destinationOffset,
                width);

            state.occupied.set(
                destinationOffset,
                destinationOffset + width);
        }

        state.rectangles.add(
            new Rect(originX, originY, width, height));

        state.stageNanos +=
            System.nanoTime() - stageStarted;
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
        State state = STATE.get();
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
        State state = STATE.get();
        if (state == null) return;
        if (!state.path.equals(atlasPath)) return;

        LOG.warn(
            "BlocksAtlasBatch stale-at-return path={} stagedCalls={} cancelled={}; flushing defensively",
            state.path,
            Integer.valueOf(state.stagedCalls),
            Integer.valueOf(state.cancelledUploads));

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
            throw new IllegalArgumentException(
                "invalid source dimensions");
        }

        if (destinationWidth <= 0 || destinationHeight <= 0) {
            throw new IllegalArgumentException(
                "invalid destination dimensions");
        }

        long right = (long) originX + width;
        long bottom = (long) originY + height;

        if (originX < 0
            || originY < 0
            || right > destinationWidth
            || bottom > destinationHeight) {

            throw new IllegalArgumentException(
                "rectangle outside atlas");
        }

        if ((long) width * (long) height > source.length) {
            throw new IllegalArgumentException(
                "source array too short");
        }

        if ((long) destinationWidth * (long) destinationHeight
            > destination.length) {

            throw new IllegalArgumentException(
                "destination array too short");
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

    static List<Rect> mergeRectsForTest(List<Rect> input) {
        return mergeRects(input);
    }

    private static boolean failOpen(
        State state,
        String reason) {

        disabledForSession = true;

        LOG.warn(
            "BlocksAtlasBatch fail-open path={} reason={} stagedCalls={} cancelled={}",
            state.path,
            reason,
            Integer.valueOf(state.stagedCalls),
            Integer.valueOf(state.cancelledUploads));

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

        long flushStarted = System.nanoTime();
        long mergeStarted = System.nanoTime();
        List<Rect> merged = mergeRects(state.rectangles);
        long mergeNanos =
            System.nanoTime() - mergeStarted;

        long mergedPixels = area(merged);

        if (mergedPixels != state.stagedPixels) {
            STATE.remove();
            disabledForSession = true;
            throw new IllegalStateException(
                "Block atlas merge changed covered area: staged="
                    + state.stagedPixels
                    + " merged="
                    + mergedPixels);
        }

        long bufferNanos = 0L;
        long glNanos = 0L;
        int glCalls = 0;
        long uploadedBytes = 0L;

        try {
            startupBatchConsumed = true;

            GL11.glBindTexture(
                GL11.GL_TEXTURE_2D,
                state.textureId);

            IntBuffer buffer = getUploadBuffer();

            for (Rect rect : merged) {
                int rowsPerUpload =
                    Math.max(1, BUFFER_INTS / rect.width);

                for (int row = 0;
                     row < rect.height;
                     row += rowsPerUpload) {

                    int rows =
                        Math.min(
                            rowsPerUpload,
                            rect.height - row);

                    long started =
                        System.nanoTime();

                    buffer.clear();

                    for (int localRow = 0;
                         localRow < rows;
                         localRow++) {

                        int sourceOffset =
                            (rect.y + row + localRow)
                                * state.width
                                + rect.x;

                        buffer.put(
                            state.pixels,
                            sourceOffset,
                            rect.width);
                    }

                    buffer.flip();

                    bufferNanos +=
                        System.nanoTime() - started;

                    started = System.nanoTime();

                    GL11.glTexSubImage2D(
                        GL11.GL_TEXTURE_2D,
                        0,
                        rect.x,
                        rect.y + row,
                        rect.width,
                        rows,
                        GL12.GL_BGRA,
                        GL12.GL_UNSIGNED_INT_8_8_8_8_REV,
                        buffer);

                    glNanos +=
                        System.nanoTime() - started;

                    glCalls++;

                    uploadedBytes +=
                        (long) rect.width
                            * (long) rows
                            * 4L;
                }
            }
        } finally {
            STATE.remove();
        }

        long flushNanos =
            System.nanoTime() - flushStarted;

        LOG.info(
            "BlocksAtlasBatch FLUSH path={} reason={} atlas={}x{} stagedCalls={} cancelled={} firstVanilla={} "
                + "originalRects={} mergedRects={} stagedPixels={} mergedPixels={} batchGlCalls={} uploadedBytes={} "
                + "stage={}ms overlapCheck={}ms merge={}ms bufferCopy={}ms gl={}ms flush={}ms savedCalls~{} policy=exact-regions",
            state.path,
            reason,
            Integer.valueOf(state.width),
            Integer.valueOf(state.height),
            Integer.valueOf(state.stagedCalls),
            Integer.valueOf(state.cancelledUploads),
            Boolean.valueOf(state.firstVanillaUploadPassed),
            Integer.valueOf(state.rectangles.size()),
            Integer.valueOf(merged.size()),
            Long.valueOf(state.stagedPixels),
            Long.valueOf(mergedPixels),
            Integer.valueOf(glCalls),
            Long.valueOf(uploadedBytes),
            Long.valueOf(millis(state.stageNanos)),
            Long.valueOf(millis(state.overlapCheckNanos)),
            Long.valueOf(millis(mergeNanos)),
            Long.valueOf(millis(bufferNanos)),
            Long.valueOf(millis(glNanos)),
            Long.valueOf(millis(flushNanos)),
            Integer.valueOf(
                Math.max(
                    0,
                    state.cancelledUploads - glCalls)));
    }

    private static List<Rect> mergeRects(
        List<Rect> input) {

        if (input == null || input.isEmpty()) {
            return Collections.emptyList();
        }

        List<Rect> current =
            new ArrayList<Rect>(input.size());

        for (Rect rect : input) {
            current.add(
                new Rect(
                    rect.x,
                    rect.y,
                    rect.width,
                    rect.height));
        }

        int previousSize = -1;
        int passes = 0;

        while (current.size() != previousSize
            && passes < 8) {

            previousSize = current.size();
            current = mergeHorizontal(current);
            current = mergeVertical(current);
            passes++;
        }

        return current;
    }

    private static List<Rect> mergeHorizontal(
        List<Rect> input) {

        List<Rect> sorted =
            new ArrayList<Rect>(input);

        Collections.sort(
            sorted,
            new Comparator<Rect>() {
                @Override
                public int compare(Rect a, Rect b) {
                    int c = compareInt(a.y, b.y);
                    if (c != 0) return c;
                    c = compareInt(a.height, b.height);
                    if (c != 0) return c;
                    c = compareInt(a.x, b.x);
                    if (c != 0) return c;
                    return compareInt(a.width, b.width);
                }
            });

        List<Rect> out =
            new ArrayList<Rect>(sorted.size());

        for (Rect rect : sorted) {
            if (!out.isEmpty()) {
                Rect last = out.get(out.size() - 1);

                if (last.y == rect.y
                    && last.height == rect.height
                    && (long) last.x + last.width == rect.x) {

                    out.set(
                        out.size() - 1,
                        new Rect(
                            last.x,
                            last.y,
                            last.width + rect.width,
                            last.height));

                    continue;
                }
            }

            out.add(rect);
        }

        return out;
    }

    private static List<Rect> mergeVertical(
        List<Rect> input) {

        List<Rect> sorted =
            new ArrayList<Rect>(input);

        Collections.sort(
            sorted,
            new Comparator<Rect>() {
                @Override
                public int compare(Rect a, Rect b) {
                    int c = compareInt(a.x, b.x);
                    if (c != 0) return c;
                    c = compareInt(a.width, b.width);
                    if (c != 0) return c;
                    c = compareInt(a.y, b.y);
                    if (c != 0) return c;
                    return compareInt(a.height, b.height);
                }
            });

        List<Rect> out =
            new ArrayList<Rect>(sorted.size());

        for (Rect rect : sorted) {
            if (!out.isEmpty()) {
                Rect last = out.get(out.size() - 1);

                if (last.x == rect.x
                    && last.width == rect.width
                    && (long) last.y + last.height == rect.y) {

                    out.set(
                        out.size() - 1,
                        new Rect(
                            last.x,
                            last.y,
                            last.width,
                            last.height + rect.height));

                    continue;
                }
            }

            out.add(rect);
        }

        return out;
    }

    private static long area(List<Rect> rects) {
        long sum = 0L;

        for (Rect rect : rects) {
            sum +=
                (long) rect.width
                    * (long) rect.height;
        }

        return sum;
    }

    private static int compareInt(int a, int b) {
        return a < b ? -1 : (a == b ? 0 : 1);
    }

    private static IntBuffer getUploadBuffer() {
        if (uploadBuffer == null
            || uploadBuffer.capacity() < BUFFER_INTS) {

            uploadBuffer =
                BufferUtils.createIntBuffer(
                    BUFFER_INTS);
        }

        return uploadBuffer;
    }

    private static void abortStale(String reason) {
        State stale = STATE.get();
        if (stale == null) return;

        LOG.warn(
            "BlocksAtlasBatch stale-state path={} reason={} stagedCalls={} cancelled={}; flushing before new atlas",
            stale.path,
            reason,
            Integer.valueOf(stale.stagedCalls),
            Integer.valueOf(stale.cancelledUploads));

        flush(
            stale,
            "stale:" + reason);
    }

    private static long millis(long nanos) {
        return nanos / 1_000_000L;
    }

    static final class Rect {
        final int x;
        final int y;
        final int width;
        final int height;

        Rect(
            int x,
            int y,
            int width,
            int height) {

            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        long area() {
            return (long) width * (long) height;
        }

        @Override
        public String toString() {
            return x
                + ","
                + y
                + " "
                + width
                + "x"
                + height;
        }
    }

    private static final class State {
        final String path;
        final int textureId;
        final int width;
        final int height;
        final int[] pixels;
        final BitSet occupied;
        final List<Rect> rectangles;

        boolean firstVanillaUploadPassed;
        int stagedCalls;
        int cancelledUploads;
        long stagedPixels;
        long stageNanos;
        long overlapCheckNanos;

        State(
            String path,
            int textureId,
            int width,
            int height,
            int[] pixels,
            BitSet occupied,
            List<Rect> rectangles) {

            this.path = path;
            this.textureId = textureId;
            this.width = width;
            this.height = height;
            this.pixels = pixels;
            this.occupied = occupied;
            this.rectangles = rectangles;
        }
    }
}
