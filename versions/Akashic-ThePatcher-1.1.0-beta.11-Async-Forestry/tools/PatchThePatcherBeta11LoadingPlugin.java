package com.akashic.compatlab.tools;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

public final class PatchThePatcherBeta11LoadingPlugin {

    private static final String EXPECTED_SHA256 =
        "5a8bdc486e40c8d4bf31b5eb5c5d74d1fc4678af0fee988821c2d3dff882cf17";

    private static final String MODULE_GATE =
        "com/akashic/thepatcher/core/ModuleGate";

    private static final String GUARD =
        "com/akashic/thepatcher/module/forestry/ForestryC2RuntimeGuard";

    private PatchThePatcherBeta11LoadingPlugin() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("expected input.class output.class");
        }

        final Path input = Paths.get(args[0]);
        final Path output = Paths.get(args[1]);
        final byte[] original = Files.readAllBytes(input);

        final String hash = sha256(original);
        if (!EXPECTED_SHA256.equals(hash)) {
            throw new IllegalStateException(
                "beta.8 loading plugin fingerprint changed: " + hash);
        }

        final ClassNode node = new ClassNode();
        new ClassReader(original).accept(node, 0);

        int constructorPatches = 0;

        for (MethodNode method : node.methods) {
            if (!"<init>".equals(method.name) || !"()V".equals(method.desc)) {
                continue;
            }

            if (containsGuard(method)) {
                throw new IllegalStateException("Forestry C2 guard already installed");
            }

            MethodInsnNode bootstrap = null;
            for (AbstractInsnNode insn = method.instructions.getFirst();
                 insn != null;
                 insn = insn.getNext()) {
                if (!(insn instanceof MethodInsnNode)) continue;
                final MethodInsnNode call = (MethodInsnNode) insn;
                if (call.getOpcode() == Opcodes.INVOKESTATIC
                    && MODULE_GATE.equals(call.owner)
                    && "bootstrap".equals(call.name)
                    && "(Ljava/io/File;)V".equals(call.desc)) {
                    if (bootstrap != null) {
                        throw new IllegalStateException(
                            "multiple ModuleGate.bootstrap calls in constructor");
                    }
                    bootstrap = call;
                }
            }

            if (bootstrap == null) {
                throw new IllegalStateException(
                    "ModuleGate.bootstrap call not found in constructor");
            }

            final InsnList patch = new InsnList();
            patch.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                GUARD,
                "verify",
                "()V",
                false));

            method.instructions.insert(bootstrap, patch);
            constructorPatches++;
        }

        if (constructorPatches != 1) {
            throw new IllegalStateException(
                "expected one constructor patch, got " + constructorPatches);
        }

        final ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        final byte[] result = writer.toByteArray();

        Files.createDirectories(output.getParent());
        Files.write(output, result);

        final ClassNode verify = new ClassNode();
        new ClassReader(result).accept(verify, 0);
        int occurrences = 0;
        for (MethodNode method : verify.methods) {
            if ("<init>".equals(method.name)) {
                for (AbstractInsnNode insn = method.instructions.getFirst();
                     insn != null;
                     insn = insn.getNext()) {
                    if (!(insn instanceof MethodInsnNode)) continue;
                    MethodInsnNode call = (MethodInsnNode) insn;
                    if (call.getOpcode() == Opcodes.INVOKESTATIC
                        && GUARD.equals(call.owner)
                        && "verify".equals(call.name)
                        && "()V".equals(call.desc)) {
                        occurrences++;
                    }
                }
            }
        }

        if (occurrences != 1) {
            throw new IllegalStateException(
                "guard registration verification failed: " + occurrences);
        }

        System.out.println(
            "PATCH_THEPATCHER_BETA11_LOADING_PLUGIN PASS sha256="
                + sha256(result));
    }

    private static boolean containsGuard(MethodNode method) {
        for (AbstractInsnNode insn = method.instructions.getFirst();
             insn != null;
             insn = insn.getNext()) {
            if (!(insn instanceof MethodInsnNode)) continue;
            MethodInsnNode call = (MethodInsnNode) insn;
            if (GUARD.equals(call.owner)
                && "verify".equals(call.name)
                && "()V".equals(call.desc)) {
                return true;
            }
        }
        return false;
    }

    private static String sha256(byte[] bytes) throws Exception {
        final byte[] digest =
            MessageDigest.getInstance("SHA-256").digest(bytes);
        final StringBuilder out =
            new StringBuilder(digest.length * 2);
        for (byte value : digest) {
            out.append(String.format("%02x", value & 0xff));
        }
        return out.toString();
    }
}