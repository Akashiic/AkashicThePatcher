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
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicVerifier;

/**
 * Offline-only edit of AkashicThePatcherLoadingPlugin (core bundle, binary only) for beta.43:
 *   getASMTransformerClass  calls HologramFastPathTransformer.register(list) right before the
 *                           "Registering N bundled transformer(s)" log, i.e. after every other entry
 *                           (the fast path must run after ForbiddenHologramTransformer). register()
 *                           checks the side itself, so no branch or stack map frame is added.
 * Everything else is unchanged (the gates compare the rest instruction by instruction).
 */
public final class PatchBeta43Classes {

    static final String PLUGIN = "com/akashic/thepatcher/core/AkashicThePatcherLoadingPlugin.class";
    static final String REGISTER_OWNER = "com/akashic/thepatcher/module/hologram/HologramFastPathTransformer";
    static final String REGISTER = "register";
    static final String REGISTER_DESC = "(Ljava/util/List;)V";
    static final String LOG_PREFIX = "Registering ";

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
        System.out.println("BETA43_PATCH PASS bytes=" + patched.length);
    }

    static byte[] patchPlugin(byte[] original) throws Exception {
        final ClassNode node = new ClassNode();
        new ClassReader(original).accept(node, 0);
        check(node.version == 52, "unexpected class version " + node.version);

        final MethodNode register = method(node, "getASMTransformerClass", "()[Ljava/lang/String;");
        LdcInsnNode log = null;
        int listVar = -1;
        for (AbstractInsnNode insn = register.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            check(!(insn instanceof MethodInsnNode && REGISTER_OWNER.equals(((MethodInsnNode) insn).owner)),
                "already registers the hologram fast path");
            if (insn.getOpcode() == Opcodes.ASTORE && listVar < 0) {
                listVar = ((VarInsnNode) insn).var; // the ArrayList created first
            }
            if (insn instanceof LdcInsnNode && LOG_PREFIX.equals(((LdcInsnNode) insn).cst)) {
                check(log == null, "log line found twice");
                log = (LdcInsnNode) insn;
            }
        }
        check(log != null && listVar == 1, "unexpected getASMTransformerClass shape (log=" + log + ", list=" + listVar + ")");
        // NEW StringBuilder; DUP; INVOKESPECIAL <init>; LDC "Registering "
        final AbstractInsnNode init = real(log.getPrevious());
        final AbstractInsnNode dup = init == null ? null : real(init.getPrevious());
        final AbstractInsnNode alloc = dup == null ? null : real(dup.getPrevious());
        check(init instanceof MethodInsnNode && "<init>".equals(((MethodInsnNode) init).name)
            && dup != null && dup.getOpcode() == Opcodes.DUP
            && alloc instanceof TypeInsnNode && alloc.getOpcode() == Opcodes.NEW
            && "java/lang/StringBuilder".equals(((TypeInsnNode) alloc).desc), "unexpected log shape");
        final InsnList call = new InsnList();
        call.add(new VarInsnNode(Opcodes.ALOAD, listVar));
        call.add(new MethodInsnNode(Opcodes.INVOKESTATIC, REGISTER_OWNER, REGISTER, REGISTER_DESC, false));
        register.instructions.insertBefore(alloc, call);
        new Analyzer<>(new BasicVerifier()).analyze(node.name, register);

        final ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    static AbstractInsnNode real(AbstractInsnNode insn) {
        while (insn != null && insn.getOpcode() < 0) insn = insn.getPrevious();
        return insn;
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

    private PatchBeta43Classes() {}
}
