import java.nio.file.*;
import org.objectweb.asm.*;

/** Build-time overlay: retain every pre-existing method except one transformer-list return hook. */
public final class Overlay {
    static final String MODULE = "com/akashic/thepatcher/module/dimdoors/DimDoorsClientModule";
    public static void main(String[] args) throws Exception {
        ClassReader reader = new ClassReader(Files.readAllBytes(Paths.get(args[0])));
        ClassWriter writer = new ClassWriter(reader, 0);
        final boolean loader = reader.getClassName().endsWith("AkashicThePatcherLoadingPlugin");
        final int[] hooks = {0};
        reader.accept(new ClassVisitor(Opcodes.ASM5, writer) {
            @Override public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] ex) {
                MethodVisitor mv = super.visitMethod(access, name, desc, sig, ex);
                if (!loader || !name.equals("getASMTransformerClass") || !desc.equals("()[Ljava/lang/String;")) return mv;
                return new MethodVisitor(Opcodes.ASM5, mv) {
                    @Override public void visitInsn(int opcode) {
                        if (opcode == Opcodes.ARETURN) {
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, MODULE, "extendTransformers",
                                "([Ljava/lang/String;)[Ljava/lang/String;", false);
                            hooks[0]++;
                        }
                        super.visitInsn(opcode);
                    }
                };
            }
            @Override public void visitEnd() {
                if (!loader) {
                    MethodVisitor mv = super.visitMethod(Opcodes.ACC_PUBLIC, "akashicDimDoorsLoadComplete",
                        "(Lcpw/mods/fml/common/event/FMLLoadCompleteEvent;)V", null, null);
                    mv.visitAnnotation("Lcpw/mods/fml/common/Mod$EventHandler;", true).visitEnd();
                    mv.visitCode();
                    mv.visitMethodInsn(Opcodes.INVOKESTATIC, MODULE, "install", "()V", false);
                    mv.visitInsn(Opcodes.RETURN); mv.visitMaxs(0, 2); mv.visitEnd(); hooks[0]++;
                }
                super.visitEnd();
            }
        }, 0);
        if (hooks[0] != 1) throw new IllegalStateException("Unexpected baseline hook count: " + hooks[0]);
        Files.write(Paths.get(args[1]), writer.toByteArray());
    }
}
