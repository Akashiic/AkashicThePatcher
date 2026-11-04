package com.akashic.thepatcher.module.helditemcull;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.launchwrapper.IClassTransformer;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicVerifier;

import com.akashic.thepatcher.core.RuntimeSide;
import com.akashic.thepatcher.util.BundleLog;

import cpw.mods.fml.common.asm.transformers.deobf.FMLDeobfuscatingRemapper;

/**
 * Held 3D items vanish at the edge of the view (beta.52). Client only.
 *
 * An entity is drawn only when its own collision box is in view: vanilla's RenderGlobal.renderEntities tests
 * camera.isBoundingBoxInFrustum(entity.boundingBox), and Angelica (Celeritas) also asks
 * CeleritasWorldRenderer.isEntityVisible(entity), which tests the same box against the chunk sections in view. A
 * player's box is 0.6 x 1.8 blocks; an item drawn by a custom renderer in its hand can reach much farther (TEOF:
 * Kunftig's 3D fishing rods for AkashicFishing, 2-3 blocks). With the player just outside the view and the rod still
 * in it, the player is skipped, the rod with it, and the fishing line (drawn by the hook, another entity) falls back
 * to vanilla's line from the hand.
 *
 * Edit: both tests get the box from HeldItemCullHooks.cullBox(entity, box): the same box, grown by
 * -Dakashic.heldItemCull.margin blocks (default 3) when the entity is living and holds an item whose IItemRenderer
 * handles EQUIPPED. Nothing else changes: the entity's own box is never touched (collisions, picking), and the
 * entities that stay in range and in view are the same as before.
 *   RenderGlobal.renderEntities  each "entity.boundingBox" fed straight into ICamera.isBoundingBoxInFrustum (vanilla
 *                                has two: the entity and its leash holder) becomes DUP; GETFIELD; INVOKESTATIC cullBox
 *   CeleritasWorldRenderer.isEntityVisible  the box local, right before isInfiniteExtentsBox(box), goes through
 *                                cullBox (ALOAD entity; ALOAD box; INVOKESTATIC; ASTORE box)
 * No branch is added. ThePatcher sees Minecraft classes before FML's deobfuscation (notch names), so names and
 * descriptors are mapped through FML's remapper first (a no-op in a deobfuscated workspace); Angelica's classes are
 * mod classes with SRG member names. Mixins are applied later, on the patched bytes: the GETFIELD and the frustum
 * call stay where they were.
 *
 * Fails closed per class: the method must exist once with the vanilla/Angelica descriptor and hold exactly the
 * expected sites (2 in renderEntities, 1 in isEntityVisible); the edited method must pass the ASM verifier. Otherwise
 * the class is kept as it is and the log says why. Kill-switch -Dakashic.heldItemCull.disable=true.
 */
public final class HeldItemCullTransformer implements IClassTransformer {

    static final String RENDER_GLOBAL = "net.minecraft.client.renderer.RenderGlobal";
    static final String CELERITAS = "com.gtnewhorizons.angelica.rendering.celeritas.CeleritasWorldRenderer";
    static final String DISABLE = "akashic.heldItemCull.disable";
    /** A second pass over a patched class: nothing to do, nothing to log. */
    static final String ALREADY = "already patched";

    static final String HOOKS = "com/akashic/thepatcher/module/helditemcull/HeldItemCullHooks";
    static final String AABB = "net/minecraft/util/AxisAlignedBB";
    static final String ENTITY = "net/minecraft/entity/Entity";
    static final String CULL_BOX = "cullBox";
    static final String CULL_BOX_DESC = "(L" + ENTITY + ";L" + AABB + ";)L" + AABB + ";";

    static final String RENDER_ENTITIES_DESC =
        "(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/client/renderer/culling/ICamera;F)V";
    static final String ICAMERA = "net/minecraft/client/renderer/culling/ICamera";
    static final String INFINITE_OWNER = "com/gtnewhorizons/angelica/rendering/TileEntityRenderBoundsRegistry";
    static final String INFINITE_NAME = "isInfiniteExtentsBox";
    static final String INFINITE_DESC = "(L" + AABB + ";)Z";

    private static volatile boolean offLogged;

    /**
     * Called by AkashicThePatcherLoadingPlugin.getASMTransformerClass right after beta.51's GeckoLib animation copy
     * registers, so this transformer is the last one in the list (client only).
     */
    public static void register(List<String> transformers) {
        if (RuntimeSide.isClient()) {
            transformers.add(HeldItemCullTransformer.class.getName());
        }
    }

    @Override
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        final String target = transformedName == null ? name : transformedName;
        final boolean renderGlobal = RENDER_GLOBAL.equals(target);
        if (!renderGlobal && !CELERITAS.equals(target)) {
            return bytes;
        }
        if (Boolean.getBoolean(DISABLE)) {
            if (!offLogged) {
                offLogged = true;
                BundleLog.info("Held item cull box disabled by -D" + DISABLE + "=true.");
            }
            return bytes;
        }
        try {
            final ClassNode node = new ClassNode();
            new ClassReader(bytes).accept(node, 0);
            final String result = renderGlobal ? patchRenderGlobal(node) : patchCeleritas(node);
            if (ALREADY.equals(result)) {
                return bytes;
            }
            if (!result.startsWith("ok")) {
                BundleLog.warn("Held item cull box skipped for " + target + ": " + result + "; original bytecode retained.");
                return bytes;
            }
            final ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(writer);
            BundleLog.info("Held item cull box applied: " + target + " (" + result.substring(3) + ").");
            return writer.toByteArray();
        } catch (ThreadDeath | VirtualMachineError fatal) {
            throw fatal;
        } catch (Throwable error) {
            BundleLog.error("Held item cull box rejected " + target + "; original bytecode retained.", error);
            return bytes;
        }
    }

    /** "ok <detail>" when patched, else why not. */
    static String patchRenderGlobal(ClassNode node) throws Exception {
        final List<MethodNode> found = new ArrayList<MethodNode>();
        for (Object o : node.methods) {
            final MethodNode m = (MethodNode) o;
            final String mapped = Names.method(node.name, m.name, m.desc);
            if (RENDER_ENTITIES_DESC.equals(Names.methodDesc(m.desc))
                && ("renderEntities".equals(mapped) || "func_147589_a".equals(mapped))) {
                found.add(m);
            }
        }
        if (found.size() != 1) {
            return found.size() + " renderEntities" + RENDER_ENTITIES_DESC + " methods, expected 1";
        }
        final MethodNode method = found.get(0);
        if (calls(method, HOOKS)) {
            return ALREADY;
        }
        final List<FieldInsnNode> sites = new ArrayList<FieldInsnNode>();
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn.getOpcode() != Opcodes.GETFIELD) {
                continue;
            }
            final FieldInsnNode get = (FieldInsnNode) insn;
            final String field = Names.field(get.owner, get.name, get.desc);
            if (!ENTITY.equals(Names.type(get.owner)) || !("L" + AABB + ";").equals(Names.desc(get.desc))
                || !("boundingBox".equals(field) || "field_70121_D".equals(field))) {
                continue;
            }
            final AbstractInsnNode next = real(get.getNext());
            if (!(next instanceof MethodInsnNode) || next.getOpcode() != Opcodes.INVOKEINTERFACE) {
                continue;
            }
            final MethodInsnNode call = (MethodInsnNode) next;
            final String callName = Names.method(call.owner, call.name, call.desc);
            if (ICAMERA.equals(Names.type(call.owner)) && ("(L" + AABB + ";)Z").equals(Names.methodDesc(call.desc))
                && ("isBoundingBoxInFrustum".equals(callName) || "func_78546_a".equals(callName))) {
                sites.add(get);
            }
        }
        if (sites.size() != 2) {
            return sites.size() + " entity.boundingBox -> isBoundingBoxInFrustum site(s), expected 2";
        }
        for (FieldInsnNode get : sites) {
            // [camera, entity] -> DUP -> [camera, entity, entity] -> GETFIELD -> [camera, entity, box] -> cullBox
            method.instructions.insertBefore(get, new InsnNode(Opcodes.DUP));
            method.instructions.insert(get, new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, CULL_BOX, CULL_BOX_DESC, false));
        }
        verify(node, method);
        return "ok " + sites.size() + " frustum test(s) in renderEntities";
    }

    /** "ok <detail>" when patched, else why not. */
    static String patchCeleritas(ClassNode node) throws Exception {
        MethodNode method = null;
        for (Object o : node.methods) {
            final MethodNode m = (MethodNode) o;
            if ("isEntityVisible".equals(m.name) && ("(L" + ENTITY + ";)Z").equals(m.desc)) {
                if (method != null) {
                    return "isEntityVisible found twice";
                }
                method = m;
            }
        }
        if (method == null) {
            return "no isEntityVisible(L" + ENTITY + ";)Z";
        }
        if ((method.access & Opcodes.ACC_STATIC) != 0) {
            return "isEntityVisible is static";
        }
        if (calls(method, HOOKS)) {
            return ALREADY;
        }
        VarInsnNode boxLoad = null;
        int sites = 0;
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof MethodInsnNode && insn.getOpcode() == Opcodes.INVOKESTATIC) {
                final MethodInsnNode call = (MethodInsnNode) insn;
                if (INFINITE_OWNER.equals(call.owner) && INFINITE_NAME.equals(call.name) && INFINITE_DESC.equals(call.desc)) {
                    sites++;
                    final AbstractInsnNode previous = realBack(call.getPrevious());
                    if (previous instanceof VarInsnNode && previous.getOpcode() == Opcodes.ALOAD
                        && ((VarInsnNode) previous).var > 1) {
                        boxLoad = (VarInsnNode) previous;
                    }
                }
            }
        }
        if (sites != 1 || boxLoad == null) {
            return sites + " isInfiniteExtentsBox(box local) call(s), expected 1";
        }
        // box = cullBox(entity, box), right where the box is first read (after the null fallback has joined)
        final InsnList edit = new InsnList();
        edit.add(new VarInsnNode(Opcodes.ALOAD, 1));
        edit.add(new VarInsnNode(Opcodes.ALOAD, boxLoad.var));
        edit.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, CULL_BOX, CULL_BOX_DESC, false));
        edit.add(new VarInsnNode(Opcodes.ASTORE, boxLoad.var));
        method.instructions.insertBefore(boxLoad, edit);
        verify(node, method);
        return "ok isEntityVisible";
    }

    private static void verify(ClassNode node, MethodNode method) throws Exception {
        final int stack = method.maxStack;
        method.maxStack = stack + 2; // COMPUTE_MAXS sets the real value when the class is written
        new Analyzer(new BasicVerifier()).analyze(node.name, method);
    }

    private static boolean calls(MethodNode method, String owner) {
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof MethodInsnNode && owner.equals(((MethodInsnNode) insn).owner)) {
                return true;
            }
        }
        return false;
    }

    private static AbstractInsnNode real(AbstractInsnNode insn) {
        while (insn != null && insn.getOpcode() < 0) {
            insn = insn.getNext();
        }
        return insn;
    }

    private static AbstractInsnNode realBack(AbstractInsnNode insn) {
        while (insn != null && insn.getOpcode() < 0) {
            insn = insn.getPrevious();
        }
        return insn;
    }

    /** FML's notch -> SRG/MCP remapper when present (it is in every Forge client), identity otherwise. */
    static final class Names {
        private static final boolean FML;

        static {
            boolean present;
            try {
                present = FMLDeobfuscatingRemapper.INSTANCE != null;
            } catch (LinkageError absent) {
                present = false;
            }
            FML = present;
        }

        static String type(String internalName) {
            return FML ? FMLDeobfuscatingRemapper.INSTANCE.map(internalName) : internalName;
        }

        static String method(String owner, String name, String desc) {
            return FML ? FMLDeobfuscatingRemapper.INSTANCE.mapMethodName(owner, name, desc) : name;
        }

        static String field(String owner, String name, String desc) {
            return FML ? FMLDeobfuscatingRemapper.INSTANCE.mapFieldName(owner, name, desc) : name;
        }

        static String desc(String desc) {
            return FML ? FMLDeobfuscatingRemapper.INSTANCE.mapDesc(desc) : desc;
        }

        static String methodDesc(String desc) {
            return FML ? FMLDeobfuscatingRemapper.INSTANCE.mapMethodDesc(desc) : desc;
        }

        private Names() {}
    }
}
