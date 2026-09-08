import java.nio.file.*;
import org.objectweb.asm.*;
/** Only a controlled tile lookup; never used in the delivered mod. */
public final class WorldFixture {
    public static void main(String[] args) throws Exception {
        ClassWriter w=new ClassWriter(0);
        w.visit(52,Opcodes.ACC_PUBLIC,"fixture/ControlledWorld",null,"net/minecraft/world/World",null);
        w.visitField(Opcodes.ACC_PUBLIC,"current","Lnet/minecraft/tileentity/TileEntity;",null,null).visitEnd();
        MethodVisitor m=w.visitMethod(Opcodes.ACC_PUBLIC,"func_147438_o","(III)Lnet/minecraft/tileentity/TileEntity;",null,null);
        m.visitCode();m.visitVarInsn(Opcodes.ALOAD,0);
        m.visitFieldInsn(Opcodes.GETFIELD,"fixture/ControlledWorld","current","Lnet/minecraft/tileentity/TileEntity;");
        m.visitInsn(Opcodes.ARETURN);m.visitMaxs(1,4);m.visitEnd();w.visitEnd();
        Path output=Paths.get(args[0],"fixture/ControlledWorld.class");Files.createDirectories(output.getParent());Files.write(output,w.toByteArray());
    }
}
