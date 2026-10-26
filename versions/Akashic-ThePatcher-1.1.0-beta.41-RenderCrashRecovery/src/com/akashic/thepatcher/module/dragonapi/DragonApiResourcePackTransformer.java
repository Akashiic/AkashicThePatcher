package com.akashic.thepatcher.module.dragonapi;

import net.minecraft.launchwrapper.IClassTransformer;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.BasicVerifier;

import com.akashic.thepatcher.core.RuntimeSide;
import com.akashic.thepatcher.util.BundleLog;

/**
 * DragonAPI x resource packs that are not AbstractResourcePack (beta.41).
 *
 * DragonAPI (V33b) reads mod textures from the selected resource packs and casts
 * every selected pack to AbstractResourcePack without checking:
 *
 *   ReikaImageLoader.getImageFromResourcePack   (AbstractResourcePack) res
 *   ReikaTextureHelper.initializeColorOverrides (AbstractResourcePack) li.get(k)
 *
 * A pack that only implements IResourcePack - Waystones' "Modernity Textures"
 * (WaystonesAlternateResourcePack) - turns every ChromatiCraft/RotaryCraft TESR
 * texture bind into a ClassCastException ("Rendering Block Entity" crash, in the
 * world and in the creative inventory).
 *
 *   getImageFromResourcePack  starts with
 *                             {@code if (DragonApiResourcePackHooks.isUnsupportedPack(res)) return null;}
 *                             - null already means "this pack has no such image": DragonAPI
 *                             tries the next pack and then the mod's own texture;
 *   initializeColorOverrides  the getCurrentResourcePacks() result goes through
 *                             DragonApiResourcePackHooks.withoutUnsupportedPacks.
 *
 * Fails closed: a method is only edited when it still has the unchecked cast,
 * the insertion point needs no new stack map entry beyond one F_SAME, and the
 * edited method passes the ASM verifier. Anything else keeps DragonAPI's bytecode.
 * Kill-switch: -Dakashic.dragonApiResourcePack.disable=true
 */
public final class DragonApiResourcePackTransformer
    implements IClassTransformer {

    public static final String DISABLE_PROPERTY =
        "akashic.dragonApiResourcePack.disable";

    static final String IMAGE_LOADER = "Reika.DragonAPI.IO.ReikaImageLoader";
    static final String TEXTURE_HELPER = "Reika.DragonAPI.Libraries.IO.ReikaTextureHelper";

    private static final String TEXTURE_HELPER_INTERNAL = "Reika/DragonAPI/Libraries/IO/ReikaTextureHelper";
    private static final String ABSTRACT_PACK = "net/minecraft/client/resources/AbstractResourcePack";

    private static final String GET_IMAGE = "getImageFromResourcePack";
    private static final String GET_IMAGE_DESC = "(Ljava/lang/String;Lnet/minecraft/client/resources/IResourcePack;"
        + "LReika/DragonAPI/IO/ReikaImageLoader$ImageEditor;)Ljava/awt/image/BufferedImage;";
    private static final int PACK_SLOT = 1;

    private static final String COLOR_OVERRIDES = "initializeColorOverrides";
    private static final String CURRENT_PACKS = "getCurrentResourcePacks";
    private static final String LIST_DESC = "()Ljava/util/ArrayList;";

    private static final String HOOKS = "com/akashic/thepatcher/module/dragonapi/DragonApiResourcePackHooks";

    /** patchImageLoader/patchTextureHelper result for a class without the unchecked cast. */
    static final String ALREADY_SAFE = "no unchecked AbstractResourcePack cast";

    private static volatile boolean disabledLogged;

    @Override
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        final String target = transformedName == null ? name : transformedName;
        final boolean loader = IMAGE_LOADER.equals(target);
        if (!loader && !TEXTURE_HELPER.equals(target)) {
            return bytes;
        }
        if (Boolean.getBoolean(DISABLE_PROPERTY)) {
            if (!disabledLogged) {
                disabledLogged = true;
                BundleLog.info("DragonAPI resource pack guard DISABLED by -D" + DISABLE_PROPERTY + "=true.");
            }
            return bytes;
        }
        if (!RuntimeSide.isClient()) {
            return bytes;
        }
        try {
            final ClassNode node = new ClassNode();
            new ClassReader(bytes).accept(node, 0);
            final String result = loader ? patchImageLoader(node) : patchTextureHelper(node);
            if (ALREADY_SAFE.equals(result)) {
                BundleLog.info("DragonAPI resource pack guard not needed for " + target + ": " + result + ".");
                return bytes;
            }
            if (result != null) {
                BundleLog.warn("DragonAPI resource pack guard skipped for " + target + ": " + result
                    + "; original bytecode retained.");
                return bytes;
            }
            final ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(writer);
            BundleLog.info("DragonAPI resource pack guard applied: " + target
                + (loader ? " (" + GET_IMAGE + " skips packs that are not AbstractResourcePack)."
                          : " (" + COLOR_OVERRIDES + " reads AbstractResourcePack packs only)."));
            return writer.toByteArray();
        } catch (ThreadDeath | VirtualMachineError fatal) {
            throw fatal;
        } catch (Throwable error) {
            BundleLog.error("DragonAPI resource pack guard rejected " + target + "; original bytecode retained.", error);
            return bytes;
        }
    }

    /** Returns null when the class was patched, otherwise why it was left alone. */
    static String patchImageLoader(ClassNode node) throws AnalyzerException {
        final MethodNode method = findMethod(node, GET_IMAGE, GET_IMAGE_DESC);
        if (method == null || (method.access & Opcodes.ACC_STATIC) == 0) {
            return GET_IMAGE + " not found";
        }
        if (countPackCasts(method) == 0) {
            return ALREADY_SAFE;
        }
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof MethodInsnNode && HOOKS.equals(((MethodInsnNode) insn).owner)) {
                return "already patched";
            }
        }
        // The new branch target sits before the first instruction; a frame there would collide with ours.
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null && insn.getOpcode() < 0;
             insn = insn.getNext()) {
            if (insn instanceof FrameNode) {
                return "method starts at a branch target";
            }
        }
        final LabelNode keep = new LabelNode();
        final InsnList guard = new InsnList();
        guard.add(new VarInsnNode(Opcodes.ALOAD, PACK_SLOT));
        guard.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, "isUnsupportedPack", "(Ljava/lang/Object;)Z", false));
        guard.add(new JumpInsnNode(Opcodes.IFEQ, keep));
        guard.add(new InsnNode(Opcodes.ACONST_NULL));
        guard.add(new InsnNode(Opcodes.ARETURN));
        guard.add(keep);
        guard.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
        method.instructions.insert(guard);
        verify(node.name, method);
        return null;
    }

    /** Returns null when the class was patched, otherwise why it was left alone. */
    static String patchTextureHelper(ClassNode node) throws AnalyzerException {
        final MethodNode method = findMethod(node, COLOR_OVERRIDES, "()V");
        if (method == null || (method.access & Opcodes.ACC_STATIC) == 0) {
            return COLOR_OVERRIDES + " not found";
        }
        if (countPackCasts(method) == 0) {
            return ALREADY_SAFE;
        }
        int wrapped = 0;
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (!(insn instanceof MethodInsnNode)) {
                continue;
            }
            final MethodInsnNode call = (MethodInsnNode) insn;
            if (HOOKS.equals(call.owner)) {
                return "already patched";
            }
            if (call.getOpcode() == Opcodes.INVOKESTATIC && TEXTURE_HELPER_INTERNAL.equals(call.owner)
                && CURRENT_PACKS.equals(call.name) && LIST_DESC.equals(call.desc)) {
                final MethodInsnNode filter = new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS,
                    "withoutUnsupportedPacks", "(Ljava/util/ArrayList;)Ljava/util/ArrayList;", false);
                method.instructions.insert(call, filter);
                insn = filter;
                wrapped++;
            }
        }
        if (wrapped == 0) {
            return CURRENT_PACKS + "() is not called by " + COLOR_OVERRIDES;
        }
        verify(node.name, method);
        return null;
    }

    private static int countPackCasts(MethodNode method) {
        int casts = 0;
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn.getOpcode() == Opcodes.CHECKCAST && ABSTRACT_PACK.equals(((TypeInsnNode) insn).desc)) {
                casts++;
            }
        }
        return casts;
    }

    private static void verify(String owner, MethodNode method) throws AnalyzerException {
        new Analyzer(new BasicVerifier()).analyze(owner, method);
    }

    private static MethodNode findMethod(ClassNode node, String name, String desc) {
        for (Object methodObject : node.methods) {
            final MethodNode method = (MethodNode) methodObject;
            if (name.equals(method.name) && desc.equals(method.desc)) {
                return method;
            }
        }
        return null;
    }
}
