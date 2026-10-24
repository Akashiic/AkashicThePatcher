package com.akashic.compatlab.tools;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/**
 * Offline-only: pads every Genome helper of ForestryGenomeCompat past HotSpot's
 * HugeMethodLimit (8000 bytes) with an unreachable block, so that with the
 * default DontCompileHugeMethods=true HotSpot never JIT-compiles or inlines them.
 *
 * Inserted at the head of each method (all frames kept expanded, F_NEW):
 *   GOTO body
 *   [frame: args | Throwable]  NOP x PAD  ATHROW     (dead, never executed)
 *   body: [frame: args | -]    original code
 * Runtime cost: one GOTO per call.
 */
public final class ApplyForestryCompileBarrier {
    private static final String ENTRY="com/akashic/thepatcher/module/forestry/ForestryGenomeCompat.class";
    private static final String OWNER="com/akashic/thepatcher/module/forestry/ForestryGenomeCompat";
    private static final List<String> TARGETS=Arrays.asList(
        "loadChromosomes","loadStoredChromosome","repairInvalidAlleles","validateChromosome","fillMissingChromosomes");
    static final int PAD=8192;

    private static byte[] read(InputStream in)throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];
        for(int n;(n=in.read(buffer))!=-1;)out.write(buffer,0,n);return out.toByteArray();
    }

    private static Object frameType(Type type) {
        switch(type.getSort()) {
            case Type.BOOLEAN: case Type.BYTE: case Type.CHAR: case Type.SHORT: case Type.INT: return Opcodes.INTEGER;
            case Type.FLOAT: return Opcodes.FLOAT;
            case Type.LONG: return Opcodes.LONG;
            case Type.DOUBLE: return Opcodes.DOUBLE;
            case Type.ARRAY: return type.getDescriptor();
            case Type.OBJECT: return type.getInternalName();
            default: throw new IllegalArgumentException(type.toString());
        }
    }

    static byte[] pad(byte[] original) {
        ClassNode node=new ClassNode();
        new ClassReader(original).accept(node,ClassReader.EXPAND_FRAMES);
        if(!OWNER.equals(node.name))throw new AssertionError("owner "+node.name);
        Set<String> seen=new HashSet<>();
        for(MethodNode method:node.methods) {
            if(!TARGETS.contains(method.name))continue;
            if(!seen.add(method.name))throw new AssertionError("overloaded target "+method.name);
            if((method.access&Opcodes.ACC_STATIC)==0)throw new AssertionError("non-static target "+method.name);
            for(AbstractInsnNode insn:method.instructions.toArray()) {
                if(insn.getOpcode()==Opcodes.NOP)throw new AssertionError("already padded "+method.name);
            }
            List<Object> locals=new ArrayList<>();
            for(Type arg:Type.getArgumentTypes(method.desc))locals.add(frameType(arg));
            if(locals.contains(Opcodes.LONG)||locals.contains(Opcodes.DOUBLE))throw new AssertionError("wide arg "+method.name);
            Object[] frameLocals=locals.toArray();
            LabelNode body=new LabelNode();
            InsnList head=new InsnList();
            head.add(new JumpInsnNode(Opcodes.GOTO,body));
            head.add(new FrameNode(Opcodes.F_NEW,frameLocals.length,frameLocals,1,new Object[]{"java/lang/Throwable"}));
            for(int i=0;i<PAD;i++)head.add(new InsnNode(Opcodes.NOP));
            head.add(new InsnNode(Opcodes.ATHROW));
            head.add(body);
            head.add(new FrameNode(Opcodes.F_NEW,frameLocals.length,frameLocals,0,new Object[0]));
            method.instructions.insert(head);
            method.maxStack=Math.max(method.maxStack,1);
        }
        if(!seen.containsAll(TARGETS))throw new AssertionError("targets missing: "+seen);
        ClassWriter writer=new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }

    public static void main(String[] args)throws Exception {
        if(args.length!=2)throw new IllegalArgumentException("base.jar output.class");
        byte[] original;
        try(JarFile jar=new JarFile(args[0]);InputStream in=jar.getInputStream(jar.getJarEntry(ENTRY))){original=read(in);}
        byte[] patched=pad(original);
        Map<String,Integer> lengths=com.akashic.thepatcher.module.forestry.ForestryC2RuntimeGuard.codeLengths(patched);
        List<String> below=com.akashic.thepatcher.module.forestry.ForestryC2RuntimeGuard.methodsBelowHugeLimit(lengths);
        if(!below.isEmpty())throw new AssertionError("below limit: "+below);
        if(!Arrays.equals(pad(original),patched))throw new AssertionError("non-deterministic");
        Path output=Paths.get(args[1]);
        if(Files.exists(output))throw new FileAlreadyExistsException(output.toString());
        Files.write(output,patched,StandardOpenOption.CREATE_NEW);
        System.out.println("FORESTRY_COMPILE_BARRIER PASS bytes="+original.length+"->"+patched.length+" codeLengths="+new TreeMap<>(lengths));
    }
}
