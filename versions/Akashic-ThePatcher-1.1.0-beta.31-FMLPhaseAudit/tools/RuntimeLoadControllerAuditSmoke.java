package com.akashic.compatlab.tools;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.util.Arrays;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

import com.akashic.thepatcher.module.startupaudit.FmlPhaseAuditTransformer;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

public final class RuntimeLoadControllerAuditSmoke {

    private static final String ENTRY =
        "cpw/mods/fml/common/LoadController.class";

    private static final String HOOKS =
        "com/akashic/thepatcher/module/startupaudit/FmlPhaseAuditHooks";

    private RuntimeLoadControllerAuditSmoke() {}

    public static void main(String[] args)
        throws Exception {

        if (args.length != 1) {
            throw new IllegalArgumentException(
                "usage: <runtime-modpack.jar>");
        }

        byte[] original =
            readJarEntry(
                new File(args[0]),
                ENTRY);

        FmlPhaseAuditTransformer transformer =
            new FmlPhaseAuditTransformer();

        byte[] patched =
            transformer.transform(
                "cpw.mods.fml.common.LoadController",
                "cpw.mods.fml.common.LoadController",
                original);

        if (patched == null
            || Arrays.equals(original, patched)) {

            throw new IllegalStateException(
                "runtime LoadController was not transformed");
        }

        ClassNode node =
            new ClassNode();

        new ClassReader(patched)
            .accept(node, 0);

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

        requireCount(
            phase,
            HOOKS,
            "phaseEnter",
            1);

        requireCount(
            phase,
            HOOKS,
            "phaseExit",
            returns(phase));

        requireCount(
            mod,
            HOOKS,
            "modEnter",
            1);

        requireCount(
            mod,
            HOOKS,
            "modExit",
            returns(mod));

        requireCount(
            build,
            HOOKS,
            "spanEnter",
            1);

        requireCount(
            build,
            HOOKS,
            "spanExit",
            returns(build));

        requireCount(
            mod,
            "com/google/common/eventbus/EventBus",
            "post",
            1);

        requireCount(
            phase,
            "cpw/mods/fml/common/LoadController",
            "sendEventToModContainer",
            1);

        requireCount(
            build,
            "cpw/mods/fml/common/ModContainer",
            "registerBus",
            1);

        System.out.println(
            "BETA31_RUNTIME_LOADCONTROLLER_SMOKE PASS original="
                + original.length
                + " patched="
                + patched.length
                + " phaseReturns="
                + returns(phase)
                + " modReturns="
                + returns(mod));
    }

    private static void requireCount(
        MethodNode method,
        String owner,
        String name,
        int expected) {

        int actual =
            calls(
                method,
                owner,
                name);

        if (actual != expected) {
            throw new IllegalStateException(
                owner
                    + "."
                    + name
                    + " expected="
                    + expected
                    + " actual="
                    + actual);
        }
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

        throw new IllegalStateException(
            "missing "
                + name
                + desc);
    }

    private static byte[] readJarEntry(
        File jar,
        String name)
        throws Exception {

        JarFile file =
            new JarFile(jar);

        try {
            ZipEntry entry =
                file.getEntry(name);

            if (entry == null) {
                throw new IllegalStateException(
                    "missing jar entry "
                        + name);
            }

            InputStream input =
                file.getInputStream(entry);

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
        } finally {
            file.close();
        }
    }
}
