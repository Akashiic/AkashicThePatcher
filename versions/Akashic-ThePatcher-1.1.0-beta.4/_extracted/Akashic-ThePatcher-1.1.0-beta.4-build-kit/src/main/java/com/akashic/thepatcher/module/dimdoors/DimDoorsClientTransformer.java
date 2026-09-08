package com.akashic.thepatcher.module.dimdoors;

import com.akashic.thepatcher.core.RuntimeSide;
import com.akashic.thepatcher.util.BundleLog;
import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import java.util.ArrayList;
import java.util.List;

/** Only touches the audited S35 notification and nullable dimension lookups. */
public final class DimDoorsClientTransformer implements IClassTransformer {
    static final String PREFIX = "org/dimdev/dimdoors/";
    static final String TILE = PREFIX + "tileentities/TileEntityDimDoor";
    static final String POCKET = PREFIX + "core/PocketManager";
    static final String HOOK = "com/akashic/thepatcher/module/dimdoors/DimDoorsClientRuntime";
    @Override public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null || transformedName == null) return bytes;
        String target = transformedName.replace('.', '/');
        if (!TILE.equals(target) && !POCKET.equals(target)) return bytes;
        if (!RuntimeSide.isClient() || !DimDoorsClientModule.enabled()) return bytes;
        try {
            ClassNode node = new ClassNode(Opcodes.ASM5);
            new ClassReader(bytes).accept(node, 0);
            if (!target.equals(node.name)) throw new IllegalArgumentException("class identity mismatch");
            for (MethodNode method : node.methods) for (AbstractInsnNode insn : method.instructions.toArray())
                if (insn instanceof MethodInsnNode && HOOK.equals(((MethodInsnNode) insn).owner)) return bytes;
            if (TILE.equals(target)) patchTile(node); else patchLookups(node);
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(writer);
            byte[] output = writer.toByteArray();
            BundleLog.info("DimDoors client sync target installed: " + target);
            return output;
        } catch (RuntimeException ex) {
            BundleLog.info("WARN: DimDoors client sync target rejected; original retained: " + target + ": " + ex);
            return bytes;
        }
    }
    private static void patchTile(ClassNode node) {
        String desc = "(Lnet/minecraft/network/NetworkManager;Lnet/minecraft/network/play/server/S35PacketUpdateTileEntity;)V";
        MethodNode target = null;
        for (MethodNode m : node.methods) if (m.name.equals("onDataPacket") && m.desc.equals(desc)) target = m;
        if (target == null) throw new IllegalArgumentException("S35 method missing");
        List<MethodInsnNode> calls = new ArrayList<>();
        boolean nbt = false;
        for (AbstractInsnNode n : target.instructions.toArray()) if (n instanceof MethodInsnNode) {
            MethodInsnNode call = (MethodInsnNode) n;
            if ((call.name.equals("func_145839_a") || call.name.equals("readFromNBT"))
                && call.desc.equals("(Lnet/minecraft/nbt/NBTTagCompound;)V")) nbt = true;
            if (call.owner.equals(PREFIX + "watcher/UpdateWatcherProxy") && call.name.equals("onCreated")
                && call.desc.equals("(Ljava/lang/Object;)V") && call.getOpcode() == Opcodes.INVOKEVIRTUAL) {
                if (!nbt) throw new IllegalArgumentException("notification occurs before NBT");
                calls.add(call);
            }
        }
        if (calls.size() != 1) throw new IllegalArgumentException("expected exactly one S35 notification");
        MethodInsnNode call = calls.get(0);
        InsnList extra = new InsnList();
        extra.add(new VarInsnNode(Opcodes.ALOAD, 0));
        extra.add(new VarInsnNode(Opcodes.ALOAD, 1));
        target.instructions.insertBefore(call, extra);
        target.instructions.set(call, new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK, "onTileLink",
            "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V", false));
    }
    private static void patchLookups(ClassNode node) {
        List<MethodInsnNode> calls = new ArrayList<>();
        int methods = 0;
        for (MethodNode method : node.methods) {
            if (!method.name.equals("getDimensionData") || !(method.desc.equals("(I)L" + PREFIX + "core/NewDimData;")
                || method.desc.equals("(Lnet/minecraft/world/World;)L" + PREFIX + "core/NewDimData;"))) continue;
            methods++;
            int before = calls.size(), fields = 0;
            for (AbstractInsnNode n : method.instructions.toArray()) {
                if (n instanceof FieldInsnNode && n.getOpcode() == Opcodes.GETSTATIC) {
                    FieldInsnNode f = (FieldInsnNode) n;
                    if (f.owner.equals(POCKET) && f.name.equals("dimensionData") && f.desc.equals("Ljava/util/HashMap;")) fields++;
                }
                if (n instanceof MethodInsnNode) {
                    MethodInsnNode c = (MethodInsnNode) n;
                    if (c.getOpcode() == Opcodes.INVOKEVIRTUAL && c.owner.equals("java/util/HashMap")
                        && c.name.equals("get") && c.desc.equals("(Ljava/lang/Object;)Ljava/lang/Object;")) calls.add(c);
                }
            }
            if (fields != 1 || calls.size() - before != 1) throw new IllegalArgumentException("lookup ABI mismatch");
        }
        if (methods != 2) throw new IllegalArgumentException("expected both nullable lookups");        for (MethodInsnNode call : calls) {
            call.setOpcode(Opcodes.INVOKESTATIC);
            call.owner = HOOK; call.name = "safeMapGet";
            call.desc = "(Ljava/util/Map;Ljava/lang/Object;)Ljava/lang/Object;";
            call.itf = false;
        }
    }
}
