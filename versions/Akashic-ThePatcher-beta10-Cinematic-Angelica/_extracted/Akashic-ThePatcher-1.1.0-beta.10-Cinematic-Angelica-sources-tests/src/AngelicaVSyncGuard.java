package com.akashic.thepatcher.module.cinematic;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Fail-soft wrapper for LWJGL2 swap interval changes from Angelica video options.
 */
public final class AngelicaVSyncGuard {
    private static final int GL_NO_ERROR = 0;
    private static volatile boolean initialized;
    private static Method setVSyncEnabled;
    private static Method glGetError;

    private AngelicaVSyncGuard() {}

    public static void setVSyncEnabled(boolean enabled) {
        ensureInitialized();
        if (setVSyncEnabled == null) {
            System.err.println(
                "[AkashicThePatcher/Angelica-VSync] LWJGL methods unavailable; request ignored");
            return;
        }

        final int pending = drainErrors();
        if (pending != GL_NO_ERROR) {
            System.err.println(
                "[AkashicThePatcher/Angelica-VSync] drained pending GL error before VSync: "
                + pending);
        }

        try {            setVSyncEnabled.invoke(null, Boolean.valueOf(enabled));
        } catch (InvocationTargetException wrapped) {
            final Throwable cause = wrapped.getCause() == null ? wrapped : wrapped.getCause();
            final int after = drainErrors();
            System.err.println(
                "[AkashicThePatcher/Angelica-VSync] VSync request failed soft"
                + " enabled=" + enabled
                + " glErrorAfter=" + after
                + " cause=" + cause);
        } catch (Throwable failure) {
            final int after = drainErrors();
            System.err.println(
                "[AkashicThePatcher/Angelica-VSync] VSync reflection failed soft"
                + " enabled=" + enabled
                + " glErrorAfter=" + after
                + " cause=" + failure);
        }
    }

    private static int drainErrors() {
        if (glGetError == null) {
            return GL_NO_ERROR;
        }
        int first = GL_NO_ERROR;
        try {
            for (int i = 0; i < 16; i++) {
                final Object value = glGetError.invoke(null);
                final int error = ((Integer) value).intValue();
                if (error == GL_NO_ERROR) {
                    break;
                }
                if (first == GL_NO_ERROR) {
                    first = error;
                }            }
        } catch (Throwable failure) {
            return first;
        }
        return first;
    }

    private static void ensureInitialized() {
        if (initialized) {
            return;
        }
        synchronized (AngelicaVSyncGuard.class) {
            if (initialized) {
                return;
            }
            try {
                final ClassLoader loader = AngelicaVSyncGuard.class.getClassLoader();
                final Class<?> display = Class.forName(
                    "org.lwjgl.opengl.Display", true, loader);
                final Class<?> gl11 = Class.forName(
                    "org.lwjgl.opengl.GL11", true, loader);
                setVSyncEnabled =
                    display.getMethod("setVSyncEnabled", Boolean.TYPE);
                glGetError = gl11.getMethod("glGetError");
            } catch (Throwable failure) {
                System.err.println(
                    "[AkashicThePatcher/Angelica-VSync] initialization failed: "
                    + failure);
                setVSyncEnabled = null;
                glGetError = null;
            } finally {
                initialized = true;
            }
        }
    }
}
