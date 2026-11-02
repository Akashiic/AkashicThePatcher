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
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicVerifier;

/**
 * Offline-only edit of AkashicThePatcherLoadingPlugin (core bundle, binary only) for beta.50:
 *   getASMTransformerClass  calls RecipeIndexTransformer.register(list) right after beta.47's
 *                           AngelicaGlLookupTransformer.register(list), so it is the last entry (both sides).
 *                           No branch or stack map frame is added.
 * Everything else is unchanged (the gates compare the rest instruction by instruction).
 */
public final class PatchBeta50Plugin {

    static final String PLUGIN = "com/akashic/thepatcher/core/AkashicThePatcherLoadingPlugin.class";
    static final String ANCHOR_OWNER = "com/akashic/thepatcher/module/angelica/AngelicaGlLookupTransformer";
    static final String[] REGISTER_OWNERS = {
        "com/akashic/thepatcher/module/recipeindex/RecipeIndexTransformer",
    };
    static final String REGISTER = "register";
    static final String REGISTER_DESC = "(Ljava/util/List;)V";

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
        System.out.println("BETA50_PATCH PASS bytes=" + patched.length);
    }

    static byte[] patchPlugin(byte[] original) throws Exception {
        final ClassNode node = new ClassNode();
        new ClassReader(original).accept(node, 0);
        check(node.version == 52, "unexpected class version " + node.version);

        final MethodNode register = method(node, "getASMTransformerClass", "()[Ljava/lang/String;");
        MethodInsnNode anchor = null;
        for (AbstractInsnNode insn = register.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (!(insn instanceof MethodInsnNode)) continue;
            final MethodInsnNode call = (MethodInsnNode) insn;
            for (String owner : REGISTER_OWNERS) {
                check(!owner.equals(call.owner), "already registers " + owner);
            }
            if (ANCHOR_OWNER.equals(call.owner) && REGISTER.equals(call.name)) {
                check(anchor == null, "anchor call found twice");
                anchor = call;
            }
        }
        check(anchor != null && anchor.getOpcode() == Opcodes.INVOKESTATIC && REGISTER_DESC.equals(anchor.desc),
            "anchor call missing");
        // ALOAD list; INVOKESTATIC AngelicaGlLookupTransformer.register(List)V  (beta.47's edit)
        final AbstractInsnNode listLoad = real(anchor.getPrevious());
        check(listLoad instanceof VarInsnNode && listLoad.getOpcode() == Opcodes.ALOAD, "unexpected anchor shape");

        final InsnList call = new InsnList();
        for (String owner : REGISTER_OWNERS) {
            call.add(new VarInsnNode(Opcodes.ALOAD, ((VarInsnNode) listLoad).var));
            call.add(new MethodInsnNode(Opcodes.INVOKESTATIC, owner, REGISTER, REGISTER_DESC, false));
        }
        register.instructions.insert(anchor, call);
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

    private PatchBeta50Plugin() {}
}
