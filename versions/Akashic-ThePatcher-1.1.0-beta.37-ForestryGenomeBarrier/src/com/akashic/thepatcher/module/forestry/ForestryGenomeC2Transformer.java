package com.akashic.thepatcher.module.forestry;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.launchwrapper.IClassTransformer;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Forestry Genome.getChromosomes / HotSpot 8u51 C2 crash - compile barrier (beta.37).
 *
 * HotSpot 25.51 (Java 8u51, the Mojang/Technic "jre-legacy") dies natively in a
 * C2 CompilerThread while compiling Forestry's
 * forestry.core.genetics.Genome.getChromosomes(NBTTagCompound, ISpeciesRoot).
 *
 * Up to beta.36 this transformer replaced that body with a reimplementation
 * (ForestryGenomeCompat), and only for one exact Forestry fingerprint. This
 * version keeps Forestry's own code byte-for-byte and only puts an unreachable
 * block in front of it:
 *
 *   GOTO body
 *   [frame: args | Throwable]  NOP x PAD  ATHROW     (dead, never executed)
 *   body: [frame: args | -]    Forestry's original instructions
 *
 * That makes the method longer than HotSpot's HugeMethodLimit (8000 bytes). With
 * the product default DontCompileHugeMethods=true, HotSpot never compiles it
 * (C1, C2 or OSR) and never inlines it into a caller, so the crashing
 * compilation cannot happen. This is the same effect as
 * -XX:CompileCommand=exclude, but it does not depend on the launcher. Runtime
 * cost: one GOTO per call. Behaviour: identical, because it is Forestry's code.
 *
 * Matching is by owner + name + descriptor only. The patch does not change
 * semantics, so it does not need to refuse other Forestry builds that have
 * the same method. Kill-switch: -Dakashic.forestryGenomeBarrier.disable=true
 */
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

    /** HotSpot's (develop, non-tunable in product builds) HugeMethodLimit. */
    public static final int HOTSPOT_HUGE_METHOD_LIMIT = 8000;
    /** GOTO (3) + PAD + ATHROW (1) = 8196: a multiple of 4, so tableswitch/lookupswitch padding is unchanged. */
    static final int PAD = 8192;

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
        if (Boolean.getBoolean("akashic.forestryGenomeBarrier.disable")) {
            System.err.println(
                "[Akashic-ThePatcher] [Forestry-C2] Genome compile barrier DISABLED by "
                    + "-Dakashic.forestryGenomeBarrier.disable=true");
            return basicClass;
        }

        try {
            return patch(basicClass);
        } catch (Throwable failure) {
            System.err.println(
                "[Akashic-ThePatcher] [Forestry-C2] "
                    + "GENOME COMPILE BARRIER REFUSED: "
                    + failure.getClass().getName()
                    + ": " + failure.getMessage());
            System.setProperty(
                "akashic.forestryGenomeBarrier.failed",
                "true");
            return basicClass;
        }
    }

    static byte[] patch(byte[] basicClass) throws IOException {
        final int before = codeLength(basicClass, METHOD, DESC);
        if (before < 0) {
            throw new IllegalStateException("getChromosomes target missing");
        }
        if (before > HOTSPOT_HUGE_METHOD_LIMIT) {
            System.setProperty("akashic.forestryGenomeBarrier.applied", "true");
            System.setProperty("akashic.forestryGenomeBarrier.codeLength", Integer.toString(before));
            return basicClass;
        }

        final ClassReader reader = new ClassReader(basicClass);
        final ClassNode node = new ClassNode();
        reader.accept(node, ClassReader.EXPAND_FRAMES);

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
        if ((target.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0) {
            throw new IllegalStateException(
                "target has no code: 0x" + Integer.toHexString(target.access));
        }

        final int originalInstructions = countReal(target);
        target.instructions.insert(barrier(node, target));
        target.maxStack = Math.max(target.maxStack, 1);

        final ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        final byte[] result = writer.toByteArray();

        final int after = codeLength(result, METHOD, DESC);
        if (after != before + 3 + PAD + 1) {
            throw new IllegalStateException(
                "unexpected patched code length " + before + " -> " + after);
        }
        final ClassNode check = new ClassNode();
        new ClassReader(result).accept(check, 0);
        for (Object methodObject : check.methods) {
            final MethodNode method = (MethodNode) methodObject;
            if (METHOD.equals(method.name) && DESC.equals(method.desc)
                && countReal(method) != originalInstructions + 1 + PAD + 1) {
                throw new IllegalStateException("original body not preserved");
            }
        }

        System.setProperty("akashic.forestryGenomeBarrier.applied", "true");
        System.setProperty("akashic.forestryGenomeBarrier.codeLength", Integer.toString(after));
        System.out.println(
            "[Akashic-ThePatcher] [Forestry-C2] Genome compile barrier applied: "
                + "Forestry's original getChromosomes kept unchanged, code "
                + before + " -> " + after + " bytes (> HugeMethodLimit "
                + HOTSPOT_HUGE_METHOD_LIMIT + "); HotSpot will never JIT-compile or inline it.");
        return result;
    }

    private static InsnList barrier(ClassNode owner, MethodNode method) {
        final boolean frames = (owner.version & 0xFFFF) >= Opcodes.V1_6;
        final List<Object> locals = new ArrayList<Object>();
        if ((method.access & Opcodes.ACC_STATIC) == 0) {
            locals.add(owner.name);
        }
        for (Type argument : Type.getArgumentTypes(method.desc)) {
            switch (argument.getSort()) {
                case Type.BOOLEAN:
                case Type.BYTE:
                case Type.CHAR:
                case Type.SHORT:
                case Type.INT: locals.add(Opcodes.INTEGER); break;
                case Type.FLOAT: locals.add(Opcodes.FLOAT); break;
                case Type.LONG: locals.add(Opcodes.LONG); break;
                case Type.DOUBLE: locals.add(Opcodes.DOUBLE); break;
                case Type.ARRAY: locals.add(argument.getDescriptor()); break;
                default: locals.add(argument.getInternalName()); break;
            }
        }
        final Object[] frameLocals = locals.toArray();

        final LabelNode body = new LabelNode();
        final InsnList code = new InsnList();
        code.add(new JumpInsnNode(Opcodes.GOTO, body));
        if (frames) {
            code.add(new FrameNode(Opcodes.F_NEW, frameLocals.length, frameLocals,
                1, new Object[] { "java/lang/Throwable" }));
        }
        for (int i = 0; i < PAD; i++) {
            code.add(new InsnNode(Opcodes.NOP));
        }
        code.add(new InsnNode(Opcodes.ATHROW));
        code.add(body);
        if (frames) {
            code.add(new FrameNode(Opcodes.F_NEW, frameLocals.length, frameLocals,
                0, new Object[0]));
        }
        return code;
    }

    private static int countReal(MethodNode method) {
        int count = 0;
        for (AbstractInsnNode insn = method.instructions.getFirst();
             insn != null;
             insn = insn.getNext()) {
            if (insn.getOpcode() >= 0) count++;
        }
        return count;
    }

    /** Code attribute length of name+desc, or -1 if the method (or its code) is absent. */
    public static int codeLength(byte[] classBytes, String name, String desc) throws IOException {
        final DataInputStream in = new DataInputStream(new ByteArrayInputStream(classBytes));
        if (in.readInt() != 0xCAFEBABE) throw new IOException("not a class file");
        in.skipBytes(4);
        final int poolCount = in.readUnsignedShort();
        final String[] utf8 = new String[poolCount];
        for (int i = 1; i < poolCount; i++) {
            final int tag = in.readUnsignedByte();
            switch (tag) {
                case 1: utf8[i] = in.readUTF(); break;
                case 3: case 4: in.skipBytes(4); break;
                case 5: case 6: in.skipBytes(8); i++; break;
                case 7: case 8: case 16: case 19: case 20: in.skipBytes(2); break;
                case 9: case 10: case 11: case 12: case 17: case 18: in.skipBytes(4); break;
                case 15: in.skipBytes(3); break;
                default: throw new IOException("unknown constant tag " + tag);
            }
        }
        in.skipBytes(6);
        in.skipBytes(2 * in.readUnsignedShort());
        final int fields = in.readUnsignedShort();
        for (int i = 0; i < fields; i++) {
            in.skipBytes(6);
            final int attributes = in.readUnsignedShort();
            for (int a = 0; a < attributes; a++) {
                in.skipBytes(2);
                in.skipBytes(in.readInt());
            }
        }
        final int methods = in.readUnsignedShort();
        for (int i = 0; i < methods; i++) {
            in.skipBytes(2);
            final String methodName = utf8[in.readUnsignedShort()];
            final String methodDesc = utf8[in.readUnsignedShort()];
            final boolean match = name.equals(methodName) && desc.equals(methodDesc);
            final int attributes = in.readUnsignedShort();
            for (int a = 0; a < attributes; a++) {
                final String attributeName = utf8[in.readUnsignedShort()];
                final int length = in.readInt();
                if (match && "Code".equals(attributeName)) {
                    in.skipBytes(4);
                    return in.readInt();
                }
                in.skipBytes(length);
            }
            if (match) return -1;
        }
        return -1;
    }
}
