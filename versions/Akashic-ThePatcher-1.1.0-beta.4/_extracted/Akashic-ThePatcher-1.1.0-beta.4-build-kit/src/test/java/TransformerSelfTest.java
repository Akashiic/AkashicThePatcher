package com.akashic.thepatcher.module.dimdoors;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import java.io.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import com.akashic.thepatcher.core.ModuleGate;

public final class TransformerSelfTest {
    static int checks;
    static void check(boolean condition,String why){checks++;if(!condition)throw new AssertionError(why);}
    static byte[] bytes(ZipFile zip,String path)throws Exception {
        try(InputStream in=zip.getInputStream(zip.getEntry(path));ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toByteArray();
        }
    }
    public static void main(String[] args)throws Exception {
        ModuleGate.bootstrap(new File(args[2]));
        DimDoorsClientTransformer t=new DimDoorsClientTransformer();
        try(ZipFile zip=new ZipFile(args[0])) {
            for(String name:new String[]{DimDoorsClientTransformer.POCKET,DimDoorsClientTransformer.TILE}) {
                byte[] old=bytes(zip,name+".class"), changed=t.transform(name,name.replace('/','.'),old);
                check(changed!=old && !Arrays.equals(old,changed),"target transformed");
                check(Arrays.equals(changed,t.transform(name,name.replace('/','.'),changed)),"idempotence");
                Path output=Paths.get(args[1],name+".class");Files.createDirectories(output.getParent());Files.write(output,changed);
                ClassNode before=new ClassNode(),after=new ClassNode();new ClassReader(old).accept(before,0);new ClassReader(changed).accept(after,0);
                int hooks=0;
                for(int i=0;i<before.methods.size();i++) {
                    MethodNode a=before.methods.get(i),b=after.methods.get(i);
                    boolean target=a.name.equals("getDimensionData") || a.name.equals("onDataPacket");
                    if(!target)check(Arrays.equals(methodBytes(a),methodBytes(b)),"unrelated method preserved: "+a.name);
                    for(AbstractInsnNode n:b.instructions.toArray())if(n instanceof MethodInsnNode
                        && ((MethodInsnNode)n).owner.equals(DimDoorsClientTransformer.HOOK))hooks++;
                }
                check(hooks==(name.endsWith("PocketManager")?2:1),"exact hook count");
                byte[] broken=Arrays.copyOf(old,12);
                check(t.transform(name,name.replace('/','.'),broken)==broken,"truncated bytecode rejected");
                ClassNode unexpected=new ClassNode();new ClassReader(old).accept(unexpected,0);
                for(MethodNode method:unexpected.methods)if(method.name.equals("onDataPacket")||method.name.equals("getDimensionData"))method.name+="Unrecognized";
                ClassWriter writer=new ClassWriter(0);unexpected.accept(writer);byte[] unknown=writer.toByteArray();
                check(t.transform(name,name.replace('/','.'),unknown)==unknown,"unknown ABI rejected");
            }
        }
        byte[] unrelated={1,2,3};check(t.transform("foo","foo",unrelated)==unrelated,"non-target untouched");
        check(t.transform("foo","foo",null)==null,"absent bytes untouched");
        System.out.println("TRANSFORMER_SELF_TEST_PASS checks="+checks);
    }
    static byte[] methodBytes(MethodNode method){
        ClassWriter writer=new ClassWriter(0);writer.visit(52,1,"TestMethod",null,"java/lang/Object",null);
        method.accept(writer);writer.visitEnd();return writer.toByteArray();
    }
}
