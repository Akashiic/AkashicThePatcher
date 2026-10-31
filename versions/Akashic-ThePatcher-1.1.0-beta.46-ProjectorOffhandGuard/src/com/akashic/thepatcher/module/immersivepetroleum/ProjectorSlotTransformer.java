package com.akashic.thepatcher.module.immersivepetroleum;

import java.util.List;

import net.minecraft.launchwrapper.IClassTransformer;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.BasicVerifier;

import com.akashic.thepatcher.core.RuntimeSide;
import com.akashic.thepatcher.util.BundleLog;

/**
 * Immersive Petroleum projector slot guard (beta.46).
 *
 * The pack's Immersive Petroleum backport keeps one projection per hotbar slot and looks up the held one with
 *
 *   ProjectorClient.selectedView()   return VIEWS[mc.thePlayer.inventory.currentItem];   // VIEWS = new ProjectorView[9]
 *
 * Backhand appends the offhand as slot 36 of InventoryPlayer.mainInventory and points currentItem at it while it
 * runs an offhand action (BackhandUtils.useOffhandItem / swapToOffhand). When the HUD text event reaches the
 * projector overlay in that window the client crashes:
 *
 *   java.lang.ArrayIndexOutOfBoundsException: 36
 *       at flaxbeard.immersivepetroleum.client.projector.ProjectorClient.selectedView(ProjectorClient.java:50)
 *       at flaxbeard.immersivepetroleum.client.projector.ProjectorClient.overlay(ProjectorClient.java:89)
 *
 * The single AALOAD of selectedView becomes ProjectorSlotHooks.view(VIEWS, slot), which answers null for a slot
 * outside the array; overlay already treats null as "no projection held". Only the client is touched.
 *
 * Fails closed: selectedView must exist once, read the static VIEWS once and index it once with
 * InventoryPlayer.currentItem, and the edited method must pass the ASM verifier, else the class is kept.
 * Kill-switch: -Dakashic.projectorSlotGuard.disable=true
 */
public final class ProjectorSlotTransformer implements IClassTransformer {

    public static final String DISABLE_PROPERTY = "akashic.projectorSlotGuard.disable";

    static final String TARGET = "flaxbeard.immersivepetroleum.client.projector.ProjectorClient";
    static final String VIEW = "flaxbeard/immersivepetroleum/client/projector/ProjectorView";
    static final String FIELD = "VIEWS";
    static final String METHOD = "selectedView";
    private static final String HOOKS = "com/akashic/thepatcher/module/immersivepetroleum/ProjectorSlotHooks";
    private static final String HOOK = "view";
    private static final String HOOK_DESC = "([Ljava/lang/Object;I)Ljava/lang/Object;";
    private static final String INVENTORY = "net/minecraft/entity/player/InventoryPlayer";

    private static volatile boolean disabledLogged;

    /**
     * Called by AkashicThePatcherLoadingPlugin.getASMTransformerClass right after beta.45's AgriCraft irrigation
     * guard registers, so this transformer is the last one in the list, on the client only.
     */
    public static void register(List<String> transformers) {
        if (RuntimeSide.isClient()) {
            transformers.add(ProjectorSlotTransformer.class.getName());
        }
    }

    @Override
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        final String target = transformedName == null ? name : transformedName;
        if (!TARGET.equals(target)) {
            return bytes;
        }
        if (Boolean.getBoolean(DISABLE_PROPERTY)) {
            if (!disabledLogged) {
                disabledLogged = true;
                BundleLog.info("Immersive Petroleum projector slot guard DISABLED by -D" + DISABLE_PROPERTY + "=true.");
            }
            return bytes;
        }
        if (!RuntimeSide.isClient()) {
            return bytes;
        }
        try {
            final ClassNode node = new ClassNode();
            new ClassReader(bytes).accept(node, 0);
            final String result = patch(node);
            if (result != null) {
                BundleLog.warn("Immersive Petroleum projector slot guard skipped for " + target + ": " + result
                    + "; original bytecode retained.");
                return bytes;
            }
            final ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(writer);
            BundleLog.info("Immersive Petroleum projector slot guard applied: " + target + " (a selected slot outside"
                + " the hotbar, e.g. Backhand's offhand slot 36, means no projection instead of a crash).");
            return writer.toByteArray();
        } catch (ThreadDeath | VirtualMachineError fatal) {
            throw fatal;
        } catch (Throwable error) {
            BundleLog.error("Immersive Petroleum projector slot guard rejected " + target
                + "; original bytecode retained.", error);
            return bytes;
        }
    }

    /** Returns null when the class was patched, otherwise why it was left alone. */
    static String patch(ClassNode node) throws AnalyzerException {
        final String arrayDesc = "[L" + VIEW + ";";
        FieldNode field = null;
        for (Object fieldObject : node.fields) {
            final FieldNode candidate = (FieldNode) fieldObject;
            if (FIELD.equals(candidate.name)) {
                field = candidate;
            }
        }
        if (field == null || !arrayDesc.equals(field.desc) || (field.access & Opcodes.ACC_STATIC) == 0) {
            return "no static " + VIEW + "[] " + FIELD;
        }
        MethodNode method = null;
        int methods = 0;
        for (Object methodObject : node.methods) {
            final MethodNode candidate = (MethodNode) methodObject;
            if (METHOD.equals(candidate.name)) {
                method = candidate;
                methods++;
            }
        }
        if (methods != 1 || !("()L" + VIEW + ";").equals(method.desc) || (method.access & Opcodes.ACC_STATIC) == 0) {
            return methods + " static " + METHOD + "()L" + VIEW + "; methods (expected 1)";
        }
        int reads = 0;
        AbstractInsnNode load = null;
        int loads = 0;
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof MethodInsnNode && HOOKS.equals(((MethodInsnNode) insn).owner)) {
                return "already patched";
            }
            if (insn.getOpcode() == Opcodes.GETSTATIC) {
                final FieldInsnNode get = (FieldInsnNode) insn;
                if (node.name.equals(get.owner) && FIELD.equals(get.name) && arrayDesc.equals(get.desc)) {
                    reads++;
                }
            } else if (insn.getOpcode() == Opcodes.AALOAD) {
                load = insn;
                loads++;
            }
        }
        if (reads != 1 || loads != 1) {
            return reads + " reads of " + FIELD + " and " + loads + " array loads in " + METHOD + " (expected 1 and 1)";
        }
        // The index must be InventoryPlayer.currentItem (SRG name in the pack, MCP name in a dev workspace).
        AbstractInsnNode index = load.getPrevious();
        while (index != null && index.getOpcode() < 0) {
            index = index.getPrevious();
        }
        if (!(index instanceof FieldInsnNode) || index.getOpcode() != Opcodes.GETFIELD
            || !INVENTORY.equals(((FieldInsnNode) index).owner) || !"I".equals(((FieldInsnNode) index).desc)
            || !("field_70461_c".equals(((FieldInsnNode) index).name)
                || "currentItem".equals(((FieldInsnNode) index).name))) {
            return "the array index is not InventoryPlayer.currentItem";
        }
        // VIEWS, slot -> ProjectorSlotHooks.view(VIEWS, slot): same stack shape, no branch, no new frame.
        final InsnList call = new InsnList();
        call.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, HOOK, HOOK_DESC, false));
        call.add(new TypeInsnNode(Opcodes.CHECKCAST, VIEW));
        method.instructions.insertBefore(load, call);
        method.instructions.remove(load);
        new Analyzer(new BasicVerifier()).analyze(node.name, method);
        return null;
    }
}
