package com.akashic.thepatcher.module.mps;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.states.AlphaState;
import com.gtnewhorizons.angelica.glsm.states.BlendState;
import com.gtnewhorizons.angelica.glsm.stacks.ColorMaskStack;
import org.lwjgl.opengl.GL11;

/**
 * Narrow compatibility scope for Numina 0.4.1.105 on Angelica 2.2.18.
 *
 * Numina relies on nested glPushAttrib/glPopAttrib scopes with overlapping
 * masks. Angelica 2.2.18 replaced that state stack with a COW implementation.
 * This bridge snapshots only the state families Numina actually mutates and
 * restores them through Angelica's GLStateManager, keeping its cache coherent.
 */
public final class MpsAngelica218StateCompat {
    private static final int MAX_DEPTH = 16;

    private static final int[] ENABLE_CAPS = new int[] {
        GL11.GL_ALPHA_TEST,
        GL11.GL_BLEND,
        GL11.GL_COLOR_MATERIAL,
        GL11.GL_CULL_FACE,
        GL11.GL_DEPTH_TEST,
        GL11.GL_FOG,
        GL11.GL_LIGHTING,
        GL11.GL_LIGHT0,
        GL11.GL_LIGHT1,
        GL11.GL_LIGHT2,
        GL11.GL_LIGHT3,
        GL11.GL_LIGHT4,
        GL11.GL_LIGHT5,
        GL11.GL_LIGHT6,
        GL11.GL_LIGHT7,
        GL11.GL_NORMALIZE,
        GL11.GL_SCISSOR_TEST,
        GL11.GL_TEXTURE_2D
    };

    private static final ThreadLocal<StateStack> STACK = new ThreadLocal<StateStack>();

    private MpsAngelica218StateCompat() {}

    private static StateStack stack() {
        StateStack state = STACK.get();
        if (state == null) {
            state = new StateStack();
            STACK.set(state);
        }
        return state;
    }

    public static void pushAttrib(int mask) {
        final StateStack stack = stack();
        if (stack.depth >= MAX_DEPTH) {
            throw new IllegalStateException(
                "Akashic MPS/Angelica compatibility stack overflow at " + MAX_DEPTH);
        }
        stack.entries[stack.depth++].capture(mask);
    }

    public static void popAttrib() {
        final StateStack stack = stack();
        if (stack.depth <= 0) {
            throw new IllegalStateException("Akashic MPS/Angelica compatibility stack underflow");
        }
        stack.entries[--stack.depth].restore();
    }

    static int depthForTests() {
        final StateStack stack = STACK.get();
        return stack == null ? 0 : stack.depth;
    }

    static void resetForTests() {
        STACK.remove();
    }

    private static void setCap(int cap, boolean enabled) {
        if (enabled) {
            GLStateManager.glEnable(cap);
        } else {
            GLStateManager.glDisable(cap);
        }
    }

    private static final class StateStack {
        final Snapshot[] entries = new Snapshot[MAX_DEPTH];
        int depth;

        StateStack() {
            for (int i = 0; i < entries.length; i++) {
                entries[i] = new Snapshot();
            }
        }
    }

    private static final class Snapshot {
        int mask;

        boolean enableCaptured;
        final boolean[] enableStates = new boolean[ENABLE_CAPS.length];

        boolean colorCaptured;
        boolean alphaEnabled;
        boolean blendEnabled;
        final AlphaState alpha = new AlphaState();
        final BlendState blend = new BlendState();
        boolean colorRed;
        boolean colorGreen;
        boolean colorBlue;
        boolean colorAlpha;

        boolean lightingCaptured;
        boolean lightingEnabled;
        boolean colorMaterialEnabled;
        final boolean[] lightsEnabled = new boolean[8];
        int shadeModel;

        void capture(int requestedMask) {
            mask = requestedMask;
            enableCaptured = (mask & GL11.GL_ENABLE_BIT) != 0;
            colorCaptured = (mask & GL11.GL_COLOR_BUFFER_BIT) != 0;
            lightingCaptured = (mask & GL11.GL_LIGHTING_BIT) != 0;

            if (enableCaptured) {
                for (int i = 0; i < ENABLE_CAPS.length; i++) {
                    enableStates[i] = GLStateManager.glIsEnabled(ENABLE_CAPS[i]);
                }
            }

            if (colorCaptured) {
                alphaEnabled = GLStateManager.glIsEnabled(GL11.GL_ALPHA_TEST);
                blendEnabled = GLStateManager.glIsEnabled(GL11.GL_BLEND);
                GLStateManager.getEffectiveAlphaState(alpha);
                GLStateManager.getEffectiveBlendState(blend);
                final ColorMaskStack colorMask = GLStateManager.getColorMask();
                colorRed = colorMask.red;
                colorGreen = colorMask.green;
                colorBlue = colorMask.blue;
                colorAlpha = colorMask.alpha;
            }

            if (lightingCaptured) {
                lightingEnabled = GLStateManager.glIsEnabled(GL11.GL_LIGHTING);
                colorMaterialEnabled = GLStateManager.glIsEnabled(GL11.GL_COLOR_MATERIAL);
                for (int i = 0; i < lightsEnabled.length; i++) {
                    lightsEnabled[i] = GLStateManager.glIsEnabled(GL11.GL_LIGHT0 + i);
                }
                shadeModel = GLStateManager.getShadeModelState().getValue();
            }
        }

        void restore() {
            if (colorCaptured) {
                GLStateManager.glAlphaFunc(alpha.getFunction(), alpha.getReference());
                GLStateManager.glBlendFuncSeparate(
                    blend.getSrcRgb(), blend.getDstRgb(),
                    blend.getSrcAlpha(), blend.getDstAlpha());
                GLStateManager.glBlendEquationSeparate(
                    blend.getEquationRgb(), blend.getEquationAlpha());
                GLStateManager.glBlendColor(
                    blend.getBlendColorR(), blend.getBlendColorG(),
                    blend.getBlendColorB(), blend.getBlendColorA());
                GLStateManager.glColorMask(colorRed, colorGreen, colorBlue, colorAlpha);
                if (!enableCaptured) {
                    setCap(GL11.GL_ALPHA_TEST, alphaEnabled);
                    setCap(GL11.GL_BLEND, blendEnabled);
                }
            }

            if (lightingCaptured) {
                GLStateManager.glShadeModel(shadeModel);
                if (!enableCaptured) {
                    setCap(GL11.GL_LIGHTING, lightingEnabled);
                    setCap(GL11.GL_COLOR_MATERIAL, colorMaterialEnabled);
                    for (int i = 0; i < lightsEnabled.length; i++) {
                        setCap(GL11.GL_LIGHT0 + i, lightsEnabled[i]);
                    }
                }
            }

            if (enableCaptured) {
                for (int i = 0; i < ENABLE_CAPS.length; i++) {
                    setCap(ENABLE_CAPS[i], enableStates[i]);
                }
            }

            mask = 0;
            enableCaptured = false;
            colorCaptured = false;
            lightingCaptured = false;
        }
    }
}
