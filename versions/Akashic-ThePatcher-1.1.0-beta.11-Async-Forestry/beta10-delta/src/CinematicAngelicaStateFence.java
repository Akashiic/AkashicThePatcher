package com.akashic.thepatcher.module.cinematic;

import java.lang.reflect.Method;

/**
 * Repairs the Angelica GLSM/backend boundary after Akashic Cinematic RawScope.
 *
 * Uses reflection so ThePatcher remains loadable without Angelica installed.
 */
public final class CinematicAngelicaStateFence {
    private static final int GL_LIGHTING = 2896;
    private static final int GL_TEXTURE_2D = 3553;
    private static final int GL_TEXTURE0 = 33984;
    private static final int GL_TEXTURE1 = 33985;

    private static volatile boolean initialized;
    private static volatile boolean available;
    private static volatile boolean logged;
    private static Method replay;
    private static Method activeTexture;
    private static Method enable;
    private static Method disable;
    private static Method cachedIsEnabled;
    private static Method rawIsEnabled;

    private CinematicAngelicaStateFence() {}

    public static void repair() {
        if (Boolean.getBoolean("akashic.cinematicAngelicaStateFence.disable")) {
            return;
        }        ensureInitialized();
        if (!available) {
            return;
        }

        try {
            final boolean cachedBefore = bool(cachedIsEnabled.invoke(null, GL_TEXTURE_2D));
            final boolean rawBefore = bool(rawIsEnabled.invoke(null, GL_TEXTURE_2D));

            // First force whatever GLSM currently believes back into the driver.
            replay.invoke(null);

            // Match the baseline Angelica itself uses before GUI/HUD rendering.
            disable.invoke(null, GL_LIGHTING);
            activeTexture.invoke(null, GL_TEXTURE1);
            disable.invoke(null, GL_TEXTURE_2D);
            activeTexture.invoke(null, GL_TEXTURE0);
            enable.invoke(null, GL_TEXTURE_2D);

            // Do not trust dirty-state elision after a large attrib scope:
            // replay once more so backend and cache converge unconditionally.
            replay.invoke(null);

            if (!logged || cachedBefore != rawBefore) {
                final boolean cachedAfter =
                    bool(cachedIsEnabled.invoke(null, GL_TEXTURE_2D));
                final boolean rawAfter =
                    bool(rawIsEnabled.invoke(null, GL_TEXTURE_2D));
                System.out.println(
                    "[AkashicThePatcher/Cinematic-Angelica] state fence"
                    + " texture2D cache/driver "
                    + cachedBefore + "/" + rawBefore
                    + " -> " + cachedAfter + "/" + rawAfter);
                logged = true;
            }        } catch (Throwable failure) {
            if (!logged) {
                System.err.println(
                    "[AkashicThePatcher/Cinematic-Angelica] state fence failed soft: "
                    + failure);
                logged = true;
            }
        }
    }

    private static boolean bool(Object value) {
        return Boolean.TRUE.equals(value);
    }

    private static void ensureInitialized() {
        if (initialized) {
            return;
        }
        synchronized (CinematicAngelicaStateFence.class) {
            if (initialized) {
                return;
            }
            try {
                final ClassLoader loader =
                    CinematicAngelicaStateFence.class.getClassLoader();
                final Class<?> glsm = Class.forName(
                    "com.gtnewhorizons.angelica.glsm.GLStateManager",
                    true,
                    loader);
                final Class<?> gl11 = Class.forName(
                    "org.lwjgl.opengl.GL11",
                    true,
                    loader);                replay = glsm.getMethod("replayStateToBackend");
                activeTexture = glsm.getMethod("glActiveTexture", Integer.TYPE);
                enable = glsm.getMethod("glEnable", Integer.TYPE);
                disable = glsm.getMethod("glDisable", Integer.TYPE);
                cachedIsEnabled = glsm.getMethod("glIsEnabled", Integer.TYPE);

                // Reflection intentionally bypasses Angelica's bytecode redirector
                // here so this query observes the real LWJGL driver state.
                rawIsEnabled = gl11.getMethod("glIsEnabled", Integer.TYPE);
                available = true;
            } catch (Throwable failure) {
                available = false;
                System.err.println(
                    "[AkashicThePatcher/Cinematic-Angelica] state fence unavailable: "
                    + failure);
            } finally {
                initialized = true;
            }
        }
    }
}
