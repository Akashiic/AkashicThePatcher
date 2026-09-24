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
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

public final class PatchThePatcherBeta16LoadingPlugin {

    private static final String EXPECTED_SHA256 =
        "0cf67803c37e405538260c4895b3698315e0533d182a774f5ca2ee83d4ab2aa2";

    private static final String TRANSFORMER =
        "com.akashic.thepatcher.module.forestry.ForestryGenomeC2Transformer";

    private PatchThePatcherBeta16LoadingPlugin() {}

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
                "beta.15 loading plugin fingerprint changed: " + hash);
        }

        final ClassNode node = new ClassNode();
        new ClassReader(original).accept(node, 0);

        MethodNode target = null;
        for (Object methodObject : node.methods) {
            MethodNode method = (MethodNode) methodObject;
            if ("getASMTransformerClass".equals(method.name)
                && "()[Ljava/lang/String;".equals(method.desc)) {
                target = method;
                break;
            }
        }
        if (target == null) {
            throw new IllegalStateException(
                "getASMTransformerClass target missing");
        }
        if (containsTransformer(target)) {
            throw new IllegalStateException(
                "Forestry transformer already registered");
        }

        VarInsnNode listStore = null;
        for (AbstractInsnNode insn = target.instructions.getFirst();
             insn != null;
             insn = insn.getNext()) {
            if (insn instanceof VarInsnNode
                && insn.getOpcode() == Opcodes.ASTORE
                && ((VarInsnNode) insn).var == 1) {
                listStore = (VarInsnNode) insn;
                break;
            }
        }
        if (listStore == null) {
            throw new IllegalStateException(
                "transformer list local store not found");
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
        patch.add(new InsnNode(Opcodes.POP));

        target.instructions.insert(listStore, patch);

        final ClassWriter writer =
            new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        final byte[] result = writer.toByteArray();

        verify(result);

        Files.createDirectories(output.getParent());
        Files.write(output, result);

        System.out.println(
            "PATCH_THEPATCHER_BETA16_LOADING_PLUGIN PASS sha256="
                + sha256(result));
    }

    private static boolean containsTransformer(MethodNode method) {
        for (AbstractInsnNode insn = method.instructions.getFirst();
             insn != null;
             insn = insn.getNext()) {
            if (insn instanceof LdcInsnNode
                && TRANSFORMER.equals(((LdcInsnNode) insn).cst)) {
                return true;
            }
        }
        return false;
    }

    private static void verify(byte[] bytes) {
        final ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);

        int transformerConstants = 0;
        int addCalls = 0;
        for (Object methodObject : node.methods) {
            final MethodNode method = (MethodNode) methodObject;
            if (!"getASMTransformerClass".equals(method.name)
                || !"()[Ljava/lang/String;".equals(method.desc)) {
                continue;
            }

            for (AbstractInsnNode insn = method.instructions.getFirst();
                 insn != null;
                 insn = insn.getNext()) {
                if (insn instanceof LdcInsnNode
                    && TRANSFORMER.equals(((LdcInsnNode) insn).cst)) {
                    transformerConstants++;
                }
                if (insn instanceof MethodInsnNode) {
                    final MethodInsnNode call =
                        (MethodInsnNode) insn;
                    if (call.getOpcode() == Opcodes.INVOKEINTERFACE
                        && "java/util/List".equals(call.owner)
                        && "add".equals(call.name)
                        && "(Ljava/lang/Object;)Z".equals(call.desc)) {
                        addCalls++;
                    }
                }
            }
        }

        if (transformerConstants != 1) {
            throw new IllegalStateException(
                "transformer registration verification failed: "
                    + transformerConstants);
        }
        if (addCalls < 1) {
            throw new IllegalStateException(
                "List.add calls disappeared");
        }
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