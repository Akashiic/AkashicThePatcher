package dev.akashic.asynciconcompat.graphics;

import java.lang.reflect.Field;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Network-aware Celeritas chunk-readiness policy.
 *
 * <p>The stock Angelica tracker waits for all eight horizontal neighbor chunks unless
 * useVanillaChunkTracking is enabled. That conservative rule is desirable for normal
 * local/modded worlds, but a Velocity/Via translated vanilla connection can deliver the
 * initial remote chunk window in an order which leaves the first render graph incomplete.
 *
 * <p>This class does not guess by host or IP. It consumes the connection type already
 * established by Akashic-ThePatcher's FML connection state machine. Reflection is kept
 * internal to avoid widening the public API of the inherited Backhand patch. Any failure
 * is fail-closed: the configured Angelica behavior is preserved.
 */
public final class ProxyVanillaChunkTrackingPolicy {
    private static final Logger LOG = LogManager.getLogger("AkashicAsyncCompat/ProxyChunkTracking");

    private static final Object INIT_LOCK = new Object();
    private static volatile boolean initialized;
    private static volatile boolean available;

    private static Object backhandLock;
    private static Field configuredField;
    private static Field connectedField;
    private static Field localField;
    private static Field connectionTypeField;
    private static Field generationField;

    private static long lastLoggedGeneration = Long.MIN_VALUE;
    private static Boolean lastLoggedForced;
    private static boolean initFailureLogged;

    private ProxyVanillaChunkTrackingPolicy() {}

    public static boolean useFastTracking() {
        ensureInitialized();
        if (!available || configuredField == null) {
            return false;
        }

        final boolean configured;
        try {
            configured = configuredField.getBoolean(null);
        } catch (Throwable failure) {
            noteFailure("config-read", failure);
            return false;
        }

        if (configured) {
            return true;
        }

        if (backhandLock == null) {
            return false;
        }

        boolean connected;
        boolean local;
        String type;
        long generation;

        try {
            synchronized (backhandLock) {
                connected = connectedField.getBoolean(null);
                local = localField.getBoolean(null);
                Object rawType = connectionTypeField.get(null);
                type = rawType == null ? "UNKNOWN" : String.valueOf(rawType);
                generation = generationField.getLong(null);
            }
        } catch (Throwable failure) {
            noteFailure("state-read", failure);
            return false;
        }

        final boolean forced = connected
            && !local
            && ("VANILLA".equals(type) || "BUKKIT".equals(type));

        if (generation != lastLoggedGeneration
            || lastLoggedForced == null
            || lastLoggedForced.booleanValue() != forced) {
            lastLoggedGeneration = generation;
            lastLoggedForced = Boolean.valueOf(forced);
            LOG.info(
                "ProxyChunkTracking generation={} type={} connected={} local={} configured={} effectiveFast={}",
                Long.valueOf(generation), type, Boolean.valueOf(connected), Boolean.valueOf(local),
                Boolean.valueOf(configured), Boolean.valueOf(forced));
        }

        return forced;
    }

    private static void ensureInitialized() {
        if (initialized) {
            return;
        }

        synchronized (INIT_LOCK) {
            if (initialized) {
                return;
            }

            try {
                ClassLoader loader = ProxyVanillaChunkTrackingPolicy.class.getClassLoader();
                Class<?> config = Class.forName(
                    "com.gtnewhorizons.angelica.config.AngelicaConfig",
                    false,
                    loader);
                Field configured = config.getDeclaredField("useVanillaChunkTracking");
                configured.setAccessible(true);

                Class<?> hooks = Class.forName(
                    "com.akashic.forbiddenbackhand.core.BackhandGuardHooks",
                    false,
                    loader);

                Field lock = hooks.getDeclaredField("LOCK");
                Field connected = hooks.getDeclaredField("connected");
                Field local = hooks.getDeclaredField("local");
                Field type = hooks.getDeclaredField("connectionType");
                Field generation = hooks.getDeclaredField("generation");

                lock.setAccessible(true);
                connected.setAccessible(true);
                local.setAccessible(true);
                type.setAccessible(true);
                generation.setAccessible(true);

                Object resolvedLock = lock.get(null);
                if (resolvedLock == null) {
                    throw new IllegalStateException("BackhandGuardHooks.LOCK was null");
                }

                configuredField = configured;
                backhandLock = resolvedLock;
                connectedField = connected;
                localField = local;
                connectionTypeField = type;
                generationField = generation;
                available = true;

                LOG.info("ProxyChunkTracking bridge ready; failClosed=true");
            } catch (Throwable failure) {
                available = false;
                noteFailure("init", failure);
            } finally {
                initialized = true;
            }
        }
    }

    private static void noteFailure(String stage, Throwable failure) {
        if (initFailureLogged) {
            return;
        }
        initFailureLogged = true;
        LOG.warn(
            "ProxyChunkTracking {} failed; preserving configured Angelica behavior: {}: {}",
            stage,
            failure.getClass().getName(),
            String.valueOf(failure.getMessage()));
    }
}
