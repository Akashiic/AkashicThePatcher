package com.akashic.thepatcher.module.hotspotaudit;

import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

public class HotspotAuditTransformerTest {

    @Test
    public void catalogContainsExpectedTargets() {
        int total = 0;

        String[] classes = {
            "com.mia.props.Props",
            "software.bernie.geckolib3.GeckoLib",
            "Reika.ChromatiCraft.ChromatiCraft",
            "noppes.npcs.client.ClientProxy",
            "mods.eln.config.ConfigHandler",
            "com.kuba6000.mobsinfo.ClientProxy"
        };

        for (String name : classes) {
            total +=
                HotspotAuditTransformer
                    .targetsForTest(name)
                    .size();
        }

        Assert.assertEquals(7, total);
        Assert.assertEquals(
            2,
            HotspotAuditTransformer
                .targetsForTest(
                    "Reika.ChromatiCraft.ChromatiCraft")
                .size());
    }

    @Test
    public void instrumentsSyntheticMethodAndPreservesBody() {
        ClassNode node = new ClassNode();
        node.version = Opcodes.V1_8;
        node.access = Opcodes.ACC_PUBLIC;
        node.name = "com/mia/props/Props";
        node.superName = "java/lang/Object";

        MethodNode method =
            new MethodNode(
                Opcodes.ACC_PUBLIC,
                "loadModels",
                "()V",
                null,
                null);

        method.instructions.add(
            new InsnNode(Opcodes.NOP));

        method.instructions.add(
            new InsnNode(Opcodes.RETURN));

        method.maxStack = 0;
        method.maxLocals = 1;
        node.methods.add(method);

        ClassWriter writer =
            new ClassWriter(
                ClassWriter.COMPUTE_MAXS);

        node.accept(writer);

        List<HotspotAuditTransformer.Target> targets =
            HotspotAuditTransformer.targetsForTest(
                "com.mia.props.Props");

        byte[] patched =
            HotspotAuditTransformer.patchClass(
                writer.toByteArray(),
                targets);

        ClassNode verify = new ClassNode();
        new ClassReader(patched).accept(verify, 0);

        MethodNode found =
            (MethodNode) verify.methods.get(0);

        int enter = 0;
        int exit = 0;
        int nop = 0;

        for (org.objectweb.asm.tree.AbstractInsnNode insn =
                 found.instructions.getFirst();
             insn != null;
             insn = insn.getNext()) {

            if (insn.getOpcode() == Opcodes.NOP) {
                nop++;
            }

            if (insn instanceof MethodInsnNode) {
                MethodInsnNode call =
                    (MethodInsnNode) insn;

                if ("spanEnter".equals(call.name)) {
                    enter++;
                }

                if ("spanExit".equals(call.name)) {
                    exit++;
                }
            }
        }

        Assert.assertEquals(1, enter);
        Assert.assertEquals(1, exit);
        Assert.assertEquals(1, nop);
    }
}
