package com.akashic.compatlab.tools;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.Arrays;

import com.akashic.thepatcher.module.mps.MpsAngelicaCompatTransformer;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

public final class ApplyMpsAngelicaCompat {
    private static final String TARGET = "net.machinemuse.numina.render.RenderState";
    private static final String ANGELICA_OWNER =
        "com/gtnewhorizons/angelica/glsm/GLStateManager";
    private static final String LWJGL_OWNER = "org/lwjgl/opengl/GL11";

    private ApplyMpsAngelicaCompat() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            throw new IllegalArgumentException("mode input.class output.class");
        }        final String mode = args[0];
        final Path input = Paths.get(args[1]);
        final Path output = Paths.get(args[2]);
        final byte[] original = Files.readAllBytes(input);

        System.clearProperty("akashic.mpsAngelica218.disable");
        System.clearProperty("akashic.mpsAngelica218.forceForTests");

        byte[] supplied = original;
        boolean expectChanged = false;

        if ("exact".equals(mode)) {
            expectChanged = true;
        } else if ("force".equals(mode)) {
            System.setProperty("akashic.mpsAngelica218.forceForTests", "true");
            expectChanged = true;
        } else if ("pretransformed".equals(mode)) {
            supplied = redirectAttribOwners(original);
            expectChanged = true;
        } else if ("disabled".equals(mode)) {
            System.setProperty("akashic.mpsAngelica218.forceForTests", "true");
            System.setProperty("akashic.mpsAngelica218.disable", "true");
        } else if ("numinaMismatch".equals(mode)) {
            System.setProperty("akashic.mpsAngelica218.forceForTests", "true");
        } else if ("shapeMismatch".equals(mode)) {
            System.setProperty("akashic.mpsAngelica218.forceForTests", "true");
            supplied = breakOneTargetCall(original);
        } else if (!"angelicaMismatch".equals(mode)) {
            throw new IllegalArgumentException("unknown mode: " + mode);
        }        final MpsAngelicaCompatTransformer transformer =
            new MpsAngelicaCompatTransformer();
        final byte[] result = transformer.transform(TARGET, TARGET, supplied);

        Files.createDirectories(output.getParent());
        Files.write(output, result);

        final boolean changed = !Arrays.equals(supplied, result);
        if (expectChanged) {
            if (!changed
                || !MpsAngelicaCompatTransformer.isPatched()
                || MpsAngelicaCompatTransformer.patchedCallCount() != 6
                || !"PATCHED".equals(MpsAngelicaCompatTransformer.status())) {
                throw new IllegalStateException(mode + " transform failed: "
                    + MpsAngelicaCompatTransformer.status());
            }
        } else if (changed) {
            throw new IllegalStateException(mode + " unexpectedly changed bytes");
        }

        System.out.println("MPS_ANGELICA_COMPAT " + mode.toUpperCase()
            + "_PASS input=" + sha256(supplied)
            + " output=" + sha256(result)
            + " status=" + MpsAngelicaCompatTransformer.status()
            + " calls=" + MpsAngelicaCompatTransformer.patchedCallCount());
    }

    private static byte[] redirectAttribOwners(byte[] input) {
        final ClassNode node = new ClassNode();
        new ClassReader(input).accept(node, 0);        int changed = 0;
        for (MethodNode method : node.methods) {
            for (AbstractInsnNode insn = method.instructions.getFirst();
                 insn != null; insn = insn.getNext()) {
                if (!(insn instanceof MethodInsnNode)) {
                    continue;
                }
                final MethodInsnNode call = (MethodInsnNode) insn;
                if (call.getOpcode() == Opcodes.INVOKESTATIC
                    && LWJGL_OWNER.equals(call.owner)
                    && ("glPushAttrib".equals(call.name)
                        || "glPopAttrib".equals(call.name))) {
                    call.owner = ANGELICA_OWNER;
                    changed++;
                }
            }
        }
        if (changed < 6) {
            throw new IllegalStateException("pretransform expected >=6 attrib calls, got " + changed);
        }
        final ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static byte[] breakOneTargetCall(byte[] input) {
        final ClassNode node = new ClassNode();
        new ClassReader(input).accept(node, 0);
        boolean broken = false;
        for (MethodNode method : node.methods) {
            if (!"on2D".equals(method.name)) {
                continue;
            }            for (AbstractInsnNode insn = method.instructions.getFirst();
                 insn != null; insn = insn.getNext()) {
                if (insn instanceof MethodInsnNode) {
                    final MethodInsnNode call = (MethodInsnNode) insn;
                    if ("glPushAttrib".equals(call.name)) {
                        call.name = "glPushAttribBroken";
                        broken = true;
                        break;
                    }
                }
            }
        }
        if (!broken) {
            throw new IllegalStateException("shape mismatch mutation target not found");
        }
        final ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
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
