package com.akashic.compatlab.tools;

import java.io.*;
import java.nio.file.*;
import java.util.jar.*;
import org.objectweb.asm.*;

/** Offline-only version metadata update for the candidate mod class. */
public final class PatchThePatcherVersion {
    private static final String ENTRY="com/akashic/thepatcher/AkashicThePatcherMod.class";
    private static byte[] read(InputStream in)throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];
        for(int n;(n=in.read(buffer))!=-1;)out.write(buffer,0,n);return out.toByteArray();
    }
    public static void main(String[] args)throws Exception {
        if(args.length!=4)throw new IllegalArgumentException("base.jar oldVersion newVersion output.class");
        final String oldVersion=args[1],newVersion=args[2];byte[] original;
        try(JarFile jar=new JarFile(args[0]);InputStream in=jar.getInputStream(jar.getJarEntry(ENTRY))){original=read(in);}
        final int[] fieldCount={0},annotationCount={0};ClassReader reader=new ClassReader(original);ClassWriter writer=new ClassWriter(0);
        ClassVisitor visitor=new ClassVisitor(Opcodes.ASM9,writer) {
            @Override public FieldVisitor visitField(int access,String name,String desc,String signature,Object value) {
                if("VERSION".equals(name)&&oldVersion.equals(value)){value=newVersion;fieldCount[0]++;}
                return super.visitField(access,name,desc,signature,value);
            }
            @Override public AnnotationVisitor visitAnnotation(String descriptor,boolean visible) {
                AnnotationVisitor base=super.visitAnnotation(descriptor,visible);
                if(!"Lcpw/mods/fml/common/Mod;".equals(descriptor))return base;
                return new AnnotationVisitor(Opcodes.ASM9,base) {
                    @Override public void visit(String name,Object value) {
                        if("version".equals(name)&&oldVersion.equals(value)){value=newVersion;annotationCount[0]++;}
                        super.visit(name,value);
                    }
                };
            }
        };
        reader.accept(visitor,0);
        if(fieldCount[0]!=1||annotationCount[0]!=1)
            throw new AssertionError("version shape mismatch field="+fieldCount[0]+" annotation="+annotationCount[0]);
        byte[] patched=writer.toByteArray();Path output=Paths.get(args[3]);Files.createDirectories(output.getParent());
        if(Files.exists(output))throw new FileAlreadyExistsException(output.toString());
        Files.write(output,patched,StandardOpenOption.CREATE_NEW);
        System.out.println("THEPATCHER_VERSION_PATCH PASS field="+fieldCount[0]+" annotation="+annotationCount[0]+" bytes="+patched.length);
    }
}
