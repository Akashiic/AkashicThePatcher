package com.akashic.compatlab.tools;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

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

public final class PatchLoadingPluginAuditTransformer {

    private static final String ENTRY =
        "com/akashic/thepatcher/core/AkashicThePatcherLoadingPlugin.class";

    private static final String METHOD =
        "getASMTransformerClass";

    private static final String DESC =
        "()[Ljava/lang/String;";

    private PatchLoadingPluginAuditTransformer() {}

    public static void main(String[] args)
        throws Exception {

        if (args.length != 3) {
            throw new IllegalArgumentException(
                "usage: <input-jar> <transformer-class> <output-class>");
        }

        File input =
            new File(args[0]);

        String transformer =
            args[1];

        File output =
            new File(args[2]);

        byte[] original =
            readJarEntry(
                input,
                ENTRY);

        byte[] patched =
            patch(
                original,
                transformer);

        File parent =
            output.getParentFile();

        if (parent != null) {
            parent.mkdirs();
        }

        FileOutputStream stream =
            new FileOutputStream(output);

        try {
            stream.write(patched);
        } finally {
            stream.close();
        }

        System.out.println(
            "LOADING_PLUGIN_AUDIT_TRANSFORMER_PATCH PASS transformer="
                + transformer
                + " bytes="
                + patched.length);
    }

    static byte[] patch(
        byte[] original,
        String transformer) {

        ClassReader reader =
            new ClassReader(original);

        ClassNode node =
            new ClassNode();

        reader.accept(node, 0);

        MethodNode target = null;

        for (Object object : node.methods) {
            MethodNode method =
                (MethodNode) object;

            if (METHOD.equals(method.name)
                && DESC.equals(method.desc)) {

                if (target != null) {
                    throw new IllegalStateException(
                        "duplicate getASMTransformerClass");
                }

                target = method;
            }
        }

        if (target == null) {
            throw new IllegalStateException(
                "getASMTransformerClass target missing");
        }

        if (countTransformerConstant(
                target,
                transformer) > 0) {

            throw new IllegalStateException(
                "transformer already registered");
        }

        int originalReturns =
            countOpcode(
                target,
                Opcodes.ARETURN);

        if (originalReturns != 1) {
            throw new IllegalStateException(
                "unexpected ARETURN count="
                    + originalReturns);
        }

        int listVar = -1;

        for (AbstractInsnNode insn =
                 target.instructions.getFirst();
             insn != null;
             insn = insn.getNext()) {

            if (!(insn instanceof MethodInsnNode)) {
                continue;
            }

            MethodInsnNode call =
                (MethodInsnNode) insn;

            if (!"java/util/ArrayList".equals(call.owner)
                || !"<init>".equals(call.name)
                || !"()V".equals(call.desc)) {

                continue;
            }

            AbstractInsnNode next =
                nextReal(insn.getNext());

            if (!(next instanceof VarInsnNode)
                || next.getOpcode() != Opcodes.ASTORE) {

                throw new IllegalStateException(
                    "ArrayList construction shape changed");
            }

            if (listVar != -1) {
                throw new IllegalStateException(
                    "multiple ArrayList initialization candidates");
            }

            listVar =
                ((VarInsnNode) next).var;
        }

        if (listVar < 0) {
            throw new IllegalStateException(
                "transformer list local not found");
        }

        AbstractInsnNode store = null;

        for (AbstractInsnNode insn =
                 target.instructions.getFirst();
             insn != null;
             insn = insn.getNext()) {

            if (insn instanceof VarInsnNode
                && insn.getOpcode() == Opcodes.ASTORE
                && ((VarInsnNode) insn).var == listVar) {

                store = insn;
                break;
            }
        }

        if (store == null) {
            throw new IllegalStateException(
                "transformer list ASTORE missing");
        }

        InsnList injection =
            new InsnList();

        injection.add(
            new VarInsnNode(
                Opcodes.ALOAD,
                listVar));

        injection.add(
            new LdcInsnNode(
                transformer));

        injection.add(
            new MethodInsnNode(
                Opcodes.INVOKEINTERFACE,
                "java/util/List",
                "add",
                "(Ljava/lang/Object;)Z",
                true));

        injection.add(
            new InsnNode(
                Opcodes.POP));

        target.instructions.insert(
            store,
            injection);

        ClassWriter writer =
            new ClassWriter(
                reader,
                ClassWriter.COMPUTE_MAXS);

        node.accept(writer);

        byte[] result =
            writer.toByteArray();

        verify(
            result,
            transformer,
            originalReturns);

        return result;
    }

    private static void verify(
        byte[] bytes,
        String transformer,
        int expectedReturns) {

        ClassNode node =
            new ClassNode();

        new ClassReader(bytes)
            .accept(node, 0);

        MethodNode target = null;

        for (Object object : node.methods) {
            MethodNode method =
                (MethodNode) object;

            if (METHOD.equals(method.name)
                && DESC.equals(method.desc)) {

                target = method;
                break;
            }
        }

        if (target == null) {
            throw new IllegalStateException(
                "patched target missing");
        }

        if (countTransformerConstant(
                target,
                transformer) != 1) {

            throw new IllegalStateException(
                "transformer constant verification failed");
        }

        if (countOpcode(
                target,
                Opcodes.ARETURN)
            != expectedReturns) {

            throw new IllegalStateException(
                "return count changed");
        }

        boolean linkedAdd = false;

        for (AbstractInsnNode insn =
                 target.instructions.getFirst();
             insn != null;
             insn = insn.getNext()) {

            if (!(insn instanceof LdcInsnNode)) {
                continue;
            }

            Object value =
                ((LdcInsnNode) insn).cst;

            if (!transformer.equals(value)) {
                continue;
            }

            AbstractInsnNode next =
                nextReal(insn.getNext());

            if (next instanceof MethodInsnNode) {
                MethodInsnNode call =
                    (MethodInsnNode) next;

                if ("java/util/List".equals(call.owner)
                    && "add".equals(call.name)
                    && "(Ljava/lang/Object;)Z".equals(call.desc)) {

                    linkedAdd = true;
                }
            }
        }

        if (!linkedAdd) {
            throw new IllegalStateException(
                "transformer List.add linkage missing");
        }
    }

    private static int countTransformerConstant(
        MethodNode method,
        String transformer) {

        int count = 0;

        for (AbstractInsnNode insn =
                 method.instructions.getFirst();
             insn != null;
             insn = insn.getNext()) {

            if (insn instanceof LdcInsnNode
                && transformer.equals(
                    ((LdcInsnNode) insn).cst)) {

                count++;
            }
        }

        return count;
    }

    private static int countOpcode(
        MethodNode method,
        int opcode) {

        int count = 0;

        for (AbstractInsnNode insn =
                 method.instructions.getFirst();
             insn != null;
             insn = insn.getNext()) {

            if (insn.getOpcode() == opcode) {
                count++;
            }
        }

        return count;
    }

    private static AbstractInsnNode nextReal(
        AbstractInsnNode insn) {

        AbstractInsnNode current = insn;

        while (current != null
            && current.getOpcode() < 0) {

            current =
                current.getNext();
        }

        return current;
    }

    private static byte[] readJarEntry(
        File jar,
        String name)
        throws Exception {

        JarFile file =
            new JarFile(jar);

        try {
            ZipEntry entry =
                file.getEntry(name);

            if (entry == null) {
                throw new IllegalStateException(
                    "missing jar entry "
                        + name);
            }

            InputStream input =
                file.getInputStream(entry);

            try {
                ByteArrayOutputStream output =
                    new ByteArrayOutputStream();

                byte[] buffer =
                    new byte[8192];

                int read;

                while ((read = input.read(buffer)) >= 0) {
                    output.write(
                        buffer,
                        0,
                        read);
                }

                return output.toByteArray();
            } finally {
                input.close();
            }
        } finally {
            file.close();
        }
    }
}
