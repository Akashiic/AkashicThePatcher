package dev.akashic.asynciconcompat.startup;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class StartupAtlasAudit {
    private static final Logger LOG = LogManager.getLogger("Akashic-ThePatcher/StartupAtlas");
    private static final String TARGET = "textures/blocks";
    private static final ThreadLocal<State> STATE = new ThreadLocal<State>();
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    private StartupAtlasAudit() {}

    public static void begin(Object textureMap) {
        State stale = STATE.get();
        if (stale != null) {
            report(stale, -1, -1, "stale-before-next-atlas");
            STATE.remove();
        }
        if (!Boolean.parseBoolean(System.getProperty("akashic.startupAudit", "true"))) return;
        String path = stringField(textureMap, "field_94254_c", "basePath");
        if (!TARGET.equals(path)) return;
        int configuredMip = intField(textureMap, "field_147636_j", "mipmapLevels", -1);
        State state = new State(SEQUENCE.incrementAndGet(), path, configuredMip);
        STATE.set(state);
        LOG.info("BlockAtlasEligibility BEGIN seq={} path={} configuredMip={}",
            Integer.valueOf(state.sequence), path, Integer.valueOf(configuredMip));
    }

    public static void allocation(int textureId, int mipmapLevels, int width, int height, float anisotropic) {
        State s = STATE.get();
        if (s == null) return;
        long started = System.nanoTime();
        try {
            s.allocationCalls++;
            long area = safeArea(width, height);
            if (area >= s.allocatedArea) {
                if (area > s.allocatedArea && s.uploadCalls > 0) {
                    s.resetUploads();
                    s.allocationResets++;
                }
                s.textureId = textureId;
                s.allocatedMip = mipmapLevels;
                s.atlasWidth = width;
                s.atlasHeight = height;
                s.anisotropic = anisotropic;
                s.allocatedArea = area;
                s.armed = area > 0L;
            }
        } finally {
            s.observerNanos += System.nanoTime() - started;
        }
    }

    public static void upload(int[][] mipmaps, int width, int height, int originX, int originY,
                              boolean blur, boolean clamp) {
        State s = STATE.get();
        if (s == null || !s.armed) return;
        long started = System.nanoTime();
        try {
            s.uploadCalls++;
            if (blur) s.blurCalls++;
            if (clamp) s.clampCalls++;
            if (width <= 0 || height <= 0 || originX < 0 || originY < 0) s.invalidGeometry++;
            if (width > s.maxSpriteWidth) s.maxSpriteWidth = width;
            if (height > s.maxSpriteHeight) s.maxSpriteHeight = height;
            if (width > 0 && width < s.minSpriteWidth) s.minSpriteWidth = width;
            if (height > 0 && height < s.minSpriteHeight) s.minSpriteHeight = height;

            long basePixels = safeArea(width, height);
            s.basePixels += Math.max(0L, basePixels);
            if (s.atlasWidth > 0 && s.atlasHeight > 0
                && ((long)originX + width > s.atlasWidth || (long)originY + height > s.atlasHeight)) {
                s.outOfBounds++;
            }

            long rectKey = rectKey(originX, originY, width, height);
            if (!s.rectangles.add(Long.valueOf(rectKey))) s.duplicateRects++;

            if (mipmaps == null) {
                s.nullMipArrays++;
                return;
            }
            int len = mipmaps.length;
            if (len == 1) s.singleLevelCalls++;
            else if (len > 1) s.multiLevelCalls++;
            else s.emptyMipArrays++;
            if (len > s.maxMipArrayLength) s.maxMipArrayLength = len;
            if (len < s.mipLengthHistogram.length) s.mipLengthHistogram[len]++;
            else s.mipLengthOverflow++;

            for (int level = 0; level < len; level++) {
                int[] data = mipmaps[level];
                if (data == null) {
                    s.nullLevels++;
                    continue;
                }
                s.presentLevels++;
                s.levelPixels += data.length;
                int ew = Math.max(1, width >> level);
                int eh = Math.max(1, height >> level);
                long expected = safeArea(ew, eh);
                if (expected > data.length) s.shortLevelArrays++;
            }
        } finally {
            s.observerNanos += System.nanoTime() - started;
        }
    }

    public static void finish(Object textureMap) {
        State s = STATE.get();
        if (s == null) return;
        int uploaded = collectionSize(textureMap, "field_94252_e", "mapUploadedSprites");
        int animated = collectionSize(textureMap, "field_94258_i", "listAnimatedSprites");
        report(s, uploaded, animated, "return");
        STATE.remove();
    }

    private static void report(State s, int uploaded, int animated, String reason) {
        long elapsedMs = (System.nanoTime() - s.startedNanos) / 1_000_000L;
        long atlasPixels = safeArea(s.atlasWidth, s.atlasHeight);
        double coverage = atlasPixels > 0L ? (100.0D * s.basePixels / (double)atlasPixels) : -1.0D;
        String eligibility;
        if (!s.armed) eligibility = "UNKNOWN_NO_ALLOCATION";
        else if (s.invalidGeometry > 0 || s.outOfBounds > 0 || s.nullMipArrays > 0 || s.emptyMipArrays > 0)
            eligibility = "UNSAFE_INVALID_UPLOAD";
        else if (s.blurCalls > 0 || s.clampCalls > 0)
            eligibility = "UNSAFE_NONSTANDARD_FLAGS";
        else if (s.allocatedMip != 0 || s.multiLevelCalls > 0)
            eligibility = "NEEDS_MIPMAP_AWARE_BATCH";
        else if (s.duplicateRects > 0)
            eligibility = "REVIEW_DUPLICATE_DESTINATIONS";
        else eligibility = "CANDIDATE_SINGLE_LEVEL_BATCH";

        LOG.info(
            "BlockAtlasEligibility END seq={} reason={} atlas={}x{} texture={} configuredMip={} allocatedMip={} "
            + "allocCalls={} allocResets={} uploads={} uploadedSprites={} animatedSprites={} singleLevel={} multiLevel={} "
            + "mipLenMax={} mipLenHist={} mipOverflow={} presentLevels={} nullLevels={} shortLevels={} "
            + "blur={} clamp={} invalidGeom={} outOfBounds={} duplicateRects={} uniqueRects={} "
            + "basePixels={} levelPixels={} coveragePct={} spriteMin={}x{} spriteMax={}x{} anisotropic={} "
            + "eligibility={} rectOverlapCheck=duplicate-only observer={}ms elapsed={}ms",
            Integer.valueOf(s.sequence), reason,
            Integer.valueOf(s.atlasWidth), Integer.valueOf(s.atlasHeight), Integer.valueOf(s.textureId),
            Integer.valueOf(s.configuredMip), Integer.valueOf(s.allocatedMip),
            Integer.valueOf(s.allocationCalls), Integer.valueOf(s.allocationResets),
            Integer.valueOf(s.uploadCalls), Integer.valueOf(uploaded), Integer.valueOf(animated),
            Integer.valueOf(s.singleLevelCalls), Integer.valueOf(s.multiLevelCalls),
            Integer.valueOf(s.maxMipArrayLength), histogram(s.mipLengthHistogram), Integer.valueOf(s.mipLengthOverflow),
            Long.valueOf(s.presentLevels), Long.valueOf(s.nullLevels), Long.valueOf(s.shortLevelArrays),
            Integer.valueOf(s.blurCalls), Integer.valueOf(s.clampCalls), Integer.valueOf(s.invalidGeometry),
            Integer.valueOf(s.outOfBounds), Integer.valueOf(s.duplicateRects), Integer.valueOf(s.rectangles.size()),
            Long.valueOf(s.basePixels), Long.valueOf(s.levelPixels),
            String.format(Locale.ROOT, "%.2f", Double.valueOf(coverage)),
            Integer.valueOf(s.minSpriteWidth == Integer.MAX_VALUE ? 0 : s.minSpriteWidth),
            Integer.valueOf(s.minSpriteHeight == Integer.MAX_VALUE ? 0 : s.minSpriteHeight),
            Integer.valueOf(s.maxSpriteWidth), Integer.valueOf(s.maxSpriteHeight),
            Float.valueOf(s.anisotropic), eligibility,
            Long.valueOf(s.observerNanos / 1_000_000L), Long.valueOf(elapsedMs));
    }

    private static String histogram(int[] h) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < h.length; i++) {
            if (h[i] == 0) continue;
            if (b.length() > 0) b.append(',');
            b.append(i).append(':').append(h[i]);
        }
        return b.length() == 0 ? "none" : b.toString();
    }

    private static long rectKey(int x, int y, int w, int h) {
        return ((long)(x & 0xFFFF) << 48) | ((long)(y & 0xFFFF) << 32)
            | ((long)(w & 0xFFFF) << 16) | (long)(h & 0xFFFF);
    }

    private static long safeArea(int w, int h) {
        if (w <= 0 || h <= 0) return -1L;
        return (long)w * (long)h;
    }

    private static String stringField(Object o, String srg, String mcp) {
        Object v = field(o, srg, mcp);
        return v == null ? null : String.valueOf(v);
    }

    private static int intField(Object o, String srg, String mcp, int fallback) {
        Object v = field(o, srg, mcp);
        return v instanceof Number ? ((Number)v).intValue() : fallback;
    }

    private static int collectionSize(Object o, String srg, String mcp) {
        Object v = field(o, srg, mcp);
        if (v instanceof java.util.Collection) return ((java.util.Collection<?>)v).size();
        if (v instanceof java.util.Map) return ((java.util.Map<?, ?>)v).size();
        return -1;
    }

    private static Object field(Object o, String srg, String mcp) {
        if (o == null) return null;
        for (String name : new String[] {srg, mcp}) {
            for (Class<?> c = o.getClass(); c != null; c = c.getSuperclass()) {
                try {
                    Field f = c.getDeclaredField(name);
                    f.setAccessible(true);
                    return f.get(o);
                } catch (Throwable ignored) {}
            }
        }
        return null;
    }

    private static final class State {
        final int sequence;
        final String path;
        final int configuredMip;
        final long startedNanos = System.nanoTime();
        final int[] mipLengthHistogram = new int[16];
        final Set<Long> rectangles = new HashSet<Long>(32768);
        int textureId = -1;
        int allocatedMip = -1;
        int atlasWidth;
        int atlasHeight;
        float anisotropic;
        long allocatedArea = -1L;
        boolean armed;
        int allocationCalls;
        int allocationResets;
        int uploadCalls;
        int singleLevelCalls;
        int multiLevelCalls;
        int nullMipArrays;
        int emptyMipArrays;
        int mipLengthOverflow;
        int maxMipArrayLength;
        long presentLevels;
        long nullLevels;
        long shortLevelArrays;
        int blurCalls;
        int clampCalls;
        int invalidGeometry;
        int outOfBounds;
        int duplicateRects;
        long basePixels;
        long levelPixels;
        int minSpriteWidth = Integer.MAX_VALUE;
        int minSpriteHeight = Integer.MAX_VALUE;
        int maxSpriteWidth;
        int maxSpriteHeight;
        long observerNanos;

        State(int sequence, String path, int configuredMip) {
            this.sequence = sequence;
            this.path = path;
            this.configuredMip = configuredMip;
        }

        void resetUploads() {
            uploadCalls = singleLevelCalls = multiLevelCalls = nullMipArrays = emptyMipArrays = 0;
            mipLengthOverflow = maxMipArrayLength = blurCalls = clampCalls = 0;
            invalidGeometry = outOfBounds = duplicateRects = 0;
            presentLevels = nullLevels = shortLevelArrays = basePixels = levelPixels = 0L;
            minSpriteWidth = minSpriteHeight = Integer.MAX_VALUE;
            maxSpriteWidth = maxSpriteHeight = 0;
            rectangles.clear();
            java.util.Arrays.fill(mipLengthHistogram, 0);
        }
    }
}
