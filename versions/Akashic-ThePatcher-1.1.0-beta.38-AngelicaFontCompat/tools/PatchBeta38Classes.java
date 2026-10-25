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
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicVerifier;

/**
 * Offline-only edits of two beta.37 classes whose original source is not
 * available (core bundle, binary only):
 *
 *  1. AkashicThePatcherLoadingPlugin.getASMTransformerClass: on the client,
 *     register NecroTempusAngelicaFontTransformer (inserted right after the
 *     RuntimeSide.isClient() branch, before the DimDoors entry).
 *  2. AngelicaDecorationPostProcessor: add the Angelica 2.2.37 drawString
 *     profile (fingerprint e79b0611..., locals curX=11, underlineEndX=42,
 *     strikethroughEndX=45) to PROFILES, and recognise an already installed
 *     endpoint fix for that layout in hasEndpointFix(MethodNode).
 *
 * Everything else in both classes is kept; the gates compare the rest
 * instruction by instruction.
 */
public final class PatchBeta38Classes {

    static final String PLUGIN = "com/akashic/thepatcher/core/AkashicThePatcherLoadingPlugin.class";
    static final String DECORATION = "com/akashic/thepatcher/module/rgb/AngelicaDecorationPostProcessor.class";
    static final String NEW_TRANSFORMER = "com.akashic.thepatcher.module.necrotempus.NecroTempusAngelicaFontTransformer";
    static final String ANCHOR_TRANSFORMER = "com.akashic.thepatcher.module.forestry.ForestryGenomeC2Transformer";
    static final String PROFILE_2_2_37 = "e79b061107b9b31255e08eb202a7aeb8040e9566d413335df551aed23566a088";
    static final int[] LAYOUT_2_2_37 = { 11, 42, 45 };
    static final int[][] OLD_LAYOUTS = { { 10, 39, 42 }, { 10, 41, 44 } };

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("base.jar outputDir");
        }
        final Path out = Paths.get(args[1]);
        try (JarFile jar = new JarFile(args[0])) {
            write(out.resolve(PLUGIN), patchPlugin(read(jar, PLUGIN)));
            write(out.resolve(DECORATION), patchDecoration(read(jar, DECORATION)));
        }
        System.out.println("BETA38_CLASS_PATCH PASS");
    }

    static byte[] patchPlugin(byte[] original) throws Exception {
        final ClassNode node = node(original);
        final MethodNode method = method(node, "getASMTransformerClass", "()[Ljava/lang/String;");

        // The list local and the exact add() call javac emitted, taken from the Forestry entry.
        VarInsnNode listLoad = null;
        MethodInsnNode addCall = null;
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof LdcInsnNode && ANCHOR_TRANSFORMER.equals(((LdcInsnNode) insn).cst)) {
                check(listLoad == null, "anchor entry found twice");
                listLoad = (VarInsnNode) insn.getPrevious();
                addCall = (MethodInsnNode) insn.getNext();
                check(listLoad.getOpcode() == Opcodes.ALOAD && addCall.name.equals("add")
                    && addCall.getNext().getOpcode() == Opcodes.POP, "unexpected anchor shape");
            }
            check(!(insn instanceof LdcInsnNode && NEW_TRANSFORMER.equals(((LdcInsnNode) insn).cst)), "already registered");
        }
        check(listLoad != null, "anchor entry missing");

        JumpInsnNode clientBranch = null;
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof MethodInsnNode && ((MethodInsnNode) insn).owner.equals("com/akashic/thepatcher/core/RuntimeSide")
                && ((MethodInsnNode) insn).name.equals("isClient")) {
                check(clientBranch == null, "more than one RuntimeSide.isClient() in getASMTransformerClass");
                check(insn.getNext().getOpcode() == Opcodes.IFEQ, "isClient() is not followed by IFEQ");
                clientBranch = (JumpInsnNode) insn.getNext();
            }
        }
        check(clientBranch != null, "client branch missing");

        final InsnList add = new InsnList();
        add.add(new VarInsnNode(Opcodes.ALOAD, listLoad.var));
        add.add(new LdcInsnNode(NEW_TRANSFORMER));
        add.add(new MethodInsnNode(addCall.getOpcode(), addCall.owner, addCall.name, addCall.desc, addCall.itf));
        add.add(new InsnNode(Opcodes.POP));
        method.instructions.insert(clientBranch, add);

        verify(node, method);
        final ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    static byte[] patchDecoration(byte[] original) throws Exception {
        final ClassNode node = node(original);

        // 1. PROFILES.put(PROFILE_2_2_37, new int[] {11, 42, 45}) at the end of <clinit>.
        final MethodNode clinit = method(node, "<clinit>", "()V");
        MethodInsnNode put = null;
        FieldInsnNode profiles = null;
        AbstractInsnNode ret = null;
        for (AbstractInsnNode insn = clinit.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof MethodInsnNode && ((MethodInsnNode) insn).name.equals("put")) {
                put = (MethodInsnNode) insn;
            }
            if (insn instanceof FieldInsnNode && ((FieldInsnNode) insn).name.equals("PROFILES") && insn.getOpcode() == Opcodes.GETSTATIC) {
                profiles = (FieldInsnNode) insn;
            }
            if (insn instanceof LdcInsnNode) {
                check(!PROFILE_2_2_37.equals(((LdcInsnNode) insn).cst), "profile already present");
            }
            if (insn.getOpcode() == Opcodes.RETURN) {
                check(ret == null, "more than one RETURN in <clinit>");
                ret = insn;
            }
        }
        check(put != null && profiles != null && ret != null, "unexpected <clinit> shape");
        final InsnList register = new InsnList();
        register.add(new FieldInsnNode(Opcodes.GETSTATIC, profiles.owner, profiles.name, profiles.desc));
        register.add(new LdcInsnNode(PROFILE_2_2_37));
        register.add(new InsnNode(Opcodes.ICONST_3));
        register.add(new IntInsnNode(Opcodes.NEWARRAY, Opcodes.T_INT));
        for (int i = 0; i < 3; i++) {
            register.add(new InsnNode(Opcodes.DUP));
            register.add(new InsnNode(Opcodes.ICONST_0 + i));
            register.add(new IntInsnNode(Opcodes.BIPUSH, LAYOUT_2_2_37[i]));
            register.add(new InsnNode(Opcodes.IASTORE));
        }
        register.add(new MethodInsnNode(put.getOpcode(), put.owner, put.name, put.desc, put.itf));
        register.add(new InsnNode(Opcodes.POP));
        clinit.instructions.insertBefore(ret, register);
        verify(node, clinit);

        // 2. hasEndpointFix(MethodNode): the two original layouts, then the 2.2.37 one.
        final MethodNode endpoint = method(node, "hasEndpointFix", "(Lorg/objectweb/asm/tree/MethodNode;)Z");
        final String helperDesc = "(Lorg/objectweb/asm/tree/MethodNode;III)Z";
        check(countCalls(endpoint, helperDesc) == OLD_LAYOUTS.length, "unexpected hasEndpointFix shape");
        int[] seen = new int[0];
        for (AbstractInsnNode insn = endpoint.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof IntInsnNode) {
                seen = append(seen, ((IntInsnNode) insn).operand);
            }
        }
        check(java.util.Arrays.equals(seen, new int[] { 10, 39, 42, 10, 41, 44 }), "unexpected hasEndpointFix layouts");
        final LabelNode found = new LabelNode();
        final InsnList body = new InsnList();
        final int[][] layouts = { OLD_LAYOUTS[0], OLD_LAYOUTS[1], LAYOUT_2_2_37 };
        for (int[] layout : layouts) {
            body.add(new VarInsnNode(Opcodes.ALOAD, 0));
            for (int value : layout) {
                body.add(new IntInsnNode(Opcodes.BIPUSH, value));
            }
            body.add(new MethodInsnNode(Opcodes.INVOKESTATIC, node.name, "hasEndpointFix", helperDesc, false));
            body.add(new JumpInsnNode(Opcodes.IFNE, found));
        }
        body.add(new InsnNode(Opcodes.ICONST_0));
        body.add(new InsnNode(Opcodes.IRETURN));
        body.add(found);
        body.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
        body.add(new InsnNode(Opcodes.ICONST_1));
        body.add(new InsnNode(Opcodes.IRETURN));
        endpoint.instructions = body;
        endpoint.localVariables = null;
        endpoint.tryCatchBlocks.clear();
        endpoint.maxStack = 4;
        endpoint.maxLocals = 1;
        verify(node, endpoint);

        final ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    static int countCalls(MethodNode method, String desc) {
        int count = 0;
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof MethodInsnNode && ((MethodInsnNode) insn).desc.equals(desc)) {
                count++;
            }
        }
        return count;
    }

    static int[] append(int[] values, int value) {
        final int[] copy = java.util.Arrays.copyOf(values, values.length + 1);
        copy[values.length] = value;
        return copy;
    }

    static void verify(ClassNode owner, MethodNode method) throws Exception {
        new Analyzer<>(new BasicVerifier()).analyze(owner.name, method);
    }

    static MethodNode method(ClassNode node, String name, String desc) {
        MethodNode found = null;
        for (MethodNode method : node.methods) {
            if (method.name.equals(name) && method.desc.equals(desc)) {
                check(found == null, name + desc + " is ambiguous");
                found = method;
            }
        }
        check(found != null, name + desc + " missing");
        return found;
    }

    static ClassNode node(byte[] bytes) {
        final ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        return node;
    }

    static byte[] read(JarFile jar, String entry) throws IOException {
        try (InputStream in = jar.getInputStream(jar.getJarEntry(entry))) {
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            final byte[] buffer = new byte[8192];
            for (int n; (n = in.read(buffer)) != -1;) {
                out.write(buffer, 0, n);
            }
            return out.toByteArray();
        }
    }

    static void write(Path path, byte[] data) throws IOException {
        Files.createDirectories(path.getParent());
        if (Files.exists(path)) {
            throw new FileAlreadyExistsException(path.toString());
        }
        Files.write(path, data, StandardOpenOption.CREATE_NEW);
    }

    static void check(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
