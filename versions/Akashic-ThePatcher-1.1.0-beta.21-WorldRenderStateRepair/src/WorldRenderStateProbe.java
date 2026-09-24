package dev.akashic.asynciconcompat.graphics;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import net.minecraft.client.Minecraft;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.backend.BackendManager;
import com.gtnewhorizons.angelica.glsm.backend.RenderBackend;
import com.gtnewhorizons.angelica.glsm.states.BlendState;
import com.gtnewhorizons.angelica.glsm.states.Color4;
import com.gtnewhorizons.angelica.glsm.states.ColorMask;
import com.gtnewhorizons.angelica.glsm.states.DepthState;
import com.gtnewhorizons.angelica.glsm.stacks.TextureBindingStack;
import com.gtnewhorizons.angelica.glsm.stacks.TextureUnitBooleanStateStack;

public final class WorldRenderStateProbe {

    private static final Logger LOG =
        LogManager.getLogger("AkashicAsyncCompat/WorldGLState");

    private static final int GL_ALPHA_TEST = 3008;
    private static final int GL_ALPHA_TEST_FUNC = 3009;
    private static final int GL_ALPHA_TEST_REF = 3010;
    private static final int GL_BLEND = 3042;
    private static final int GL_CULL_FACE = 2884;
    private static final int GL_DEPTH_TEST = 2929;
    private static final int GL_DEPTH_WRITEMASK = 2930;
    private static final int GL_DEPTH_FUNC = 2932;
    private static final int GL_COLOR_WRITEMASK = 3107;
    private static final int GL_CURRENT_COLOR = 2816;
    private static final int GL_TEXTURE_2D = 3553;
    private static final int GL_TEXTURE0 = 33984;
    private static final int GL_ACTIVE_TEXTURE = 34016;
    private static final int GL_TEXTURE_BINDING_2D = 32873;
    private static final int GL_BLEND_COLOR = 32773;
    private static final int GL_BLEND_EQUATION_RGB = 32777;
    private static final int GL_BLEND_DST_RGB = 32968;
    private static final int GL_BLEND_SRC_RGB = 32969;
    private static final int GL_BLEND_DST_ALPHA = 32970;
    private static final int GL_BLEND_SRC_ALPHA = 32971;
    private static final int GL_BLEND_EQUATION_ALPHA = 34877;

    private static final int MAX_SAMPLES_PER_PASS = 12;
    private static final float EPSILON = 0.0005F;

    private static Object lastWorld;
    private static int pass0Samples;
    private static int pass1Samples;
    private static int repairs;
    private static int mismatches;

    private WorldRenderStateProbe() {}

    public static void beforeTerrainPass(int pass) {
        if (!Boolean.parseBoolean(
            System.getProperty("akashic.worldRenderStateProbe", "true"))) {
            return;
        }

        if (pass != 0 && pass != 1) return;

        final Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.theWorld == null) return;

        if (mc.theWorld != lastWorld) {
            lastWorld = mc.theWorld;
            pass0Samples = 0;
            pass1Samples = 0;
            repairs = 0;
            mismatches = 0;

            LOG.info(
                "WorldGLState new-world repairEnabled={} glsmCache={} backend={}",
                repairEnabled(),
                GLStateManager.isCachingEnabled(),
                GLStateManager.getRenderBackendName());
        }

        if (pass == 0) {
            if (pass0Samples >= MAX_SAMPLES_PER_PASS) return;
            pass0Samples++;
        } else {
            if (pass1Samples >= MAX_SAMPLES_PER_PASS) return;
            pass1Samples++;
        }

        inspectAndRepair(pass, pass == 0 ? pass0Samples : pass1Samples);
    }

    private static void inspectAndRepair(int pass, int sample) {
        final RenderBackend backend = BackendManager.RENDER_BACKEND;
        final boolean repair = repairEnabled();
        final StringBuilder diff = new StringBuilder(256);
        int localMismatch = 0;
        int localRepairs = 0;

        try {
            final int cacheActive = GLStateManager.getActiveTextureUnit();
            int actualActive =
                backend.getInteger(GL_ACTIVE_TEXTURE) - GL_TEXTURE0;

            if (actualActive != cacheActive) {
                localMismatch++;
                append(
                    diff,
                    "activeTexture",
                    Integer.toString(cacheActive),
                    Integer.toString(actualActive));

                if (repair) {
                    backend.activeTexture(GL_TEXTURE0 + cacheActive);
                    actualActive = cacheActive;
                    localRepairs++;
                }
            }

            if (actualActive == cacheActive) {
                final TextureUnitBooleanStateStack textureState =
                    GLStateManager.getTextures()
                        .getTextureUnitStates(cacheActive);
                final TextureBindingStack textureBinding =
                    GLStateManager.getTextures()
                        .getTextureUnitBindings(cacheActive);

                final boolean cacheTexture =
                    textureState.isEnabled();
                final boolean actualTexture =
                    backend.getBoolean(GL_TEXTURE_2D);

                if (cacheTexture != actualTexture) {
                    localMismatch++;
                    append(
                        diff,
                        "texture2D",
                        Boolean.toString(cacheTexture),
                        Boolean.toString(actualTexture));

                    if (repair) {
                        setCapability(
                            backend,
                            GL_TEXTURE_2D,
                            cacheTexture);
                        localRepairs++;
                    }
                }

                final int cacheBinding =
                    textureBinding.getBinding();
                final int actualBinding =
                    backend.getInteger(GL_TEXTURE_BINDING_2D);

                if (cacheBinding != actualBinding) {
                    localMismatch++;
                    append(
                        diff,
                        "textureBinding2D",
                        Integer.toString(cacheBinding),
                        Integer.toString(actualBinding));

                    if (repair) {
                        backend.bindTexture(
                            GL_TEXTURE_2D,
                            cacheBinding);
                        localRepairs++;
                    }
                }
            }

            final boolean cacheBlend =
                GLStateManager.getBlendMode().isEnabled();
            final boolean actualBlend =
                backend.getBoolean(GL_BLEND);
            if (cacheBlend != actualBlend) {
                localMismatch++;
                append(
                    diff,
                    "blend",
                    Boolean.toString(cacheBlend),
                    Boolean.toString(actualBlend));

                if (repair) {
                    setCapability(backend, GL_BLEND, cacheBlend);
                    localRepairs++;
                }
            }

            final boolean cacheDepthTest =
                GLStateManager.getDepthTest().isEnabled();
            final boolean actualDepthTest =
                backend.getBoolean(GL_DEPTH_TEST);
            if (cacheDepthTest != actualDepthTest) {
                localMismatch++;
                append(
                    diff,
                    "depthTest",
                    Boolean.toString(cacheDepthTest),
                    Boolean.toString(actualDepthTest));

                if (repair) {
                    setCapability(
                        backend,
                        GL_DEPTH_TEST,
                        cacheDepthTest);
                    localRepairs++;
                }
            }

            final boolean cacheAlphaTest =
                GLStateManager.getAlphaTest().isEnabled();
            final boolean actualAlphaTest =
                backend.getBoolean(GL_ALPHA_TEST);
            if (cacheAlphaTest != actualAlphaTest) {
                localMismatch++;
                append(
                    diff,
                    "alphaTest",
                    Boolean.toString(cacheAlphaTest),
                    Boolean.toString(actualAlphaTest));

                if (repair) {
                    setCapability(
                        backend,
                        GL_ALPHA_TEST,
                        cacheAlphaTest);
                    localRepairs++;
                }
            }

            final boolean cacheCull =
                GLStateManager.getCullState().isEnabled();
            final boolean actualCull =
                backend.getBoolean(GL_CULL_FACE);
            if (cacheCull != actualCull) {
                localMismatch++;
                append(
                    diff,
                    "cullFace",
                    Boolean.toString(cacheCull),
                    Boolean.toString(actualCull));

                if (repair) {
                    setCapability(
                        backend,
                        GL_CULL_FACE,
                        cacheCull);
                    localRepairs++;
                }
            }

            final DepthState depth =
                GLStateManager.getDepthState();

            final boolean cacheDepthMask =
                depth.isEnabled();
            final boolean actualDepthMask =
                backend.getBoolean(GL_DEPTH_WRITEMASK);
            if (cacheDepthMask != actualDepthMask) {
                localMismatch++;
                append(
                    diff,
                    "depthMask",
                    Boolean.toString(cacheDepthMask),
                    Boolean.toString(actualDepthMask));

                if (repair) {
                    backend.depthMask(cacheDepthMask);
                    localRepairs++;
                }
            }

            final int cacheDepthFunc =
                depth.getFunc();
            final int actualDepthFunc =
                backend.getInteger(GL_DEPTH_FUNC);
            if (cacheDepthFunc != actualDepthFunc) {
                localMismatch++;
                append(
                    diff,
                    "depthFunc",
                    Integer.toString(cacheDepthFunc),
                    Integer.toString(actualDepthFunc));

                if (repair) {
                    backend.depthFunc(cacheDepthFunc);
                    localRepairs++;
                }
            }

            final ColorMask colorMask =
                GLStateManager.getColorMask();
            final ByteBuffer actualColorMask =
                directBytes(4);
            backend.getBoolean(
                GL_COLOR_WRITEMASK,
                actualColorMask);

            final boolean actualRed =
                actualColorMask.get(0) != 0;
            final boolean actualGreen =
                actualColorMask.get(1) != 0;
            final boolean actualBlue =
                actualColorMask.get(2) != 0;
            final boolean actualAlpha =
                actualColorMask.get(3) != 0;

            if (colorMask.red != actualRed
                || colorMask.green != actualGreen
                || colorMask.blue != actualBlue
                || colorMask.alpha != actualAlpha) {

                localMismatch++;
                append(
                    diff,
                    "colorMask",
                    mask(
                        colorMask.red,
                        colorMask.green,
                        colorMask.blue,
                        colorMask.alpha),
                    mask(
                        actualRed,
                        actualGreen,
                        actualBlue,
                        actualAlpha));

                if (repair) {
                    backend.colorMask(
                        colorMask.red,
                        colorMask.green,
                        colorMask.blue,
                        colorMask.alpha);
                    localRepairs++;
                }
            }

            final BlendState blend =
                GLStateManager.getBlendState();

            final int actualSrcRgb =
                backend.getInteger(GL_BLEND_SRC_RGB);
            final int actualDstRgb =
                backend.getInteger(GL_BLEND_DST_RGB);
            final int actualSrcAlpha =
                backend.getInteger(GL_BLEND_SRC_ALPHA);
            final int actualDstAlpha =
                backend.getInteger(GL_BLEND_DST_ALPHA);

            if (blend.getSrcRgb() != actualSrcRgb
                || blend.getDstRgb() != actualDstRgb
                || blend.getSrcAlpha() != actualSrcAlpha
                || blend.getDstAlpha() != actualDstAlpha) {

                localMismatch++;
                append(
                    diff,
                    "blendFunc",
                    blendFunc(
                        blend.getSrcRgb(),
                        blend.getDstRgb(),
                        blend.getSrcAlpha(),
                        blend.getDstAlpha()),
                    blendFunc(
                        actualSrcRgb,
                        actualDstRgb,
                        actualSrcAlpha,
                        actualDstAlpha));

                if (repair) {
                    backend.blendFuncSeparate(
                        blend.getSrcRgb(),
                        blend.getDstRgb(),
                        blend.getSrcAlpha(),
                        blend.getDstAlpha());
                    localRepairs++;
                }
            }

            final int actualEquationRgb =
                backend.getInteger(GL_BLEND_EQUATION_RGB);
            final int actualEquationAlpha =
                backend.getInteger(GL_BLEND_EQUATION_ALPHA);

            if (blend.getEquationRgb() != actualEquationRgb
                || blend.getEquationAlpha() != actualEquationAlpha) {

                localMismatch++;
                append(
                    diff,
                    "blendEquation",
                    blend.getEquationRgb()
                        + "/"
                        + blend.getEquationAlpha(),
                    actualEquationRgb
                        + "/"
                        + actualEquationAlpha);

                if (repair) {
                    backend.blendEquationSeparate(
                        blend.getEquationRgb(),
                        blend.getEquationAlpha());
                    localRepairs++;
                }
            }

            final FloatBuffer actualBlendColor =
                directFloats(4);
            backend.getFloat(
                GL_BLEND_COLOR,
                actualBlendColor);

            if (!same(
                    blend.getBlendColorR(),
                    actualBlendColor.get(0))
                || !same(
                    blend.getBlendColorG(),
                    actualBlendColor.get(1))
                || !same(
                    blend.getBlendColorB(),
                    actualBlendColor.get(2))
                || !same(
                    blend.getBlendColorA(),
                    actualBlendColor.get(3))) {

                localMismatch++;
                append(
                    diff,
                    "blendColor",
                    color(
                        blend.getBlendColorR(),
                        blend.getBlendColorG(),
                        blend.getBlendColorB(),
                        blend.getBlendColorA()),
                    color(
                        actualBlendColor.get(0),
                        actualBlendColor.get(1),
                        actualBlendColor.get(2),
                        actualBlendColor.get(3)));

                if (repair) {
                    backend.blendColor(
                        blend.getBlendColorR(),
                        blend.getBlendColorG(),
                        blend.getBlendColorB(),
                        blend.getBlendColorA());
                    localRepairs++;
                }
            }

            // Diagnostic-only states without a safe backend setter.
            final Color4 cacheColor =
                GLStateManager.getColor();
            final FloatBuffer actualColor =
                directFloats(4);
            backend.getFloat(
                GL_CURRENT_COLOR,
                actualColor);

            if (!same(
                    cacheColor.getRed(),
                    actualColor.get(0))
                || !same(
                    cacheColor.getGreen(),
                    actualColor.get(1))
                || !same(
                    cacheColor.getBlue(),
                    actualColor.get(2))
                || !same(
                    cacheColor.getAlpha(),
                    actualColor.get(3))) {

                localMismatch++;
                append(
                    diff,
                    "currentColor",
                    color(
                        cacheColor.getRed(),
                        cacheColor.getGreen(),
                        cacheColor.getBlue(),
                        cacheColor.getAlpha()),
                    color(
                        actualColor.get(0),
                        actualColor.get(1),
                        actualColor.get(2),
                        actualColor.get(3)));
            }

            final int cacheAlphaFunc =
                GLStateManager.getAlphaState()
                    .getFunction();
            final float cacheAlphaRef =
                GLStateManager.getAlphaState()
                    .getReference();
            final int actualAlphaFunc =
                backend.getInteger(GL_ALPHA_TEST_FUNC);
            final float actualAlphaRef =
                backend.getFloat(GL_ALPHA_TEST_REF);

            if (cacheAlphaFunc != actualAlphaFunc
                || !same(cacheAlphaRef, actualAlphaRef)) {

                localMismatch++;
                append(
                    diff,
                    "alphaFunc",
                    cacheAlphaFunc
                        + "@"
                        + cacheAlphaRef,
                    actualAlphaFunc
                        + "@"
                        + actualAlphaRef);
            }

            mismatches += localMismatch;
            repairs += localRepairs;

            LOG.info(
                "WorldGLState pass={} sample={} cache={} mismatches={} repaired={} totalMismatch={} totalRepairs={} diff=[{}]",
                pass,
                sample,
                GLStateManager.isCachingEnabled(),
                localMismatch,
                localRepairs,
                mismatches,
                repairs,
                diff.toString());
        } catch (Throwable failure) {
            LOG.warn(
                "WorldGLState probe failed closed at pass={} sample={}; disabling probe",
                pass,
                sample,
                failure);
            System.setProperty(
                "akashic.worldRenderStateProbe",
                "false");
        }
    }

    private static boolean repairEnabled() {
        return Boolean.parseBoolean(
            System.getProperty(
                "akashic.worldRenderStateRepair",
                "true"));
    }

    private static void setCapability(
        RenderBackend backend,
        int capability,
        boolean enabled) {

        if (enabled) {
            backend.enable(capability);
        } else {
            backend.disable(capability);
        }
    }

    private static ByteBuffer directBytes(int count) {
        return ByteBuffer.allocateDirect(count)
            .order(ByteOrder.nativeOrder());
    }

    private static FloatBuffer directFloats(int count) {
        return ByteBuffer
            .allocateDirect(count * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer();
    }

    private static boolean same(float left, float right) {
        return Math.abs(left - right) <= EPSILON;
    }

    private static void append(
        StringBuilder builder,
        String name,
        String expected,
        String actual) {

        if (builder.length() > 0) {
            builder.append("; ");
        }

        builder
            .append(name)
            .append(":cache=")
            .append(expected)
            .append(",gl=")
            .append(actual);
    }

    private static String mask(
        boolean red,
        boolean green,
        boolean blue,
        boolean alpha) {

        return (red ? "1" : "0")
            + (green ? "1" : "0")
            + (blue ? "1" : "0")
            + (alpha ? "1" : "0");
    }

    private static String blendFunc(
        int srcRgb,
        int dstRgb,
        int srcAlpha,
        int dstAlpha) {

        return srcRgb
            + "/"
            + dstRgb
            + "/"
            + srcAlpha
            + "/"
            + dstAlpha;
    }

    private static String color(
        float red,
        float green,
        float blue,
        float alpha) {

        return red
            + "/"
            + green
            + "/"
            + blue
            + "/"
            + alpha;
    }
}