package com.akashic.thepatcher.module.forestry;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.launchwrapper.IClassTransformer;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

public final class ForestryGenomeC2Transformer
    implements IClassTransformer {

    private static final String TARGET =
        "forestry.core.genetics.Genome";
    private static final String METHOD =
        "getChromosomes";
    private static final String DESC =
        "(Lnet/minecraft/nbt/NBTTagCompound;"
            + "Lforestry/api/genetics/ISpeciesRoot;)"
            + "[Lforestry/api/genetics/IChromosome;";
    private static final String HELPER =
        "com/akashic/thepatcher/module/forestry/ForestryGenomeCompat";

    @Override
    public byte[] transform(
        String name,
        String transformedName,
        byte[] basicClass) {

        if (basicClass == null) return null;
        if (!TARGET.equals(transformedName)
            && !TARGET.equals(name)) {
            return basicClass;
        }

        try {
            return patch(basicClass);
        } catch (Throwable failure) {
            System.err.println(
                "[Akashic-ThePatcher] [Forestry-C2] "
                    + "UNIVERSAL PATCH REFUSED: "
                    + failure.getClass().getName()
                    + ": " + failure.getMessage());
            System.setProperty(
                "akashic.forestryUniversalPatch.failed",
                "true");
            return basicClass;
        }
    }

    private static byte[] patch(byte[] basicClass) {
        final ClassReader reader = new ClassReader(basicClass);
        final ClassNode node = new ClassNode();
        reader.accept(node, 0);

        MethodNode target = null;
        for (Object methodObject : node.methods) {
            final MethodNode method = (MethodNode) methodObject;
            if (METHOD.equals(method.name)
                && DESC.equals(method.desc)) {
                if (target != null) {
                    throw new IllegalStateException(
                        "duplicate getChromosomes target");
                }
                target = method;
            }
        }

        if (target == null) {
            throw new IllegalStateException(
                "getChromosomes target missing");
        }

        if (isAlreadyPatched(target)) {
            System.setProperty(
                "akashic.forestryUniversalPatch.applied",
                "true");
            return basicClass;
        }

        verifyOriginalShape(target);

        target.instructions.clear();
        target.tryCatchBlocks.clear();
        if (target.localVariables != null) {
            target.localVariables.clear();
        }

        final InsnList code = new InsnList();
        code.add(new VarInsnNode(Opcodes.ALOAD, 0));
        code.add(new VarInsnNode(Opcodes.ALOAD, 1));
        code.add(new MethodInsnNode(
            Opcodes.INVOKESTATIC,
            HELPER,
            "loadChromosomes",
            DESC,
            false));
        code.add(new InsnNode(Opcodes.ARETURN));
        target.instructions.add(code);
        target.maxStack = 2;
        target.maxLocals = 2;

        final ClassWriter writer =
            new ClassWriter(reader, 0);
        node.accept(writer);
        final byte[] result = writer.toByteArray();

        verifyPatched(result);

        System.setProperty(
            "akashic.forestryUniversalPatch.applied",
            "true");
        System.out.println(
            "[Akashic-ThePatcher] [Forestry-C2] "
                + "Universal Genome.getChromosomes rewrite applied.");
        return result;
    }

    private static void verifyOriginalShape(MethodNode method) {
        final int required =
            Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC;
        if ((method.access & required) != required) {
            throw new IllegalStateException(
                "target access flags changed: 0x"
                    + Integer.toHexString(method.access));
        }

        final Set<String> strings = new HashSet<String>();
        boolean loadChromosome = false;
        boolean clearInvalid = false;
        boolean karyotypeKey = false;

        for (AbstractInsnNode insn = method.instructions.getFirst();
             insn != null;
             insn = insn.getNext()) {

            if (insn instanceof LdcInsnNode) {
                final Object value =
                    ((LdcInsnNode) insn).cst;
                if (value instanceof String) {
                    strings.add((String) value);
                }
            } else if (insn instanceof MethodInsnNode) {
                final MethodInsnNode call =
                    (MethodInsnNode) insn;
                if ("forestry/core/genetics/Chromosome".equals(call.owner)
                    && "loadChromosomeFromNBT".equals(call.name)) {
                    loadChromosome = true;
                }
                if ("forestry/api/genetics/ISpeciesRoot".equals(call.owner)
                    && "getKaryotypeKey".equals(call.name)) {
                    karyotypeKey = true;
                }
            } else if (insn instanceof FieldInsnNode) {
                final FieldInsnNode field =
                    (FieldInsnNode) insn;
                if ("forestry/core/config/Config".equals(field.owner)
                    && "clearInvalidChromosomes".equals(field.name)
                    && "Z".equals(field.desc)) {
                    clearInvalid = true;
                }
            }
        }

        if (!strings.contains("Chromosomes")
            || !strings.contains("Slot")
            || !strings.contains(
                "Found Chromosome with invalid Alleles.\nNBTTagCompound: ")
            || !strings.contains(
                "\nSee config option \"genetics.clear.invalid.chromosomes\".\nMissing: ")
            || !loadChromosome
            || !clearInvalid
            || !karyotypeKey) {

            throw new IllegalStateException(
                "Forestry 4.11.36 method fingerprint mismatch"
                    + " strings=" + strings
                    + " load=" + loadChromosome
                    + " clear=" + clearInvalid
                    + " key=" + karyotypeKey);
        }
    }

    private static boolean isAlreadyPatched(MethodNode method) {
        for (AbstractInsnNode insn = method.instructions.getFirst();
             insn != null;
             insn = insn.getNext()) {
            if (!(insn instanceof MethodInsnNode)) continue;
            final MethodInsnNode call =
                (MethodInsnNode) insn;
            if (HELPER.equals(call.owner)
                && "loadChromosomes".equals(call.name)
                && DESC.equals(call.desc)) {
                return true;
            }
        }
        return false;
    }

    private static void verifyPatched(byte[] bytes) {
        final ClassNode verify = new ClassNode();
        new ClassReader(bytes).accept(verify, 0);

        for (Object methodObject : verify.methods) {
            final MethodNode method = (MethodNode) methodObject;
            if (!METHOD.equals(method.name)
                || !DESC.equals(method.desc)) {
                continue;
            }

            int helperCalls = 0;
            int meaningful = 0;
            for (AbstractInsnNode insn = method.instructions.getFirst();
                 insn != null;
                 insn = insn.getNext()) {
                if (insn.getOpcode() >= 0) meaningful++;
                if (insn instanceof MethodInsnNode) {
                    MethodInsnNode call =
                        (MethodInsnNode) insn;
                    if (HELPER.equals(call.owner)
                        && "loadChromosomes".equals(call.name)
                        && DESC.equals(call.desc)) {
                        helperCalls++;
                    }
                }
            }

            if (helperCalls != 1 || meaningful != 4) {
                throw new IllegalStateException(
                    "patched wrapper verification failed: calls="
                        + helperCalls + " insns=" + meaningful);
            }
            return;
        }

        throw new IllegalStateException(
            "patched target disappeared");
    }
}