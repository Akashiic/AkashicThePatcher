package com.akashic.thepatcher.module.neostates;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.launchwrapper.IClassTransformer;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicVerifier;

import com.akashic.thepatcher.core.RuntimeSide;
import com.akashic.thepatcher.util.BundleLog;

import makamys.mixingasm.api.MixinSafeTransformer;

/**
 * NeoStates x DragonAPI without Angelica (beta.53). Client only.
 *
 * Neo States (Akashic) adds, only when Angelica is absent, the mixin
 * client.MixinWorldRenderer_WaterPass: a required @WrapOperation on the call Block.getRenderBlockPass() inside
 * WorldRenderer.updateRenderer, so a block that may hold water asks for the translucent pass (else its water is
 * skipped in a section with no other translucent block). DragonAPI's coremod (RenderBlockEvent) rewrites that very
 * call into its own hook, RenderBlockAtPosEvent.getMaxRenderPass(block, x, y, z), which calls
 * block.getRenderBlockPass() itself and keeps the highest pass of its listeners. With both mods and no Angelica the
 * required injection finds no target and the game stops in pre-init (Critical injection failure ...
 * MixinWorldRenderer_WaterPass ... Scanned 0 target(s)). TEOF hits it in the launcher's compatibility mode, which
 * removes Angelica for GPUs without OpenGL 3.3.
 *
 * Edit, only with Neo States and DragonAPI present and Angelica absent (the same Angelica test Neo States does):
 *   NeoStates' mixin class      the @WrapOperation of neostates$holdersNeedTheWaterPass gets require = 0, so it is
 *                               optional (it still applies when the vanilla call is there)
 *   DragonAPI's getMaxRenderPass  the value of its block.getRenderBlockPass() call goes through
 *                               NeoStatesWaterPassHooks.waterPass(pass, block): the rule of Neo States' wrap (pass < 1
 *                               and the block may hold water -> 1), at the same place
 * The result is what Neo States does on the vanilla call: the same sections get the translucent pass, nothing else
 * changes. With Angelica, Neo States does not add the mixin and nothing here runs.
 *
 * Mixin reads mixin classes through the transformers it trusts; UniMixins' Mixingasm trusts this one through
 * @MixinSafeTransformer (beta.39's lesson). Fails closed per class: exact name/descriptor/annotation fingerprints and
 * exactly one call site, ASM verifier on the edited method; a Neo States that already wraps DragonAPI's hook itself
 * (upstream fix) leaves both classes alone. Kill-switch -Dakashic.neoStatesWaterPass.disable=true.
 */
@MixinSafeTransformer
public final class NeoStatesWaterPassTransformer implements IClassTransformer {

    static final String MIXIN = "com.akashic.neostates.mixin.early.client.MixinWorldRenderer_WaterPass";
    static final String DRAGON = "Reika.DragonAPI.Instantiable.Event.Client.RenderBlockAtPosEvent";
    static final String DISABLE = "akashic.neoStatesWaterPass.disable";
    static final String ALREADY = "already patched";

    static final String HANDLER = "neostates$holdersNeedTheWaterPass";
    static final String HANDLER_DESC =
        "(Lnet/minecraft/block/Block;Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;)I";
    static final String WRAP_OPERATION = "Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;";
    static final String AT = "Lorg/spongepowered/asm/mixin/injection/At;";
    static final String VANILLA_TARGET = "Lnet/minecraft/block/Block;getRenderBlockPass()I";
    static final String DRAGON_INTERNAL = "Reika/DragonAPI/Instantiable/Event/Client/RenderBlockAtPosEvent";

    static final String BLOCK = "net/minecraft/block/Block";
    static final String MAX_PASS = "getMaxRenderPass";
    static final String MAX_PASS_DESC = "(L" + BLOCK + ";III)I";
    static final String HOOKS = "com/akashic/thepatcher/module/neostates/NeoStatesWaterPassHooks";
    static final String HOOK = "waterPass";
    static final String HOOK_DESC = "(IL" + BLOCK + ";)I";

    static final String NEOSTATES_API = "com/akashic/neostates/api/NeoStates.class";
    static final String NEOSTATES_MIXIN_RES = "com/akashic/neostates/mixin/early/client/MixinWorldRenderer_WaterPass.class";
    static final String DRAGON_RES = DRAGON_INTERNAL + ".class";
    static final String ANGELICA_RES = "com/gtnewhorizons/angelica/AngelicaMod.class";

    private static volatile String armed;
    private static volatile boolean offLogged;

    /** Called by AkashicThePatcherLoadingPlugin.getASMTransformerClass right after beta.52's HeldItemCullTransformer. */
    public static void register(List<String> transformers) {
        if (RuntimeSide.isClient()) {
            transformers.add(NeoStatesWaterPassTransformer.class.getName());
        }
    }

    @Override
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        final String target = transformedName == null ? name : transformedName;
        final boolean mixin = MIXIN.equals(target);
        if (!mixin && !DRAGON.equals(target)) {
            return bytes;
        }
        if (Boolean.getBoolean(DISABLE)) {
            if (!offLogged) {
                offLogged = true;
                BundleLog.info("NeoStates x DragonAPI water pass fix disabled by -D" + DISABLE + "=true.");
            }
            return bytes;
        }
        final String state = armState();
        if (!"armed".equals(state)) {
            return bytes;
        }
        try {
            final ClassNode node = new ClassNode();
            new ClassReader(bytes).accept(node, 0);
            final String result = mixin ? patchMixin(node) : patchDragonApi(node);
            if (ALREADY.equals(result)) {
                return bytes;
            }
            if (!result.startsWith("ok")) {
                BundleLog.warn("NeoStates x DragonAPI water pass fix skipped for " + target + ": " + result
                    + "; original bytecode retained.");
                return bytes;
            }
            final ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(writer);
            BundleLog.info("NeoStates x DragonAPI water pass fix applied: " + target + " (" + result.substring(3) + ").");
            return writer.toByteArray();
        } catch (ThreadDeath | VirtualMachineError fatal) {
            throw fatal;
        } catch (Throwable error) {
            BundleLog.error("NeoStates x DragonAPI water pass fix rejected " + target + "; original bytecode retained.",
                error);
            return bytes;
        }
    }

    /** "armed", or why not (decided once, logged once). */
    static String armState() {
        String state = armed;
        if (state == null) {
            synchronized (NeoStatesWaterPassTransformer.class) {
                state = armed;
                if (state == null) {
                    state = decide(NeoStatesWaterPassTransformer.class.getClassLoader());
                    armed = state;
                    if ("armed".equals(state)) {
                        BundleLog.info("NeoStates x DragonAPI water pass fix armed (Neo States and DragonAPI without"
                            + " Angelica).");
                    } else {
                        BundleLog.info("NeoStates x DragonAPI water pass fix not needed: " + state + ".");
                    }
                }
            }
        }
        return state;
    }

    static String decide(ClassLoader loader) {
        if (loader.getResource(NEOSTATES_API) == null) {
            return "no Neo States";
        }
        if (loader.getResource(DRAGON_RES) == null) {
            return "no DragonAPI";
        }
        if (loader.getResource(ANGELICA_RES) != null) {
            return "Angelica present (Neo States does not add the vanilla renderer mixin)";
        }
        final byte[] own = read(loader, NEOSTATES_MIXIN_RES);
        if (own == null) {
            return "Neo States without MixinWorldRenderer_WaterPass";
        }
        final ClassNode node = new ClassNode();
        new ClassReader(own).accept(node, ClassReader.SKIP_CODE);
        return mixinShape(node);
    }

    /** "armed" for the Neo States shape this fix knows (one wrap, on the vanilla call), else why not. */
    static String mixinShape(ClassNode node) {
        int wraps = 0;
        for (Object o : node.methods) {
            final MethodNode m = (MethodNode) o;
            final AnnotationNode wrap = annotation(m, WRAP_OPERATION);
            if (wrap == null) {
                continue;
            }
            wraps++;
            final String at = atTarget(wrap);
            if (at != null && at.startsWith("L" + DRAGON_INTERNAL + ";")) {
                return "Neo States already wraps DragonAPI's hook";
            }
        }
        if (wraps != 1) {
            return wraps + " @WrapOperation handlers in Neo States' mixin, expected 1";
        }
        return "armed";
    }

    /** "ok <detail>" when patched, else why not. */
    static String patchMixin(ClassNode node) {
        final List<MethodNode> found = new ArrayList<MethodNode>();
        for (Object o : node.methods) {
            final MethodNode m = (MethodNode) o;
            if (HANDLER.equals(m.name) && HANDLER_DESC.equals(m.desc)) {
                found.add(m);
            }
        }
        if (found.size() != 1) {
            return found.size() + " " + HANDLER + HANDLER_DESC + " handlers, expected 1";
        }
        final AnnotationNode wrap = annotation(found.get(0), WRAP_OPERATION);
        if (wrap == null) {
            return "handler without @WrapOperation";
        }
        if (!VANILLA_TARGET.equals(atTarget(wrap)) || !"updateRenderer".equals(single(value(wrap, "method")))) {
            return "@WrapOperation is not updateRenderer -> " + VANILLA_TARGET;
        }
        final Object require = value(wrap, "require");
        if (Integer.valueOf(0).equals(require)) {
            return ALREADY;
        }
        if (require != null) {
            return "unexpected require=" + require;
        }
        final List<Object> values = new ArrayList<Object>();
        if (wrap.values != null) {
            values.addAll(wrap.values);
        }
        values.add("require");
        values.add(Integer.valueOf(0));
        wrap.values = values;
        return "ok " + HANDLER + " require=0";
    }

    /** "ok <detail>" when patched, else why not. */
    static String patchDragonApi(ClassNode node) throws Exception {
        final List<MethodNode> found = new ArrayList<MethodNode>();
        for (Object o : node.methods) {
            final MethodNode m = (MethodNode) o;
            if (MAX_PASS.equals(m.name) && MAX_PASS_DESC.equals(m.desc)) {
                found.add(m);
            }
        }
        if (found.size() != 1) {
            return found.size() + " " + MAX_PASS + MAX_PASS_DESC + " methods, expected 1";
        }
        final MethodNode method = found.get(0);
        if ((method.access & Opcodes.ACC_STATIC) == 0) {
            return MAX_PASS + " is not static";
        }
        final List<MethodInsnNode> sites = new ArrayList<MethodInsnNode>();
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (!(insn instanceof MethodInsnNode)) {
                continue;
            }
            final MethodInsnNode call = (MethodInsnNode) insn;
            if (HOOKS.equals(call.owner)) {
                return ALREADY;
            }
            if (call.getOpcode() == Opcodes.INVOKEVIRTUAL && BLOCK.equals(call.owner) && "()I".equals(call.desc)
                && ("func_149701_w".equals(call.name) || "getRenderBlockPass".equals(call.name))) {
                sites.add(call);
            }
        }
        if (sites.size() != 1) {
            return sites.size() + " Block.getRenderBlockPass() calls in " + MAX_PASS + ", expected 1";
        }
        final MethodInsnNode site = sites.get(0);
        final AbstractInsnNode receiver = real(site.getPrevious());
        if (!(receiver instanceof VarInsnNode) || receiver.getOpcode() != Opcodes.ALOAD || ((VarInsnNode) receiver).var != 0) {
            return "getRenderBlockPass() is not called on the block parameter";
        }
        final InsnList hook = new InsnList();
        hook.add(new VarInsnNode(Opcodes.ALOAD, 0));
        hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, HOOK, HOOK_DESC, false));
        method.instructions.insert(site, hook);
        new Analyzer(new BasicVerifier()).analyze(node.name, method);
        return "ok " + MAX_PASS + ": 1 site";
    }

    static AnnotationNode annotation(MethodNode m, String desc) {
        final AnnotationNode visible = find(m.visibleAnnotations, desc);
        return visible != null ? visible : find(m.invisibleAnnotations, desc);
    }

    static AnnotationNode find(List<?> list, String desc) {
        if (list != null) {
            for (Object o : list) {
                if (o instanceof AnnotationNode && desc.equals(((AnnotationNode) o).desc)) {
                    return (AnnotationNode) o;
                }
            }
        }
        return null;
    }

    static Object value(AnnotationNode a, String key) {
        if (a.values == null) {
            return null;
        }
        for (int i = 0; i + 1 < a.values.size(); i += 2) {
            if (key.equals(a.values.get(i))) {
                return a.values.get(i + 1);
            }
        }
        return null;
    }

    static Object single(Object list) {
        return list instanceof List && ((List<?>) list).size() == 1 ? ((List<?>) list).get(0) : null;
    }

    /** The target of the single @At of an injector, or null. */
    static String atTarget(AnnotationNode injector) {
        final Object at = single(value(injector, "at"));
        if (!(at instanceof AnnotationNode) || !AT.equals(((AnnotationNode) at).desc)) {
            return null;
        }
        final Object target = value((AnnotationNode) at, "target");
        return target instanceof String ? (String) target : null;
    }

    static AbstractInsnNode real(AbstractInsnNode insn) {
        while (insn != null && insn.getOpcode() < 0) {
            insn = insn.getPrevious();
        }
        return insn;
    }

    static byte[] read(ClassLoader loader, String resource) {
        try (InputStream in = loader.getResourceAsStream(resource)) {
            if (in == null) {
                return null;
            }
            final java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            final byte[] buffer = new byte[8192];
            for (int n; (n = in.read(buffer)) != -1;) {
                out.write(buffer, 0, n);
            }
            return out.toByteArray();
        } catch (java.io.IOException e) {
            return null;
        }
    }
}
