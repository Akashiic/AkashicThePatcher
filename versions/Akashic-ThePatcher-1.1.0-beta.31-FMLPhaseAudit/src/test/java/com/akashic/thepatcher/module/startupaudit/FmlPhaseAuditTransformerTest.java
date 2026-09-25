package com.akashic.thepatcher.module.startupaudit;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Arrays;

import cpw.mods.fml.common.LoadController;

import org.junit.Assert;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

public class FmlPhaseAuditTransformerTest {

    private static final String HOOKS =
        "com/akashic/thepatcher/module/startupaudit/FmlPhaseAuditHooks";

    @Test
    public void patchesRealLoadControllerWithoutRemovingOriginalCalls()
        throws Exception {

        byte[] original =
            readClassBytes(LoadController.class);

        byte[] patched =
            FmlPhaseAuditTransformer.patch(original);

        Assert.assertFalse(
            Arrays.equals(original, patched));

        ClassNode node = new ClassNode();
        new ClassReader(patched).accept(node, 0);

        MethodNode phase =
            find(
                node,
                "propogateStateMessage",
                "(Lcpw/mods/fml/common/event/FMLEvent;)V");

        MethodNode mod =
            find(
                node,
                "sendEventToModContainer",
                "(Lcpw/mods/fml/common/event/FMLEvent;"
                    + "Lcpw/mods/fml/common/ModContainer;)V");

        MethodNode build =
            find(
                node,
                "buildModList",
                "(Lcpw/mods/fml/common/event/FMLLoadEvent;)V");

        Assert.assertEquals(
            1,
            calls(
                phase,
                HOOKS,
                "phaseEnter"));

        Assert.assertEquals(
            returns(phase),
            calls(
                phase,
                HOOKS,
                "phaseExit"));

        Assert.assertEquals(
            1,
            calls(
                mod,
                HOOKS,
                "modEnter"));

        Assert.assertEquals(
            returns(mod),
            calls(
                mod,
                HOOKS,
                "modExit"));

        Assert.assertEquals(
            1,
            calls(
                build,
                HOOKS,
                "spanEnter"));

        Assert.assertEquals(
            returns(build),
            calls(
                build,
                HOOKS,
                "spanExit"));

        Assert.assertEquals(
            1,
            calls(
                phase,
                "cpw/mods/fml/common/LoadController",
                "sendEventToModContainer"));

        Assert.assertEquals(
            1,
            calls(
                mod,
                "com/google/common/eventbus/EventBus",
                "post"));

        Assert.assertEquals(
            1,
            calls(
                build,
                "cpw/mods/fml/common/ModContainer",
                "registerBus"));
    }

    @Test
    public void patchIsIdempotent()
        throws Exception {

        byte[] original =
            readClassBytes(LoadController.class);

        byte[] first =
            FmlPhaseAuditTransformer.patch(original);

        byte[] second =
            FmlPhaseAuditTransformer.patch(first);

        Assert.assertArrayEquals(
            first,
            second);
    }

    private static byte[] readClassBytes(
        Class<?> type)
        throws Exception {

        String name =
            "/"
                + type.getName()
                    .replace('.', '/')
                + ".class";

        InputStream input =
            type.getResourceAsStream(name);

        Assert.assertNotNull(input);

        try {
            ByteArrayOutputStream output =
                new ByteArrayOutputStream();

            byte[] buffer =
                new byte[8192];

            int read;

            while ((read = input.read(buffer)) >= 0) {
                output.write(
                    buffer,
                    0,
                    read);
            }

            return output.toByteArray();
        } finally {
            input.close();
        }
    }

    private static MethodNode find(
        ClassNode node,
        String name,
        String desc) {

        for (Object object : node.methods) {
            MethodNode method =
                (MethodNode) object;

            if (name.equals(method.name)
                && desc.equals(method.desc)) {

                return method;
            }
        }

        throw new AssertionError(
            "missing method "
                + name
                + desc);
    }

    private static int calls(
        MethodNode method,
        String owner,
        String name) {

        int count = 0;

        for (AbstractInsnNode insn =
                 method.instructions.getFirst();
             insn != null;
             insn = insn.getNext()) {

            if (!(insn instanceof MethodInsnNode)) {
                continue;
            }

            MethodInsnNode call =
                (MethodInsnNode) insn;

            if (owner.equals(call.owner)
                && name.equals(call.name)) {

                count++;
            }
        }

        return count;
    }

    private static int returns(
        MethodNode method) {

        int count = 0;

        for (AbstractInsnNode insn =
                 method.instructions.getFirst();
             insn != null;
             insn = insn.getNext()) {

            if (insn.getOpcode()
                == org.objectweb.asm.Opcodes.RETURN) {

                count++;
            }
        }

        return count;
    }
}
