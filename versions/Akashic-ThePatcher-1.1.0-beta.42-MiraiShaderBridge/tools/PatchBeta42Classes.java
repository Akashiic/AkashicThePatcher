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
 * Offline-only edit of AkashicThePatcherLoadingPlugin (core bundle, binary only) for beta.42:
 *   getASMTransformerClass  registers MiraiShaderBridgeTransformer and AngelicaShaderPackFolderTransformer on
 *                           the client, right after CoreTweaksCrashRecoveryTransformer (beta.41's last client
 *                           entry before the ModuleGate-controlled ones); the add() call is copied from that entry;
 *   injectData              calls ChromaPlayerApiPreloadFix.arm() (it checks the side itself) before its
 *                           single return, so no branch or stack map frame is added.
 * Everything else is unchanged (the gates compare the rest instruction by instruction).
 */
public final class PatchBeta42Classes {

    static final String PLUGIN = "com/akashic/thepatcher/core/AkashicThePatcherLoadingPlugin.class";
    static final String ANCHOR = "com.akashic.thepatcher.module.crashrecovery.CoreTweaksCrashRecoveryTransformer";
    static final String[] ADDED = {
        "com.akashic.thepatcher.module.mirai.MiraiShaderBridgeTransformer",
        "com.akashic.thepatcher.module.angelica.AngelicaShaderPackFolderTransformer",
    };
    static final String ARM_OWNER = "com/akashic/thepatcher/module/chroma/ChromaPlayerApiPreloadFix";
    static final String ARM = "arm";

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
        System.out.println("BETA42_PATCH PASS bytes=" + patched.length);
    }

    static byte[] patchPlugin(byte[] original) throws Exception {
        final ClassNode node = new ClassNode();
        new ClassReader(original).accept(node, 0);
        check(node.version == 52, "unexpected class version " + node.version);

        // 1. registration
        final MethodNode register = method(node, "getASMTransformerClass", "()[Ljava/lang/String;");
        LdcInsnNode anchor = null;
        for (AbstractInsnNode insn = register.instructions.getFirst(); insn != null; insn = insn.getNext()) {
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
        final InsnList entry = new InsnList();
        for (String added : ADDED) {
            entry.add(new VarInsnNode(Opcodes.ALOAD, ((VarInsnNode) listLoad).var));
            entry.add(new LdcInsnNode(added));
            entry.add(new MethodInsnNode(add.getOpcode(), add.owner, add.name, add.desc, add.itf));
            entry.add(new InsnNode(Opcodes.POP));
        }
        register.instructions.insert(pop, entry);
        new Analyzer<>(new BasicVerifier()).analyze(node.name, register);

        // 2. injectData arms the folded-in ChromaPlayerApiPreload fix
        final MethodNode inject = method(node, "injectData", "(Ljava/util/Map;)V");
        AbstractInsnNode ret = null;
        for (AbstractInsnNode insn = inject.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            check(!(insn instanceof MethodInsnNode && ARM_OWNER.equals(((MethodInsnNode) insn).owner)), "already arms");
            if (insn.getOpcode() == Opcodes.RETURN) {
                check(ret == null, "injectData has more than one return");
                ret = insn;
            }
        }
        check(ret != null && ret.getNext() == null || ret != null && onlyPseudo(ret.getNext()), "unexpected injectData end");
        inject.instructions.insertBefore(ret, new MethodInsnNode(Opcodes.INVOKESTATIC, ARM_OWNER, ARM, "()V", false));
        new Analyzer<>(new BasicVerifier()).analyze(node.name, inject);

        final ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    static boolean onlyPseudo(AbstractInsnNode insn) {
        for (; insn != null; insn = insn.getNext()) {
            if (insn.getOpcode() >= 0) return false;
        }
        return true;
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

    private PatchBeta42Classes() {}
}
