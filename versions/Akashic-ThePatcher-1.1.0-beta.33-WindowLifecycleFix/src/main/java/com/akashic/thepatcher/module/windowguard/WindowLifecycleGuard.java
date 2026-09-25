package com.akashic.thepatcher.module.windowguard;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.LWJGLException;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;

public final class WindowLifecycleGuard {

    private static final Logger LOG =
        LogManager.getLogger("Akashic-ThePatcher/WindowGuard");

    private static int startupSelections;
    private static int runtimeTransitions;
    private static int failures;

    private WindowLifecycleGuard() {}

    public static void setDisplayMode(DisplayMode mode)
        throws LWJGLException {

        if (mode == null) {
            throw new NullPointerException("mode");
        }

        if (!Display.isCreated()) {
            startupSelections++;
            Display.setDisplayMode(mode);
            return;
        }
        final boolean beforeFullscreen = Display.isFullscreen();
        final DisplayMode beforeMode = Display.getDisplayMode();

        try {
            LOG.info(
                "WindowGuard atomic transition begin fromMode={} fromFullscreen={} toMode={} toFullscreenCapable={}",
                beforeMode,
                Boolean.valueOf(beforeFullscreen),
                mode,
                Boolean.valueOf(mode.isFullscreenCapable()));

            Display.setDisplayModeAndFullscreen(mode);
            runtimeTransitions++;

            LOG.info(
                "WindowGuard atomic transition complete created={} fullscreen={} mode={} transitions={} startupSelections={}",
                Boolean.valueOf(Display.isCreated()),
                Boolean.valueOf(Display.isFullscreen()),
                Display.getDisplayMode(),
                Integer.valueOf(runtimeTransitions),
                Integer.valueOf(startupSelections));
        } catch (LWJGLException failure) {
            failures++;
            LOG.error(
                "WindowGuard native transition failed; failures={} fromMode={} requestedMode={}",
                Integer.valueOf(failures),
                beforeMode,
                mode,
                failure);
            throw failure;
        } catch (RuntimeException failure) {
            failures++;
            LOG.error(
                "WindowGuard runtime transition failed; failures={} fromMode={} requestedMode={}",
                Integer.valueOf(failures),
                beforeMode,
                mode,
                failure);
            throw failure;
        }
    }

    static int startupSelectionsForTest() {
        return startupSelections;
    }

    static int runtimeTransitionsForTest() {
        return runtimeTransitions;
    }

    static int failuresForTest() {
        return failures;
    }
}
