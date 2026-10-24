package dev.akashic.asynciconcompat.graphics;

import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicLongArray;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.embeddedt.embeddium.impl.render.chunk.terrain.material.Material;

import com.gtnewhorizons.angelica.rendering.celeritas.AngelicaRenderPassConfiguration;

public final class TerrainVertexAlphaProbe {

    private static final Logger LOG =
        LogManager.getLogger("AkashicAsyncCompat/TerrainVertexAlpha");

    private static final int STRIDE_INTS = 8;
    private static final int COLOR_OFFSET = 5;
    private static final int PASS_COUNT = 4;
    private static final long LIMIT_PER_PASS = 262_144L;

    private static final String[] PASS_NAMES = {
        "SOLID", "CUTOUT_MIPPED", "TRANSLUCENT", "OTHER"
    };

    private static final long[] LOG_THRESHOLDS = {
        1_024L, 16_384L, 65_536L, LIMIT_PER_PASS
    };

    private static final AtomicLongArray TOTAL =
        new AtomicLongArray(PASS_COUNT);
    private static final AtomicLongArray ZERO =
        new AtomicLongArray(PASS_COUNT);
    private static final AtomicLongArray PARTIAL =
        new AtomicLongArray(PASS_COUNT);
    private static final AtomicLongArray OPAQUE =
        new AtomicLongArray(PASS_COUNT);
    private static final AtomicIntegerArray MIN_ALPHA =
        new AtomicIntegerArray(PASS_COUNT);
    private static final AtomicIntegerArray MAX_ALPHA =
        new AtomicIntegerArray(PASS_COUNT);
    private static final AtomicIntegerArray NEXT_LOG =
        new AtomicIntegerArray(PASS_COUNT);
    private static final AtomicIntegerArray FIRST_NON_OPAQUE =
        new AtomicIntegerArray(PASS_COUNT);
    private static final AtomicIntegerArray FIRST_SET =
        new AtomicIntegerArray(PASS_COUNT);
    private static final AtomicIntegerArray MALFORMED_LOGGED =
        new AtomicIntegerArray(PASS_COUNT);

    static {
        reset();
    }

    private TerrainVertexAlphaProbe() {}

    public static void reset() {
        for (int i = 0; i < PASS_COUNT; i++) {
            TOTAL.set(i, 0L);
            ZERO.set(i, 0L);
            PARTIAL.set(i, 0L);
            OPAQUE.set(i, 0L);
            MIN_ALPHA.set(i, 256);
            MAX_ALPHA.set(i, -1);
            NEXT_LOG.set(i, 0);
            FIRST_NON_OPAQUE.set(i, 0);
            FIRST_SET.set(i, 0);
            MALFORMED_LOGGED.set(i, 0);
        }
    }

    public static void observe(
        int[] rawBuffer,
        int vertexCount,
        Material material) {

        if (!Boolean.parseBoolean(
            System.getProperty("akashic.terrainVertexAlphaProbe", "true"))) {
            return;
        }

        if (rawBuffer == null || vertexCount <= 0) {
            return;
        }

        final int pass = classify(material);
        final long already = TOTAL.get(pass);
        if (already >= LIMIT_PER_PASS) {
            return;
        }

        final int availableVertices = rawBuffer.length / STRIDE_INTS;
        final int safeCount = Math.min(vertexCount, availableVertices);

        if (safeCount != vertexCount
            && MALFORMED_LOGGED.compareAndSet(pass, 0, 1)) {

            LOG.warn(
                "TerrainVertexAlpha malformed pass={} vertexCount={} available={} rawInts={}",
                PASS_NAMES[pass],
                vertexCount,
                availableVertices,
                rawBuffer.length);
        }

        final int budget =
            (int) Math.min((long) safeCount, LIMIT_PER_PASS - already);

        long localZero = 0L;
        long localPartial = 0L;
        long localOpaque = 0L;
        int localMin = 256;
        int localMax = -1;
        int firstNonOpaque = 0;
        boolean foundNonOpaque = false;

        for (int i = 0; i < budget; i++) {
            final int color = rawBuffer[i * STRIDE_INTS + COLOR_OFFSET];
            final int alpha = color >>> 24 & 0xFF;

            if (alpha == 0) {
                localZero++;
            } else if (alpha == 255) {
                localOpaque++;
            } else {
                localPartial++;
            }

            if (alpha < localMin) localMin = alpha;
            if (alpha > localMax) localMax = alpha;

            if (alpha != 255 && !foundNonOpaque) {
                firstNonOpaque = color;
                foundNonOpaque = true;
            }
        }

        if (budget == 0) {
            return;
        }

        final long total = TOTAL.addAndGet(pass, budget);
        ZERO.addAndGet(pass, localZero);
        PARTIAL.addAndGet(pass, localPartial);
        OPAQUE.addAndGet(pass, localOpaque);
        updateMin(pass, localMin);
        updateMax(pass, localMax);

        if (foundNonOpaque && FIRST_SET.compareAndSet(pass, 0, 1)) {
            FIRST_NON_OPAQUE.set(pass, firstNonOpaque);
        }

        maybeLog(pass, total);
    }

    private static int classify(Material material) {
        if (material != null) {
            if (material.pass == AngelicaRenderPassConfiguration.SOLID_PASS) {
                return 0;
            }
            if (material.pass == AngelicaRenderPassConfiguration.CUTOUT_MIPPED_PASS) {
                return 1;
            }
            if (material.pass == AngelicaRenderPassConfiguration.TRANSLUCENT_PASS) {
                return 2;
            }
        }
        return 3;
    }

    private static void updateMin(int pass, int value) {
        int current = MIN_ALPHA.get(pass);
        while (value < current
            && !MIN_ALPHA.compareAndSet(pass, current, value)) {
            current = MIN_ALPHA.get(pass);
        }
    }

    private static void updateMax(int pass, int value) {
        int current = MAX_ALPHA.get(pass);
        while (value > current
            && !MAX_ALPHA.compareAndSet(pass, current, value)) {
            current = MAX_ALPHA.get(pass);
        }
    }

    private static void maybeLog(int pass, long total) {
        final int index = NEXT_LOG.get(pass);
        if (index >= LOG_THRESHOLDS.length
            || total < LOG_THRESHOLDS[index]
            || !NEXT_LOG.compareAndSet(pass, index, index + 1)) {
            return;
        }

        final boolean firstSet = FIRST_SET.get(pass) != 0;
        final String first = firstSet
            ? String.format("0x%08X", FIRST_NON_OPAQUE.get(pass))
            : "none";

        LOG.info(
            "TerrainVertexAlpha pass={} sampled={} a0={} partial={} a255={} min={} max={} firstNonOpaque={}",
            PASS_NAMES[pass],
            TOTAL.get(pass),
            ZERO.get(pass),
            PARTIAL.get(pass),
            OPAQUE.get(pass),
            MIN_ALPHA.get(pass),
            MAX_ALPHA.get(pass),
            first);
    }
}
