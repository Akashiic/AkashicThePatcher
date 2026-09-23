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
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

public final class PatchThePatcherBeta9LoadingPlugin {
    private static final String EXPECTED_SHA256 =
        "5a8bdc486e40c8d4bf31b5eb5c5d74d1fc4678af0fee988821c2d3dff882cf17";
    private static final String TRANSFORMER =
        "com.akashic.thepatcher.module.mps.MpsAngelicaCompatTransformer";

    private PatchThePatcherBeta9LoadingPlugin() {}    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("expected input.class output.class");
        }
        final Path input = Paths.get(args[0]);
        final Path output = Paths.get(args[1]);
        final byte[] original = Files.readAllBytes(input);
        final String hash = sha256(original);
        if (!EXPECTED_SHA256.equals(hash)) {
            throw new IllegalStateException(
                "loading plugin fingerprint changed: " + hash);
        }

        final ClassNode node = new ClassNode();
        new ClassReader(original).accept(node, 0);
        int patchedMethods = 0;

        for (MethodNode method : node.methods) {
            if (!"getASMTransformerClass".equals(method.name)
                || !"()[Ljava/lang/String;".equals(method.desc)) {
                continue;
            }

            if (containsTransformer(method)) {
                throw new IllegalStateException("beta9 transformer already registered");
            }

            JumpInsnNode clientGate = null;
            for (AbstractInsnNode insn = method.instructions.getFirst();
                 insn != null; insn = insn.getNext()) {                if (!(insn instanceof MethodInsnNode)) {
                    continue;
                }
                final MethodInsnNode call = (MethodInsnNode) insn;
                if (call.getOpcode() == Opcodes.INVOKESTATIC
                    && "com/akashic/thepatcher/core/RuntimeSide".equals(call.owner)
                    && "isClient".equals(call.name)
                    && "()Z".equals(call.desc)
                    && call.getNext() instanceof JumpInsnNode
                    && call.getNext().getOpcode() == Opcodes.IFEQ) {
                    clientGate = (JumpInsnNode) call.getNext();
                    break;
                }
            }

            if (clientGate == null) {
                throw new IllegalStateException("client registration gate not found");
            }

            final InsnList patch = new InsnList();
            patch.add(new VarInsnNode(Opcodes.ALOAD, 1));
            patch.add(new LdcInsnNode(TRANSFORMER));
            patch.add(new MethodInsnNode(
                Opcodes.INVOKEINTERFACE,
                "java/util/List",
                "add",
                "(Ljava/lang/Object;)Z",
                true));
            patch.add(new InsnNode(Opcodes.POP));            method.instructions.insert(clientGate, patch);
            patchedMethods++;
        }

        if (patchedMethods != 1) {
            throw new IllegalStateException(
                "expected one getASMTransformerClass method, got " + patchedMethods);
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
            if ("getASMTransformerClass".equals(method.name)) {
                for (AbstractInsnNode insn = method.instructions.getFirst();
                     insn != null; insn = insn.getNext()) {
                    if (insn instanceof LdcInsnNode
                        && TRANSFORMER.equals(((LdcInsnNode) insn).cst)) {
                        occurrences++;
                    }
                }
            }
        }
        if (occurrences != 1) {
            throw new IllegalStateException(
                "transformer registration verification failed: " + occurrences);
        }        System.out.println(
            "PATCH_THEPATCHER_BETA9_LOADING_PLUGIN PASS sha256=" + sha256(result));
    }

    private static boolean containsTransformer(MethodNode method) {
        for (AbstractInsnNode insn = method.instructions.getFirst();
             insn != null; insn = insn.getNext()) {
            if (insn instanceof LdcInsnNode
                && TRANSFORMER.equals(((LdcInsnNode) insn).cst)) {
                return true;
            }
        }
        return false;
    }

    private static String sha256(byte[] bytes) throws Exception {
        final byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
        final StringBuilder out = new StringBuilder(digest.length * 2);
        for (byte value : digest) {
            out.append(String.format("%02x", value & 0xff));
        }
        return out.toString();
    }
}
