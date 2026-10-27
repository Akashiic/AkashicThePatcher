package com.akashic.containerclickguard;

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
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Patches the three mod packet handlers described in {@link ContainerClickGuardPlugin}. Each patch checks the exact
 * shape of the method it changes (InventoryTweaks 1.59-unofficial-asie-1, Avaritiaddons 1.9.5-GTNH); a different
 * shape is left untouched and logged as an error, because the hole would still be open.
 */
public final class ClickGuardTransformer implements IClassTransformer {
    static final String INVTWEAKS_CLICK = "invtweaks.network.handlers.ClickMessageHandler";
    static final String INFINITY_CLICK = "wanion.avaritiaddons.network.InfinityChestClick$Handler";
    static final String INFINITY_SLOT_SYNC = "wanion.avaritiaddons.network.InfinityChestSlotSync$Handler";
    static final String INFINITY_SYNC_ALL = "wanion.avaritiaddons.network.InfinityChestSyncAllSlots$Handler";

    static final String HOOKS = "com/akashic/containerclickguard/ClickGuardHooks";
    static final String CONTAINER = "net/minecraft/inventory/Container";
    static final String SLOT_CLICK = "func_75144_a";
    static final String SLOT_CLICK_DESC = "(IIILnet/minecraft/entity/player/EntityPlayer;)Lnet/minecraft/item/ItemStack;";
    static final String MESSAGE_CONTEXT = "cpw/mods/fml/common/network/simpleimpl/MessageContext";

    @Override
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        try {
            if (INVTWEAKS_CLICK.equals(transformedName)) {
                if (Boolean.getBoolean("akashic.containerClickGuard.invTweaks.disable")) {
                    Log.info("InventoryTweaks click routing disabled by -Dakashic.containerClickGuard.invTweaks.disable=true");
                    return bytes;
                }
                return patchInvTweaks(bytes);
            }
            if (INFINITY_CLICK.equals(transformedName) || INFINITY_SLOT_SYNC.equals(transformedName)
                    || INFINITY_SYNC_ALL.equals(transformedName)) {
                if (Boolean.getBoolean("akashic.containerClickGuard.avaritiaddons.disable")) {
                    Log.info("Avaritiaddons guard disabled by -Dakashic.containerClickGuard.avaritiaddons.disable=true ("
                            + transformedName + ")");
                    return bytes;
                }
                String hook = INFINITY_CLICK.equals(transformedName) ? "blockInfinityChestClick" : "blockServerBoundSync";
                return patchAvaritiaddons(transformedName, bytes, hook);
            }
        } catch (Throwable t) {
            Log.error("could not patch " + transformedName + "; left unchanged", t);
        }
        return bytes;
    }

    /** ClickMessageHandler.channelRead0: Container.slotClick -> ClickGuardHooks.invTweaksSlotClick (same stack shape). */
    static byte[] patchInvTweaks(byte[] bytes) {
        ClassNode cn = read(bytes);
        MethodNode m = method(cn, "channelRead0",
                "(Lio/netty/channel/ChannelHandlerContext;Linvtweaks/network/packets/ITPacketClick;)V");
        MethodInsnNode call = null;
        int calls = 0;
        if (m != null) {
            for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (insn.getOpcode() == Opcodes.INVOKEVIRTUAL) {
                    MethodInsnNode mi = (MethodInsnNode) insn;
                    if (CONTAINER.equals(mi.owner) && SLOT_CLICK.equals(mi.name) && SLOT_CLICK_DESC.equals(mi.desc)) {
                        call = mi;
                        calls++;
                    }
                }
            }
        }
        if (call == null || calls != 1 || call.getNext() == null || call.getNext().getOpcode() != Opcodes.POP) {
            Log.warn("InventoryTweaks ClickMessageHandler has an unknown shape (slotClick calls: " + calls
                    + "); NOT patched - its clicks still bypass InventoryClickEvent. Remove InventoryTweaks from the server.");
            return bytes;
        }
        m.instructions.set(call, new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, "invTweaksSlotClick",
                "(L" + CONTAINER + ";IIILnet/minecraft/entity/player/EntityPlayer;)Lnet/minecraft/item/ItemStack;", false));
        Log.info("InventoryTweaks click routing applied: ITPacketClick now goes through processClickWindow (InventoryClickEvent)");
        return write(cn);
    }

    /**
     * onMessage(message, MessageContext): prepend {@code if (ClickGuardHooks.<hook>(ctx)) return null;}. The new
     * branch target has the method's initial frame, so an F_SAME frame keeps the following compressed frames valid.
     */
    static byte[] patchAvaritiaddons(String className, byte[] bytes, String hook) {
        ClassNode cn = read(bytes);
        String messageClass = className.substring(0, className.lastIndexOf('$')).replace('.', '/');
        MethodNode m = method(cn, "onMessage",
                "(L" + messageClass + ";L" + MESSAGE_CONTEXT + ";)Lcpw/mods/fml/common/network/simpleimpl/IMessage;");
        AbstractInsnNode first = m == null ? null : firstReal(m.instructions.getFirst());
        if (m == null || first == null || hasFrameBefore(first) || (m.access & Opcodes.ACC_STATIC) != 0) {
            Log.warn(className + " has an unknown shape; NOT patched - forged Infinity Chest packets are still accepted");
            return bytes;
        }
        LabelNode go = new LabelNode();
        InsnList head = new InsnList();
        head.add(new VarInsnNode(Opcodes.ALOAD, 2));
        head.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, hook, "(L" + MESSAGE_CONTEXT + ";)Z", false));
        head.add(new JumpInsnNode(Opcodes.IFEQ, go));
        head.add(new InsnNode(Opcodes.ACONST_NULL));
        head.add(new InsnNode(Opcodes.ARETURN));
        head.add(go);
        head.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
        m.instructions.insert(head);
        Log.info("Avaritiaddons guard applied: " + className + " (" + hook + ")");
        return write(cn);
    }

    private static boolean hasFrameBefore(AbstractInsnNode insn) {
        for (AbstractInsnNode p = insn.getPrevious(); p != null; p = p.getPrevious()) {
            if (p.getType() == AbstractInsnNode.FRAME) {
                return true;
            }
        }
        return false;
    }

    private static AbstractInsnNode firstReal(AbstractInsnNode insn) {
        while (insn != null && insn.getOpcode() < 0) {
            insn = insn.getNext();
        }
        return insn;
    }

    private static ClassNode read(byte[] bytes) {
        ClassNode cn = new ClassNode();
        new ClassReader(bytes).accept(cn, 0);
        return cn;
    }

    private static byte[] write(ClassNode cn) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);
        return cw.toByteArray();
    }

    private static MethodNode method(ClassNode cn, String name, String desc) {
        for (Object o : cn.methods) { // raw List in asm-all 5.0.3
            MethodNode m = (MethodNode) o;
            if (m.name.equals(name) && m.desc.equals(desc)) {
                return m;
            }
        }
        return null;
    }
}
