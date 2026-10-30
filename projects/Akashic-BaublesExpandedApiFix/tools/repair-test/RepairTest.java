import com.akashic.baublesexpandedapifix.InterfaceRepairTransformer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import static org.objectweb.asm.Opcodes.*;

/**
 * Reproduces the server crash and checks the repair, in an isolated class loader:
 *  1. stale Java 6 IBaubleExpanded (no onSlotContentsChanged) + elytra-like item + SlotBauble-like caller
 *     -> NoSuchMethodError, the crash from the report;
 *  2. the same stale copy after the transformer -> the call returns normally;
 *  3. an item that overrides onSlotContentsChanged still gets its override after the repair;
 *  4. the real BaublesExpanded 2.2.26 interface (args[0]) comes back as the very same byte array.
 *
 *     java -cp <fix.jar>:asm-all.jar:launchwrapper.jar:log4j-api.jar:log4j-core.jar RepairTest.java <real IBaubleExpanded.class>
 */
public class RepairTest {
    static final String IFACE = "baubles/api/expanded/IBaubleExpanded";
    static final String DESC = "(Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/EntityLivingBase;)V";

    public static void main(String[] args) throws Exception {
        byte[] stale = staleInterface();

        expect("stale copy reproduces the crash", run(stale, item("Elytra", false)), "NoSuchMethodError");
        byte[] repaired = new InterfaceRepairTransformer().transform("x", "baubles.api.expanded.IBaubleExpanded",
                stale);
        expect("repaired copy: elytra slot change", run(repaired, item("Elytra", false)), "ok");
        expect("repaired copy: override still runs", run(repaired, item("Custom", true)), "override");

        byte[] real = Files.readAllBytes(Paths.get(args[0]));
        byte[] out = new InterfaceRepairTransformer().transform("x", "baubles.api.expanded.IBaubleExpanded", real);
        expect("real 2.2.26 interface untouched", out == real ? "same" : "changed", "same");
        byte[] other = new byte[] {1, 2, 3};
        expect("other classes untouched",
                new InterfaceRepairTransformer().transform("a", "net.minecraft.item.Item", other) == other
                        ? "same" : "changed", "same");
        System.out.println("ALL PASS");
    }

    static void expect(String what, String got, String want) {
        if (!got.equals(want)) {
            throw new AssertionError(what + ": got " + got + ", want " + want);
        }
        System.out.println("PASS " + what + " -> " + got);
    }

    static String run(byte[] iface, byte[] item) throws Exception {
        Map<String, byte[]> classes = new HashMap<>();
        classes.put("net.minecraft.item.ItemStack", empty("net/minecraft/item/ItemStack", 0));
        classes.put("net.minecraft.entity.EntityLivingBase", empty("net/minecraft/entity/EntityLivingBase", 0));
        classes.put("baubles.api.IBauble", empty("baubles/api/IBauble", ACC_INTERFACE | ACC_ABSTRACT));
        classes.put("baubles.api.expanded.IBaubleExpanded", iface);
        classes.put("test.Item", item);
        classes.put("test.Slot", slot());
        ClassLoader loader = new ClassLoader(null) {
            @Override
            protected Class<?> findClass(String name) throws ClassNotFoundException {
                byte[] b = classes.get(name);
                if (b == null) {
                    throw new ClassNotFoundException(name);
                }
                return defineClass(name, b, 0, b.length);
            }
        };
        try {
            Object result = loader.loadClass("test.Slot").getMethod("onSlotChanged", Object.class)
                    .invoke(null, loader.loadClass("test.Item").getConstructor().newInstance());
            return String.valueOf(result);
        } catch (java.lang.reflect.InvocationTargetException e) {
            return e.getCause().getClass().getSimpleName();
        }
    }

    /** An old API copy: Java 6, bare interface, only getBaubleTypes. */
    static byte[] staleInterface() {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(V1_6, ACC_PUBLIC | ACC_INTERFACE | ACC_ABSTRACT, IFACE, null, "java/lang/Object",
                new String[] {"baubles/api/IBauble"});
        cw.visitMethod(ACC_PUBLIC | ACC_ABSTRACT, "getBaubleTypes",
                "(Lnet/minecraft/item/ItemStack;)[Ljava/lang/String;", null, null).visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    /** Like ItemArmorElytra: implements IBaubleExpanded, optionally overrides onSlotContentsChanged. */
    static byte[] item(String tag, boolean override) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(V1_8, ACC_PUBLIC, "test/Item", null, "java/lang/Object", new String[] {IFACE});
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
        mv = cw.visitMethod(ACC_PUBLIC, "getBaubleTypes", "(Lnet/minecraft/item/ItemStack;)[Ljava/lang/String;",
                null, null);
        mv.visitCode();
        mv.visitInsn(ACONST_NULL);
        mv.visitInsn(ARETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
        if (override) {
            mv = cw.visitMethod(ACC_PUBLIC, "onSlotContentsChanged", DESC, null, null);
            mv.visitCode();
            mv.visitLdcInsn("override");
            mv.visitFieldInsn(PUTSTATIC, "test/Slot", "seen", "Ljava/lang/String;");
            mv.visitInsn(RETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }
        cw.visitEnd();
        return cw.toByteArray();
    }

    /** Same call shape as SlotBauble.onSlotChanged: checkcast + invokeinterface onSlotContentsChanged. */
    static byte[] slot() {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(V1_8, ACC_PUBLIC, "test/Slot", null, "java/lang/Object", null);
        cw.visitField(ACC_PUBLIC | ACC_STATIC, "seen", "Ljava/lang/String;", null, null).visitEnd();
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "onSlotChanged", "(Ljava/lang/Object;)Ljava/lang/Object;",
                null, null);
        mv.visitCode();
        mv.visitLdcInsn("ok");
        mv.visitFieldInsn(PUTSTATIC, "test/Slot", "seen", "Ljava/lang/String;");
        mv.visitVarInsn(ALOAD, 0);
        mv.visitTypeInsn(CHECKCAST, IFACE);
        mv.visitInsn(ACONST_NULL);
        mv.visitInsn(ACONST_NULL);
        mv.visitMethodInsn(INVOKEINTERFACE, IFACE, "onSlotContentsChanged", DESC, true);
        mv.visitFieldInsn(GETSTATIC, "test/Slot", "seen", "Ljava/lang/String;");
        mv.visitInsn(ARETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    static byte[] empty(String name, int extra) {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(V1_6, ACC_PUBLIC | extra, name, null, "java/lang/Object", null);
        cw.visitEnd();
        return cw.toByteArray();
    }
}
