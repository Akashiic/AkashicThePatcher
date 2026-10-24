package dev.akashic.asynciconcompat.core;

import dev.akashic.asynciconcompat.CompatState;
import net.minecraft.launchwrapper.IClassTransformer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

public final class AsyncIconCompatTransformer implements IClassTransformer {

    private static final Logger LOG = LogManager.getLogger("AkashicAsyncCompatCore");
    private static final String CHISEL = "team.chisel.ctmlib.TextureSubmap";
    private static final String MEKANISM = "mekanism.client.render.block.TextureSubmap";
    private static final String CTOR_DESC = "(Lnet/minecraft/util/IIcon;II)V";
    private static final String HOOK = "dev/akashic/asynciconcompat/CompatHooks";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) return null;
        String target = transformedName == null ? name : transformedName;
        if (CHISEL.equals(target)) {
            return patchChisel(basicClass);
        }
        if (MEKANISM.equals(target)) {
            return patchMekanism(basicClass);
        }
        return basicClass;
    }

    private static byte[] patchChisel(byte[] original) {
        try {
            ClassNode node = read(original);
            MethodNode ctor = findUniqueConstructor(node);
            if (ctor == null) {
                LOG.error("Refusing Chisel async guard: expected constructor was not unique.");
                return original;
            }

            int patched = 0;
            for (AbstractInsnNode insn = ctor.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (!(insn instanceof MethodInsnNode)) continue;
                MethodInsnNode call = (MethodInsnNode) insn;
                if (call.getOpcode() == Opcodes.INVOKEINTERFACE
                    && "java/util/List".equals(call.owner)
                    && "add".equals(call.name)
                    && "(Ljava/lang/Object;)Z".equals(call.desc)) {
                    ctor.instructions.set(call, new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        HOOK,
                        "addChiselSubmapSynchronized",
                        "(Ljava/util/List;Ljava/lang/Object;)Z",
                        false));
                    patched++;
                }
            }

            if (patched != 1) {
                LOG.error("Refusing Chisel async guard: expected 1 List.add call, found {}.", patched);
                return original;
            }
            CompatState.markChiselPatched();
            LOG.info("Installed synchronized Chisel TextureSubmap registration guard.");
            return write(node);
        } catch (Throwable failure) {
            LOG.error("Chisel async compatibility transform failed; preserving original bytecode.", failure);
            return original;
        }
    }

    private static byte[] patchMekanism(byte[] original) {
        try {
            ClassNode node = read(original);
            MethodNode ctor = findUniqueConstructor(node);
            if (ctor == null) {
                LOG.error("Refusing Mekanism async guard: expected constructor was not unique.");
                return original;
            }

            int patched = 0;
            for (AbstractInsnNode insn = ctor.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (!(insn instanceof MethodInsnNode)) continue;
                MethodInsnNode call = (MethodInsnNode) insn;
                if (call.getOpcode() == Opcodes.INVOKEVIRTUAL
                    && "cpw/mods/fml/common/eventhandler/EventBus".equals(call.owner)
                    && "register".equals(call.name)
                    && "(Ljava/lang/Object;)V".equals(call.desc)) {
                    ctor.instructions.set(call, new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        HOOK,
                        "registerForgeEventHandlerSynchronized",
                        "(Lcpw/mods/fml/common/eventhandler/EventBus;Ljava/lang/Object;)V",
                        false));
                    patched++;
                }
            }

            if (patched != 1) {
                LOG.error("Refusing Mekanism async guard: expected 1 EventBus.register call, found {}.", patched);
                return original;
            }
            CompatState.markMekanismPatched();
            LOG.info("Installed synchronized Mekanism TextureSubmap EventBus guard.");
            return write(node);
        } catch (Throwable failure) {
            LOG.error("Mekanism async compatibility transform failed; preserving original bytecode.", failure);
            return original;
        }
    }

    private static ClassNode read(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        return node;
    }

    private static byte[] write(ClassNode node) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static MethodNode findUniqueConstructor(ClassNode node) {
        MethodNode found = null;
        for (Object object : node.methods) {
            MethodNode method = (MethodNode) object;
            if (!"<init>".equals(method.name) || !CTOR_DESC.equals(method.desc)) continue;
            if (found != null) return null;
            found = method;
        }
        return found;
    }
}