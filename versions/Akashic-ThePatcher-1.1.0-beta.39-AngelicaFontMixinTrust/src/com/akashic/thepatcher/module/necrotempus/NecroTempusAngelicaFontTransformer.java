package com.akashic.thepatcher.module.necrotempus;

import java.io.IOException;
import java.util.List;

import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraft.launchwrapper.Launch;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.BasicVerifier;

import com.akashic.thepatcher.core.RuntimeSide;
import com.akashic.thepatcher.util.BundleLog;

import makamys.mixingasm.api.MixinSafeTransformer;

/**
 * NecroTempus x Angelica 2.2.24+ font API (beta.38).
 *
 * Angelica 2.2.24 (PR #2165) changed BatchingFontRenderer.pushDrawCmd and
 * FontProvider.getTexture from a ResourceLocation to a GL texture id (int).
 * NecroTempus 2.0.0 and 2.1.0 still target the old API:
 *
 *   BatchingFontRendererMixin  @Shadow pushDrawCmd(IILResourceLocation;Z)V and a
 *                              @Redirect on that call: the shadow is not found, the
 *                              mixin config is "required", so BatchingFontRenderer
 *                              fails to load and the client crashes with
 *                              NoClassDefFoundError in the FontRenderer constructor;
 *   AngelicaGlyphProvider,     getTexture(C)LResourceLocation; does not implement
 *   AngelicaModernFontProvider FontProvider.getTexture(C)I (AbstractMethodError).
 *
 * Mixin reads mixin classes through the LaunchWrapper transformer chain, so the
 * mixin can be rewritten here before Mixin parses it:
 *
 *   mixin     shadow and redirect handler take an int texture; the handler's
 *             null checks on it become == 0 checks; the redirect target names
 *             pushDrawCmd(IIIZ)V;
 *   providers getTexture(C)I is added next to the original method and returns
 *             NecroTempusAngelicaFontHooks.textureId(getTexture(chr)).
 *
 * The value only ever reaches NecroTempus' own pushDrawCmd redirect, which takes
 * over glyph and modern-font draws; Angelica draws nothing with it.
 *
 * Fails closed: nothing changes unless the installed Angelica has the int API,
 * the NecroTempus class has the exact old shape, and the rewritten methods pass
 * the ASM verifier. A class that already has the new API is left alone.
 * Kill-switch: -Dakashic.necroTempusAngelicaFont.disable=true
 *
 * beta.39: marked @MixinSafeTransformer. UniMixins' Mixingasm removes every
 * transformer it does not trust from the chain Mixin runs on mixin classes,
 * so in beta.38 the mixin was never rewritten in a real client. The annotation
 * is retained at runtime and ignored by the JVM when Mixingasm is absent.
 */
@MixinSafeTransformer
public final class NecroTempusAngelicaFontTransformer
    implements IClassTransformer {

    public static final String DISABLE_PROPERTY =
        "akashic.necroTempusAngelicaFont.disable";

    static final String MIXIN =
        "io.github.cruciblemc.necrotempus.modules.mixin.mixins.angelica.BatchingFontRendererMixin";
    static final String GLYPH_PROVIDER =
        "io.github.cruciblemc.necrotempus.modules.features.glyphs.compat.angelica.AngelicaGlyphProvider";
    static final String MODERN_FONT_PROVIDER =
        "io.github.cruciblemc.necrotempus.modules.features.modernfonts.compat.angelica.AngelicaModernFontProvider";

    static final String ANGELICA_RENDERER =
        "com.gtnewhorizons.angelica.client.font.BatchingFontRenderer";
    static final String ANGELICA_PROVIDER =
        "com.gtnewhorizons.angelica.client.font.FontProvider";

    private static final String RENDERER_INTERNAL =
        "com/gtnewhorizons/angelica/client/font/BatchingFontRenderer";
    private static final String PROVIDER_INTERNAL =
        "com/gtnewhorizons/angelica/client/font/FontProvider";
    private static final String RESOURCE_LOCATION =
        "net/minecraft/util/ResourceLocation";

    private static final String PUSH_DRAW_CMD = "pushDrawCmd";
    private static final String OLD_PUSH = "(IIL" + RESOURCE_LOCATION + ";Z)V";
    private static final String NEW_PUSH = "(IIIZ)V";
    private static final String OLD_TARGET = "L" + RENDERER_INTERNAL + ";" + PUSH_DRAW_CMD + OLD_PUSH;
    private static final String NEW_TARGET = "L" + RENDERER_INTERNAL + ";" + PUSH_DRAW_CMD + NEW_PUSH;
    private static final String OLD_HANDLER = "(L" + RENDERER_INTERNAL + ";IIL" + RESOURCE_LOCATION + ";Z)V";
    private static final String NEW_HANDLER = "(L" + RENDERER_INTERNAL + ";IIIZ)V";

    private static final String GET_TEXTURE = "getTexture";
    private static final String OLD_GET_TEXTURE = "(C)L" + RESOURCE_LOCATION + ";";
    private static final String NEW_GET_TEXTURE = "(C)I";

    private static final String SHADOW = "Lorg/spongepowered/asm/mixin/Shadow;";
    private static final String REDIRECT = "Lorg/spongepowered/asm/mixin/injection/Redirect;";

    private static final String HOOKS =
        "com/akashic/thepatcher/module/necrotempus/NecroTempusAngelicaFontHooks";
    private static final String HOOK_DESC = "(L" + RESOURCE_LOCATION + ";)I";

    /** Shadow: this, startIdx, idxCount, texture. Handler: this, self, startIdx, idxCount, texture. */
    private static final int SHADOW_TEXTURE_SLOT = 3;
    private static final int HANDLER_TEXTURE_SLOT = 4;

    /** patchMixin/patchProvider result for a class that already has the int API. */
    static final String ALREADY_COMPATIBLE = "already uses GL texture ids";

    private static volatile Boolean angelicaUsesTextureIds;
    private static volatile boolean disabledLogged;
    private static volatile boolean skipLogged;

    @Override
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        final String target = transformedName == null ? name : transformedName;
        final boolean mixin = MIXIN.equals(target);
        if (!mixin && !GLYPH_PROVIDER.equals(target) && !MODERN_FONT_PROVIDER.equals(target)) {
            return bytes;
        }
        if (Boolean.getBoolean(DISABLE_PROPERTY)) {
            if (!disabledLogged) {
                disabledLogged = true;
                BundleLog.info("NecroTempus Angelica font fix DISABLED by -D" + DISABLE_PROPERTY + "=true.");
            }
            return bytes;
        }
        if (!RuntimeSide.isClient() || !angelicaUsesTextureIds()) {
            return bytes;
        }
        try {
            final ClassNode node = new ClassNode();
            new ClassReader(bytes).accept(node, 0);
            final String result = mixin ? patchMixin(node) : patchProvider(node);
            if (ALREADY_COMPATIBLE.equals(result)) {
                return bytes;
            }
            if (result != null) {
                BundleLog.warn("NecroTempus Angelica font fix skipped for " + target + ": " + result
                    + "; original bytecode retained.");
                return bytes;
            }
            final ClassWriter writer = new ClassWriter(0);
            node.accept(writer);
            BundleLog.info("NecroTempus Angelica font fix applied: " + target
                + (mixin ? " (pushDrawCmd shadow/redirect -> " + NEW_PUSH + ")." : " (getTexture" + NEW_GET_TEXTURE + " added)."));
            return writer.toByteArray();
        } catch (ThreadDeath | VirtualMachineError fatal) {
            throw fatal;
        } catch (Throwable error) {
            BundleLog.error("NecroTempus Angelica font fix rejected " + target + "; original bytecode retained.", error);
            return bytes;
        }
    }

    /**
     * True when the installed Angelica passes GL texture ids to pushDrawCmd and
     * FontProvider.getTexture. Reads the class files only; nothing is loaded.
     */
    static boolean angelicaUsesTextureIds() {
        Boolean known = angelicaUsesTextureIds;
        if (known == null) {
            known = probeAngelica();
            angelicaUsesTextureIds = known;
        }
        return known;
    }

    private static boolean probeAngelica() {
        try {
            final ClassNode renderer = readHeader(ANGELICA_RENDERER);
            final ClassNode provider = readHeader(ANGELICA_PROVIDER);
            if (renderer == null || provider == null) {
                return logSkip("Angelica font classes not found");
            }
            if (findMethod(renderer.methods, PUSH_DRAW_CMD, NEW_PUSH) == null
                || findMethod(renderer.methods, PUSH_DRAW_CMD, OLD_PUSH) != null
                || findMethod(provider.methods, GET_TEXTURE, NEW_GET_TEXTURE) == null) {
                return logSkip("installed Angelica does not use GL texture ids in its font API");
            }
            BundleLog.info("NecroTempus Angelica font fix armed: Angelica font API uses GL texture ids.");
            return true;
        } catch (IOException | RuntimeException error) {
            return logSkip("could not read Angelica font classes (" + error + ")");
        }
    }

    private static boolean logSkip(String reason) {
        if (!skipLogged) {
            skipLogged = true;
            BundleLog.info("NecroTempus Angelica font fix not needed: " + reason + ".");
        }
        return false;
    }

    private static ClassNode readHeader(String className) throws IOException {
        final byte[] bytes = Launch.classLoader.getClassBytes(className);
        if (bytes == null) {
            return null;
        }
        final ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return node;
    }

    /** Returns null when the class was patched, otherwise why it was left alone. */
    static String patchMixin(ClassNode node) throws AnalyzerException {
        final MethodNode shadow = findMethod(node.methods, PUSH_DRAW_CMD, OLD_PUSH);
        if (shadow == null) {
            if (findMethod(node.methods, PUSH_DRAW_CMD, NEW_PUSH) != null) {
                return ALREADY_COMPATIBLE;
            }
            return "pushDrawCmd shadow not found";
        }
        if (!hasAnnotation(shadow, SHADOW) || (shadow.access & Opcodes.ACC_STATIC) != 0) {
            return "pushDrawCmd is not an instance @Shadow";
        }
        MethodNode handler = null;
        for (Object methodObject : node.methods) {
            final MethodNode method = (MethodNode) methodObject;
            final AnnotationNode at = redirectAt(method);
            if (at == null || !OLD_TARGET.equals(annotationValue(at, "target"))) {
                continue;
            }
            if (handler != null) {
                return "more than one redirect of pushDrawCmd";
            }
            handler = method;
        }
        if (handler == null || !OLD_HANDLER.equals(handler.desc) || (handler.access & Opcodes.ACC_STATIC) != 0) {
            return "pushDrawCmd redirect handler not found";
        }

        retypeTextureParameter(shadow, SHADOW_TEXTURE_SLOT, NEW_PUSH);
        final String handlerProblem = retypeTextureParameter(handler, HANDLER_TEXTURE_SLOT, NEW_HANDLER);
        if (handlerProblem != null) {
            return handlerProblem;
        }
        setAnnotationValue(redirectAt(handler), "target", NEW_TARGET);
        for (Object methodObject : node.methods) {
            final MethodNode method = (MethodNode) methodObject;
            for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (insn instanceof MethodInsnNode) {
                    final MethodInsnNode call = (MethodInsnNode) insn;
                    if (node.name.equals(call.owner) && PUSH_DRAW_CMD.equals(call.name) && OLD_PUSH.equals(call.desc)) {
                        call.desc = NEW_PUSH;
                    }
                }
            }
        }
        verify(node.name, shadow);
        verify(node.name, handler);
        return null;
    }

    /**
     * Turns the ResourceLocation parameter in the given slot into an int: loads
     * become ILOAD, null checks become zero checks, and the debug/frame entries
     * follow. Anything else done with it is caught by the verifier afterwards.
     */
    private static String retypeTextureParameter(MethodNode method, int slot, String newDesc) {
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (!(insn instanceof VarInsnNode) || ((VarInsnNode) insn).var != slot) {
                continue;
            }
            if (insn.getOpcode() != Opcodes.ALOAD) {
                return "texture parameter is reassigned";
            }
            ((VarInsnNode) insn).setOpcode(Opcodes.ILOAD);
            final AbstractInsnNode next = nextReal(insn);
            if (next != null && next.getOpcode() == Opcodes.IFNULL) {
                ((JumpInsnNode) next).setOpcode(Opcodes.IFEQ);
            } else if (next != null && next.getOpcode() == Opcodes.IFNONNULL) {
                ((JumpInsnNode) next).setOpcode(Opcodes.IFNE);
            }
        }
        if (method.localVariables != null) {
            for (Object localObject : method.localVariables) {
                final LocalVariableNode local = (LocalVariableNode) localObject;
                if (local.index == slot) {
                    local.desc = "I";
                    local.signature = null;
                }
            }
        }
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof FrameNode) {
                retypeFrameEntries(((FrameNode) insn).local);
            }
        }
        method.desc = newDesc;
        method.signature = null;
        return null;
    }

    @SuppressWarnings("unchecked")
    private static void retypeFrameEntries(List<?> frameEntries) {
        final List<Object> entries = (List<Object>) frameEntries;
        if (entries == null) {
            return;
        }
        for (int i = 0; i < entries.size(); i++) {
            if (RESOURCE_LOCATION.equals(entries.get(i))) {
                entries.set(i, Opcodes.INTEGER);
            }
        }
    }

    /** Adds getTexture(C)I, delegating to the class' own getTexture(C)ResourceLocation. */
    @SuppressWarnings("unchecked")
    static String patchProvider(ClassNode node) throws AnalyzerException {
        if (node.interfaces == null || !node.interfaces.contains(PROVIDER_INTERNAL)) {
            return "does not implement FontProvider";
        }
        if (findMethod(node.methods, GET_TEXTURE, NEW_GET_TEXTURE) != null) {
            return ALREADY_COMPATIBLE;
        }
        final MethodNode original = findMethod(node.methods, GET_TEXTURE, OLD_GET_TEXTURE);
        if (original == null || (original.access & (Opcodes.ACC_STATIC | Opcodes.ACC_ABSTRACT)) != 0) {
            return "getTexture(C)ResourceLocation not found";
        }
        final MethodNode bridge = new MethodNode(Opcodes.ACC_PUBLIC, GET_TEXTURE, NEW_GET_TEXTURE, null, null);
        bridge.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        bridge.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        bridge.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, node.name, GET_TEXTURE, OLD_GET_TEXTURE, false));
        bridge.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, "textureId", HOOK_DESC, false));
        bridge.instructions.add(new InsnNode(Opcodes.IRETURN));
        bridge.maxStack = 2;
        bridge.maxLocals = 2;
        verify(node.name, bridge);
        node.methods.add(bridge);
        return null;
    }

    private static void verify(String owner, MethodNode method) throws AnalyzerException {
        new Analyzer(new BasicVerifier()).analyze(owner, method);
    }

    private static MethodNode findMethod(List<?> methods, String name, String desc) {
        for (Object methodObject : methods) {
            final MethodNode method = (MethodNode) methodObject;
            if (name.equals(method.name) && desc.equals(method.desc)) {
                return method;
            }
        }
        return null;
    }

    private static boolean hasAnnotation(MethodNode method, String desc) {
        return findAnnotation(method.visibleAnnotations, desc) != null
            || findAnnotation(method.invisibleAnnotations, desc) != null;
    }

    private static AnnotationNode findAnnotation(List<?> annotations, String desc) {
        if (annotations != null) {
            for (Object annotationObject : annotations) {
                final AnnotationNode annotation = (AnnotationNode) annotationObject;
                if (desc.equals(annotation.desc)) {
                    return annotation;
                }
            }
        }
        return null;
    }

    private static AnnotationNode redirectAt(MethodNode method) {
        AnnotationNode redirect = findAnnotation(method.visibleAnnotations, REDIRECT);
        if (redirect == null) {
            redirect = findAnnotation(method.invisibleAnnotations, REDIRECT);
        }
        if (redirect == null) {
            return null;
        }
        final Object at = annotationValue(redirect, "at");
        return at instanceof AnnotationNode ? (AnnotationNode) at : null;
    }

    private static Object annotationValue(AnnotationNode annotation, String key) {
        if (annotation.values != null) {
            for (int i = 0; i + 1 < annotation.values.size(); i += 2) {
                if (key.equals(annotation.values.get(i))) {
                    return annotation.values.get(i + 1);
                }
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static void setAnnotationValue(AnnotationNode annotation, String key, Object value) {
        for (int i = 0; i + 1 < annotation.values.size(); i += 2) {
            if (key.equals(annotation.values.get(i))) {
                annotation.values.set(i + 1, value);
                return;
            }
        }
        throw new IllegalStateException("annotation value " + key + " missing");
    }

    private static AbstractInsnNode nextReal(AbstractInsnNode insn) {
        AbstractInsnNode next = insn.getNext();
        while (next instanceof LabelNode || next instanceof LineNumberNode || next instanceof FrameNode) {
            next = next.getNext();
        }
        return next;
    }
}
