package dev.akashic.asynciconcompat.profile;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class TextureUploadProfiler {

    private static final Logger LOG =
        LogManager.getLogger("AkashicAsyncCompat/UploadProfiler");

    private static final int MAX_LEVELS = 16;

    private static boolean active;
    private static String path;
    private static int currentLevel = -1;
    private static long uploadSubStartNanos;

    private static long uploadSubNanos;
    private static long stateNanos;
    private static long copyNanos;
    private static long glNanos;
    private static long mipmapGenerateNanos;
    private static long mipmapGenerateStartNanos;

    private static int uploadSubCalls;
    private static int stateCalls;
    private static int copyCalls;
    private static int glCalls;
    private static int mipmapChecks;
    private static int mipmapGenerateCalls;

    private static long copyInts;
    private static long glBytes;

    private static final long[] levelSubNanos = new long[MAX_LEVELS];
    private static final long[] levelCopyNanos = new long[MAX_LEVELS];
    private static final long[] levelGlNanos = new long[MAX_LEVELS];
    private static final int[] levelGlCalls = new int[MAX_LEVELS];
    private static final long[] levelBytes = new long[MAX_LEVELS];

    private TextureUploadProfiler() {}

    public static void begin(String atlasPath) {
        active = true;
        path = atlasPath;
        currentLevel = -1;
        uploadSubStartNanos = 0L;

        uploadSubNanos = 0L;
        stateNanos = 0L;
        copyNanos = 0L;
        glNanos = 0L;
        mipmapGenerateNanos = 0L;
        mipmapGenerateStartNanos = 0L;

        uploadSubCalls = 0;
        stateCalls = 0;
        copyCalls = 0;
        glCalls = 0;
        mipmapChecks = 0;
        mipmapGenerateCalls = 0;

        copyInts = 0L;
        glBytes = 0L;

        for (int i = 0; i < MAX_LEVELS; i++) {
            levelSubNanos[i] = 0L;
            levelCopyNanos[i] = 0L;
            levelGlNanos[i] = 0L;
            levelGlCalls[i] = 0;
            levelBytes[i] = 0L;
        }
    }

    public static void finish() {
        if (!active) return;

        long accounted = stateNanos + copyNanos + glNanos;
        long overhead = Math.max(0L, uploadSubNanos - accounted);

        LOG.info(
            "UploadBreakdown path={} sub={}ms/{} state={}ms/{} copy={}ms/{} ints={} gl={}ms/{} bytes={} mipChecks={} mipGen={} mipGenMs={} overhead={}ms levels={}",
            path,
            millis(uploadSubNanos),
            uploadSubCalls,
            millis(stateNanos),
            stateCalls,
            millis(copyNanos),
            copyCalls,
            copyInts,
            millis(glNanos),
            glCalls,
            glBytes,
            mipmapChecks,
            mipmapGenerateCalls,
            millis(mipmapGenerateNanos),
            millis(overhead),
            levelSummary());

        active = false;
        path = null;
        currentLevel = -1;
    }

    public static boolean isActive() {
        return active;
    }

    public static void uploadSubEnter(int level) {
        if (!active) return;
        currentLevel = level;
        uploadSubStartNanos = System.nanoTime();
        uploadSubCalls++;
    }

    public static void uploadSubExit() {
        if (!active || uploadSubStartNanos == 0L) return;

        long elapsed = System.nanoTime() - uploadSubStartNanos;
        uploadSubNanos += elapsed;
        if (validLevel(currentLevel)) {
            levelSubNanos[currentLevel] += elapsed;
        }

        uploadSubStartNanos = 0L;
        currentLevel = -1;
    }

    public static long start() {
        return active ? System.nanoTime() : 0L;
    }

    public static void recordState(long started) {
        if (!active || started == 0L) return;
        stateNanos += System.nanoTime() - started;
        stateCalls++;
    }

    public static void recordCopy(long started, int length) {
        if (!active || started == 0L) return;

        long elapsed = System.nanoTime() - started;
        copyNanos += elapsed;
        copyCalls++;
        copyInts += Math.max(0, length);

        if (validLevel(currentLevel)) {
            levelCopyNanos[currentLevel] += elapsed;
        }
    }

    public static void recordGl(long started, int width, int height) {
        if (!active || started == 0L) return;

        long elapsed = System.nanoTime() - started;
        long bytes = Math.max(0L, (long) width)
            * Math.max(0L, (long) height)
            * 4L;

        glNanos += elapsed;
        glCalls++;
        glBytes += bytes;

        if (validLevel(currentLevel)) {
            levelGlNanos[currentLevel] += elapsed;
            levelGlCalls[currentLevel]++;
            levelBytes[currentLevel] += bytes;
        }
    }


    public static void recordMipmapCheck() {
        if (!active) return;
        mipmapChecks++;
    }

    public static void mipmapGenerateEnter() {
        if (!active) return;
        mipmapGenerateCalls++;
        mipmapGenerateStartNanos = System.nanoTime();
    }

    public static void mipmapGenerateExit() {
        if (!active || mipmapGenerateStartNanos == 0L) return;
        mipmapGenerateNanos += System.nanoTime() - mipmapGenerateStartNanos;
        mipmapGenerateStartNanos = 0L;
    }
    private static String levelSummary() {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < MAX_LEVELS; i++) {
            if (levelGlCalls[i] == 0 && levelSubNanos[i] == 0L) continue;
            if (out.length() > 0) out.append(';');

            out.append('L').append(i)
                .append("{sub=").append(millis(levelSubNanos[i]))
                .append(",copy=").append(millis(levelCopyNanos[i]))
                .append(",gl=").append(millis(levelGlNanos[i]))
                .append(",calls=").append(levelGlCalls[i])
                .append(",bytes=").append(levelBytes[i])
                .append('}');
        }
        return out.toString();
    }

    private static boolean validLevel(int level) {
        return level >= 0 && level < MAX_LEVELS;
    }

    private static long millis(long nanos) {
        return nanos / 1_000_000L;
    }
}