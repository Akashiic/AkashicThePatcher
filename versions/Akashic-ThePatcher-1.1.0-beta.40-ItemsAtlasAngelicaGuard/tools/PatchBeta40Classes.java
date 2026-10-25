package com.akashic.compatlab.tools;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.jar.JarFile;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicVerifier;

/**
 * Offline-only edit of ItemsAtlasUploadBatcher (beta.18/19, shipped since then) for beta.40:
 *
 *  1. begin(String,int,int,int,int): right after the "textures/items" path check,
 *     {@code if (ItemsAtlasAngelicaGuard.bypassBatch(atlasPath)) return;}
 *  2. flush(State,String): right after the "STATE.get() != state" check,
 *     {@code if (ItemsAtlasAngelicaGuard.skipFlush(state.cancelledUploads, state.path, state.stagedCalls, reason))
 *     { STATE.remove(); startupBatchConsumed = true; return; }}
 *
 * Both insertion points are branch targets that already carry a stack map frame with the same locals, so the
 * inserted code adds one F_SAME frame each and leaves every other instruction unchanged (the gates compare).
 */
public final class PatchBeta40Classes {

    static final String BATCHER = "dev/akashic/asynciconcompat/upload/ItemsAtlasUploadBatcher";
    static final String STATE = BATCHER + "$State";
    static final String GUARD = "dev/akashic/asynciconcompat/upload/ItemsAtlasAngelicaGuard";

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("base.jar output.class");
        }
        final byte[] original;
        try (JarFile jar = new JarFile(args[0]); InputStream in = jar.getInputStream(jar.getJarEntry(BATCHER + ".class"))) {
            original = read(in);
        }
        final byte[] patched = patch(original);
        final Path output = Paths.get(args[1]);
        Files.createDirectories(output.getParent());
        if (Files.exists(output)) {
            throw new FileAlreadyExistsException(output.toString());
        }
        Files.write(output, patched, StandardOpenOption.CREATE_NEW);
        System.out.println("BETA40_PATCH PASS bytes=" + patched.length);
    }

    static byte[] patch(byte[] original) throws Exception {
        final ClassNode node = new ClassNode();
        new ClassReader(original).accept(node, 0);
        check(node.version == 52, "unexpected class version " + node.version);
        for (MethodNode m : node.methods) {
            for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                check(!(insn instanceof MethodInsnNode && ((MethodInsnNode) insn).owner.equals(GUARD)), "already patched");
            }
        }

        // 1. begin
        final MethodNode begin = method(node, "begin", "(Ljava/lang/String;IIII)V");
        final AbstractInsnNode consumed = first(begin, Opcodes.GETSTATIC, "startupBatchConsumed");
        checkFramedTarget(consumed);
        final LabelNode armed = new LabelNode();
        final InsnList bypass = new InsnList();
        bypass.add(new VarInsnNode(Opcodes.ALOAD, 0));
        bypass.add(new MethodInsnNode(Opcodes.INVOKESTATIC, GUARD, "bypassBatch", "(Ljava/lang/String;)Z", false));
        bypass.add(new JumpInsnNode(Opcodes.IFEQ, armed));
        bypass.add(new InsnNode(Opcodes.RETURN));
        bypass.add(armed);
        bypass.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
        begin.instructions.insertBefore(consumed, bypass);
        verify(node, begin);

        // 2. flush
        final MethodNode flush = method(node, "flush", "(L" + STATE + ";Ljava/lang/String;)V");
        AbstractInsnNode nano = null;
        for (AbstractInsnNode insn = flush.instructions.getFirst(); insn != null && nano == null; insn = insn.getNext()) {
            if (insn instanceof MethodInsnNode && ((MethodInsnNode) insn).name.equals("nanoTime")) nano = insn;
        }
        check(nano != null, "flush: nanoTime not found");
        checkFramedTarget(nano);
        final LabelNode upload = new LabelNode();
        final InsnList skip = new InsnList();
        skip.add(new VarInsnNode(Opcodes.ALOAD, 0));
        skip.add(new FieldInsnNode(Opcodes.GETFIELD, STATE, "cancelledUploads", "I"));
        skip.add(new VarInsnNode(Opcodes.ALOAD, 0));
        skip.add(new FieldInsnNode(Opcodes.GETFIELD, STATE, "path", "Ljava/lang/String;"));
        skip.add(new VarInsnNode(Opcodes.ALOAD, 0));
        skip.add(new FieldInsnNode(Opcodes.GETFIELD, STATE, "stagedCalls", "I"));
        skip.add(new VarInsnNode(Opcodes.ALOAD, 1));
        skip.add(new MethodInsnNode(Opcodes.INVOKESTATIC, GUARD, "skipFlush",
            "(ILjava/lang/String;ILjava/lang/String;)Z", false));
        skip.add(new JumpInsnNode(Opcodes.IFEQ, upload));
        skip.add(new FieldInsnNode(Opcodes.GETSTATIC, BATCHER, "STATE", "Ljava/lang/ThreadLocal;"));
        skip.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/ThreadLocal", "remove", "()V", false));
        skip.add(new InsnNode(Opcodes.ICONST_1));
        skip.add(new FieldInsnNode(Opcodes.PUTSTATIC, BATCHER, "startupBatchConsumed", "Z"));
        skip.add(new InsnNode(Opcodes.RETURN));
        skip.add(upload);
        skip.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
        flush.instructions.insertBefore(nano, skip);
        verify(node, flush);

        final ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    /** The anchor must directly follow a stack map frame (ClassReader order: label, line number, frame, insn). */
    static void checkFramedTarget(AbstractInsnNode anchor) {
        AbstractInsnNode prev = anchor.getPrevious();
        while (prev instanceof LineNumberNode) prev = prev.getPrevious();
        check(prev instanceof FrameNode, "anchor is not a framed branch target");
    }

    static AbstractInsnNode first(MethodNode method, int opcode, String name) {
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn.getOpcode() == opcode && insn instanceof FieldInsnNode && ((FieldInsnNode) insn).name.equals(name)) {
                return insn;
            }
        }
        throw new AssertionError(method.name + ": " + name + " not found");
    }

    static MethodNode method(ClassNode node, String name, String desc) {
        for (MethodNode m : node.methods) {
            if (m.name.equals(name) && m.desc.equals(desc)) return m;
        }
        throw new AssertionError("missing " + name + desc);
    }

    static void verify(ClassNode owner, MethodNode method) throws Exception {
        new Analyzer<>(new BasicVerifier()).analyze(owner.name, method);
    }

    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    static byte[] read(InputStream in) throws IOException {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final byte[] buffer = new byte[8192];
        for (int n; (n = in.read(buffer)) != -1;) out.write(buffer, 0, n);
        return out.toByteArray();
    }

    private PatchBeta40Classes() {}
}
