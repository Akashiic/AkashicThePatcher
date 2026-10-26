package com.akashic.thepatcher.module.mirai;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraft.launchwrapper.Launch;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
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
 * Akashic: Horizons of The Mirai x Angelica shaders (beta.42).
 *
 * Angelica 2.2.37 carries Iris' Distant Horizons support (net.coderbot.iris.compat.dh):
 * with a shaderpack that has dh_terrain / dh_water programs it binds DH API events,
 * draws the LODs with the pack's programs into the pack's gbuffers and defers the
 * translucent LODs. It only turns this on when
 * Class.forName("com.seibel.distanthorizons.DistantHorizonsTweaker") succeeds, in
 * Iris.onRenderSystemInit (shaderpack load waits for DH's init event) and in
 * DHCompat.run (binds the events). Upstream DH 1.7.10 renamed its coremod to that
 * name on 2026-09-10 ("Rename tweaker for Angelica compat", dfca6541); Mirai 0.1.19
 * is pinned before it (b4abe2f) and still ships
 * com.seibel.distanthorizons.forge17.DistantHorizonsLoadingPlugin. Angelica therefore
 * believes DH is absent: the shaderpack never receives the LODs and DH keeps drawing
 * its own shader-less pass into a pipeline that does not show it.
 *
 * Mirai 0.1.19 also lacks upstream's MixinRenderGlobal.renderDeferredLodsDuringTranslucentSetup
 * (left commented out as "TODO Fix"; re-enabled upstream on 2026-09-10, ef720c8e). With
 * shaders Angelica sets setDeferTransparentRendering(true), so without that call no
 * translucent LOD (distant water) is ever drawn.
 *
 * This transformer, when Mirai is installed and upstream's tweaker class is not:
 *   Iris, DHCompat     the probe's class name becomes Mirai's coremod class name
 *                      (exactly one LDC + Class.forName each, checked on both classes
 *                      before either is changed);
 *   MixinRenderGlobal  gets upstream's @Inject (after EntityRenderer.enableLightmap in
 *                      sortAndRender), calling MiraiShaderBridgeHooks, which runs Mirai's
 *                      RenderHelper.renderDeferredLods() in the translucent pass only while
 *                      DH's renderProxy has deferred transparent rendering (i.e. while
 *                      Angelica's DH support is active). Without shaders nothing changes.
 *
 * The mixin class is read by Mixin through the transformer chain, hence
 * {@link MixinSafeTransformer} (Mixingasm, beta.39). Fails closed: any unexpected shape
 * leaves every class untouched.
 * Kill-switch: -Dakashic.miraiShaderBridge.disable=true
 */
@MixinSafeTransformer
public final class MiraiShaderBridgeTransformer
    implements IClassTransformer {

    public static final String DISABLE_PROPERTY =
        "akashic.miraiShaderBridge.disable";

    static final String IRIS = "net.coderbot.iris.Iris";
    static final String DH_COMPAT = "net.coderbot.iris.compat.dh.DHCompat";
    static final String MIRAI_MIXIN = "com.seibel.distanthorizons.forge17.mixin.MixinRenderGlobal";

    static final String UPSTREAM_TWEAKER = "com.seibel.distanthorizons.DistantHorizonsTweaker";
    static final String MIRAI_PLUGIN = "com.seibel.distanthorizons.forge17.DistantHorizonsLoadingPlugin";
    static final String DH_API = "com.seibel.distanthorizons.api.DhApi";
    static final String MIRAI_RENDER_HELPER = "com.seibel.distanthorizons.forge17.RenderHelper";

    private static final String CLASS = "java/lang/Class";
    private static final String FOR_NAME = "forName";
    private static final String FOR_NAME_DESC = "(Ljava/lang/String;)Ljava/lang/Class;";

    private static final String INJECT = "Lorg/spongepowered/asm/mixin/injection/Inject;";
    private static final String AT = "Lorg/spongepowered/asm/mixin/injection/At;";
    private static final String SHIFT = "Lorg/spongepowered/asm/mixin/injection/At$Shift;";
    private static final String SORT_AND_RENDER = "sortAndRender";
    /** SRG name of EntityRenderer.enableLightmap(D)V (mcp_stable-12-1.7.10 methods.csv). */
    private static final String ENABLE_LIGHTMAP_SRG = "func_78463_b";
    private static final String ENABLE_LIGHTMAP_MCP = "enableLightmap";
    private static final String ENTITY_RENDERER = "Lnet/minecraft/client/renderer/EntityRenderer;";
    static final String HANDLER = "akashic$renderDeferredLodsDuringTranslucentSetup";
    private static final String HANDLER_DESC = "(Lnet/minecraft/entity/EntityLivingBase;ID"
        + "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;)V";
    private static final String HANDLER_SIGNATURE = "(Lnet/minecraft/entity/EntityLivingBase;ID"
        + "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable<Ljava/lang/Integer;>;)V";

    private static final String HOOKS = "com/akashic/thepatcher/module/mirai/MiraiShaderBridgeHooks";
    private static final String HOOK = "afterTranslucentLightmap";

    /** null until decided; ARMED, or why the bridge stays off. */
    private static volatile String plan;
    static final String ARMED = "armed";

    @Override
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        final String target = transformedName == null ? name : transformedName;
        final boolean probe = IRIS.equals(target) || DH_COMPAT.equals(target);
        if (!probe && !MIRAI_MIXIN.equals(target)) {
            return bytes;
        }
        if (plan() != ARMED) {
            return bytes;
        }
        try {
            final ClassNode node = new ClassNode();
            new ClassReader(bytes).accept(node, 0);
            final String result = probe ? patchProbe(node) : patchMixin(node, deobfuscatedEnvironment());
            if (result != null) {
                BundleLog.warn("Mirai x Angelica shader bridge skipped for " + target + ": " + result
                    + "; original bytecode retained.");
                return bytes;
            }
            final ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(writer);
            BundleLog.info("Mirai x Angelica shader bridge applied: " + target + (probe
                ? " now detects Distant Horizons through " + MIRAI_PLUGIN + "."
                : " now draws the deferred (translucent) LODs after enableLightmap in sortAndRender pass 1."));
            return writer.toByteArray();
        } catch (ThreadDeath | VirtualMachineError fatal) {
            throw fatal;
        } catch (Throwable error) {
            BundleLog.error("Mirai x Angelica shader bridge rejected " + target + "; original bytecode retained.", error);
            return bytes;
        }
    }

    /** Decided once for all three classes, from resources only (no class is loaded). */
    static String plan() {
        String current = plan;
        if (current == null) {
            synchronized (MiraiShaderBridgeTransformer.class) {
                current = plan;
                if (current == null) {
                    current = decide();
                    plan = current;
                    if (current == ARMED) {
                        BundleLog.info("Mirai x Angelica shader bridge armed: Angelica's Distant Horizons shader support "
                            + "will accept " + MIRAI_PLUGIN + " (no " + UPSTREAM_TWEAKER + " in this DH build).");
                    } else {
                        BundleLog.info("Mirai x Angelica shader bridge off: " + current + ".");
                    }
                }
            }
        }
        return current;
    }

    private static String decide() {
        if (Boolean.getBoolean(DISABLE_PROPERTY)) {
            return "disabled by -D" + DISABLE_PROPERTY + "=true";
        }
        if (!RuntimeSide.isClient()) {
            return "not a client";
        }
        final ClassLoader loader = MiraiShaderBridgeTransformer.class.getClassLoader();
        if (loader.getResource(resource(UPSTREAM_TWEAKER)) != null) {
            return UPSTREAM_TWEAKER + " is present, Angelica detects this Distant Horizons by itself";
        }
        for (String required : new String[] {MIRAI_PLUGIN, DH_API, MIRAI_RENDER_HELPER}) {
            if (loader.getResource(resource(required)) == null) {
                return "Akashic: Horizons of The Mirai is not installed (" + required + " missing)";
            }
        }
        try {
            for (String probe : new String[] {IRIS, DH_COMPAT}) {
                final byte[] bytes = read(loader, resource(probe));
                if (bytes == null) {
                    return "Angelica with Distant Horizons shader support is not installed (" + probe + " missing)";
                }
                final ClassNode node = new ClassNode();
                new ClassReader(bytes).accept(node, ClassReader.SKIP_FRAMES);
                final int count = probes(node).size();
                if (count != 1) {
                    return probe + " has " + count + " Distant Horizons probes, expected 1";
                }
            }
        } catch (IOException | RuntimeException error) {
            return "Angelica could not be inspected: " + error;
        }
        return ARMED;
    }

    /** Returns null when the class was patched, otherwise why it was left alone. */
    static String patchProbe(ClassNode node) {
        final List<LdcInsnNode> found = probes(node);
        if (found.size() != 1) {
            return found.size() + " Distant Horizons probes, expected 1";
        }
        found.get(0).cst = MIRAI_PLUGIN;
        return null;
    }

    /** LDC UPSTREAM_TWEAKER directly followed by Class.forName(String). */
    static List<LdcInsnNode> probes(ClassNode node) {
        final List<LdcInsnNode> found = new ArrayList<>();
        for (Object methodObject : node.methods) {
            final MethodNode method = (MethodNode) methodObject;
            for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (!(insn instanceof LdcInsnNode) || !UPSTREAM_TWEAKER.equals(((LdcInsnNode) insn).cst)) {
                    continue;
                }
                AbstractInsnNode next = insn.getNext();
                while (next != null && next.getOpcode() < 0) next = next.getNext();
                if (next instanceof MethodInsnNode && next.getOpcode() == Opcodes.INVOKESTATIC
                    && CLASS.equals(((MethodInsnNode) next).owner) && FOR_NAME.equals(((MethodInsnNode) next).name)
                    && FOR_NAME_DESC.equals(((MethodInsnNode) next).desc)) {
                    found.add((LdcInsnNode) insn);
                }
            }
        }
        return found;
    }

    /** Returns null when the mixin was patched, otherwise why it was left alone. */
    static String patchMixin(ClassNode node, boolean deobfuscated) throws AnalyzerException {
        if (!"java/lang/Object".equals(node.superName)) {
            return "unexpected super class " + node.superName;
        }
        boolean sortAndRenderInjector = false;
        for (Object methodObject : node.methods) {
            final MethodNode method = (MethodNode) methodObject;
            if (HANDLER.equals(method.name)) {
                return "already patched";
            }
            final AnnotationNode inject = annotation(method, INJECT);
            if (inject == null) {
                continue;
            }
            final Object methods = value(inject, "method");
            if (!(methods instanceof List) || !((List<?>) methods).contains(SORT_AND_RENDER)) {
                continue;
            }
            if (!HANDLER_DESC.equals(method.desc)) {
                return "unexpected sortAndRender handler " + method.name + method.desc;
            }
            sortAndRenderInjector = true;
            final Object at = value(inject, "at");
            if (at instanceof List) {
                for (Object a : (List<?>) at) {
                    final Object t = a instanceof AnnotationNode ? value((AnnotationNode) a, "target") : null;
                    if (t instanceof String && (((String) t).contains(ENABLE_LIGHTMAP_SRG)
                        || ((String) t).contains(ENABLE_LIGHTMAP_MCP))) {
                        return "this Mirai already renders the deferred LODs (" + method.name + ")";
                    }
                }
            }
        }
        if (!sortAndRenderInjector) {
            return "no sortAndRender injector, not the expected MixinRenderGlobal";
        }

        final MethodNode handler = new MethodNode(0, HANDLER, HANDLER_DESC, HANDLER_SIGNATURE, null);
        handler.instructions.add(new VarInsnNode(Opcodes.ILOAD, 2));
        handler.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, HOOK, "(I)V", false));
        handler.instructions.add(new InsnNode(Opcodes.RETURN));
        handler.maxStack = 1;
        handler.maxLocals = 6;

        final AnnotationNode at = new AnnotationNode(AT);
        at.values = new ArrayList<Object>(Arrays.asList(
            "value", "INVOKE",
            "target", ENTITY_RENDERER + (deobfuscated ? ENABLE_LIGHTMAP_MCP : ENABLE_LIGHTMAP_SRG) + "(D)V",
            "shift", new String[] {SHIFT, "AFTER"}));
        final AnnotationNode inject = new AnnotationNode(INJECT);
        inject.values = new ArrayList<Object>(Arrays.asList(
            "method", new ArrayList<Object>(Arrays.asList(SORT_AND_RENDER)),
            "at", new ArrayList<Object>(Arrays.asList(at)),
            "require", 0));
        handler.visibleAnnotations = new ArrayList<AnnotationNode>(Arrays.asList(inject));

        new Analyzer(new BasicVerifier()).analyze(node.name, handler);
        addMethod(node, handler);
        return null;
    }

    @SuppressWarnings("unchecked")
    private static void addMethod(ClassNode node, MethodNode method) {
        node.methods.add(method);
    }

    private static AnnotationNode annotation(MethodNode method, String desc) {
        for (List<?> list : Arrays.<List<?>>asList(method.visibleAnnotations, method.invisibleAnnotations)) {
            if (list == null) continue;
            for (Object a : list) {
                if (desc.equals(((AnnotationNode) a).desc)) return (AnnotationNode) a;
            }
        }
        return null;
    }

    private static Object value(AnnotationNode annotation, String key) {
        if (annotation.values == null) return null;
        for (int i = 0; i + 1 < annotation.values.size(); i += 2) {
            if (key.equals(annotation.values.get(i))) return annotation.values.get(i + 1);
        }
        return null;
    }

    private static boolean deobfuscatedEnvironment() {
        try {
            return Launch.blackboard != null
                && Boolean.TRUE.equals(Launch.blackboard.get("fml.deobfuscatedEnvironment"));
        } catch (Throwable absent) {
            return false;
        }
    }

    private static String resource(String className) {
        return className.replace('.', '/') + ".class";
    }

    private static byte[] read(ClassLoader loader, String path) throws IOException {
        try (InputStream in = loader.getResourceAsStream(path)) {
            if (in == null) return null;
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            final byte[] buffer = new byte[8192];
            for (int n; (n = in.read(buffer)) != -1;) out.write(buffer, 0, n);
            return out.toByteArray();
        }
    }
}
