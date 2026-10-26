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
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicVerifier;

/**
 * Offline-only edit of AkashicThePatcherLoadingPlugin (core bundle, binary only) for beta.41:
 * getASMTransformerClass registers, on the client, DragonApiResourcePackTransformer and
 * CoreTweaksCrashRecoveryTransformer right after NecroTempusAngelicaFontTransformer (beta.38's entry, inside the
 * RuntimeSide.isClient() branch). The add() call is copied from that entry; everything else is unchanged
 * (the gates compare the rest instruction by instruction).
 */
public final class PatchBeta41Classes {

    static final String PLUGIN = "com/akashic/thepatcher/core/AkashicThePatcherLoadingPlugin.class";
    static final String ANCHOR = "com.akashic.thepatcher.module.necrotempus.NecroTempusAngelicaFontTransformer";
    static final String[] ADDED = {
        "com.akashic.thepatcher.module.dragonapi.DragonApiResourcePackTransformer",
        "com.akashic.thepatcher.module.crashrecovery.CoreTweaksCrashRecoveryTransformer",
    };

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("base.jar output.class");
        }
        final byte[] original;
        try (JarFile jar = new JarFile(args[0]); InputStream in = jar.getInputStream(jar.getJarEntry(PLUGIN))) {
            original = read(in);
        }
        final byte[] patched = patchPlugin(original);
        final Path output = Paths.get(args[1]);
        Files.createDirectories(output.getParent());
        if (Files.exists(output)) {
            throw new FileAlreadyExistsException(output.toString());
        }
        Files.write(output, patched, StandardOpenOption.CREATE_NEW);
        System.out.println("BETA41_PATCH PASS bytes=" + patched.length);
    }

    static byte[] patchPlugin(byte[] original) throws Exception {
        final ClassNode node = new ClassNode();
        new ClassReader(original).accept(node, 0);
        check(node.version == 52, "unexpected class version " + node.version);
        final MethodNode method = method(node, "getASMTransformerClass", "()[Ljava/lang/String;");

        LdcInsnNode anchor = null;
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (!(insn instanceof LdcInsnNode)) continue;
            final Object cst = ((LdcInsnNode) insn).cst;
            for (String added : ADDED) check(!added.equals(cst), "already registered: " + added);
            if (ANCHOR.equals(cst)) {
                check(anchor == null, "anchor entry found twice");
                anchor = (LdcInsnNode) insn;
            }
        }
        check(anchor != null, "anchor entry missing");
        final AbstractInsnNode listLoad = anchor.getPrevious();
        final AbstractInsnNode addCall = anchor.getNext();
        final AbstractInsnNode pop = addCall == null ? null : addCall.getNext();
        check(listLoad instanceof VarInsnNode && listLoad.getOpcode() == Opcodes.ALOAD
            && addCall instanceof MethodInsnNode && ((MethodInsnNode) addCall).name.equals("add")
            && pop != null && pop.getOpcode() == Opcodes.POP, "unexpected anchor shape");
        final MethodInsnNode add = (MethodInsnNode) addCall;
        // The anchor must sit directly behind the RuntimeSide.isClient() branch (beta.38 put it there).
        AbstractInsnNode branch = listLoad.getPrevious();
        while (branch != null && branch.getOpcode() < 0) branch = branch.getPrevious();
        check(branch != null && branch.getOpcode() == Opcodes.IFEQ
            && branch.getPrevious() instanceof MethodInsnNode
            && ((MethodInsnNode) branch.getPrevious()).owner.equals("com/akashic/thepatcher/core/RuntimeSide")
            && ((MethodInsnNode) branch.getPrevious()).name.equals("isClient"), "anchor is not the first client entry");

        final InsnList entries = new InsnList();
        for (String added : ADDED) {
            entries.add(new VarInsnNode(Opcodes.ALOAD, ((VarInsnNode) listLoad).var));
            entries.add(new LdcInsnNode(added));
            entries.add(new MethodInsnNode(add.getOpcode(), add.owner, add.name, add.desc, add.itf));
            entries.add(new InsnNode(Opcodes.POP));
        }
        method.instructions.insert(pop, entries);

        new Analyzer<>(new BasicVerifier()).analyze(node.name, method);
        final ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    static MethodNode method(ClassNode node, String name, String desc) {
        for (MethodNode m : node.methods) {
            if (m.name.equals(name) && m.desc.equals(desc)) return m;
        }
        throw new AssertionError("missing " + name + desc);
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

    private PatchBeta41Classes() {}
}
