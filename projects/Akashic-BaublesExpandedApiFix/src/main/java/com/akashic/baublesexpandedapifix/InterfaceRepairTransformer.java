package com.akashic.baublesexpandedapifix;

import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraft.launchwrapper.Launch;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Adds {@code public default void onSlotContentsChanged(ItemStack, EntityLivingBase) {}} to
 * baubles.api.expanded.IBaubleExpanded when the loaded copy lacks it. That is exactly the body BaublesExpanded
 * 2.2.26-GTNH ships, so items that override it keep their override and every other item gets the same no-op the
 * correct API gives them. Any other class, or a copy that already has the method, is returned untouched.
 */
public final class InterfaceRepairTransformer implements IClassTransformer {
    static final String TARGET = "baubles.api.expanded.IBaubleExpanded";
    static final String METHOD = "onSlotContentsChanged";
    static final String DESC = "(Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/EntityLivingBase;)V";

    @Override
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null || !TARGET.equals(transformedName)) {
            return bytes;
        }
        try {
            return repair(bytes);
        } catch (Throwable t) {
            Log.warn("could not inspect " + TARGET + ", leaving it unchanged: " + t);
            return bytes;
        }
    }

    static byte[] repair(byte[] bytes) {
        ClassReader reader = new ClassReader(bytes);
        Scan scan = new Scan();
        reader.accept(scan, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        if (scan.hasMethod) {
            Log.info(TARGET + " already declares " + METHOD + ", nothing to do");
            return bytes;
        }
        if ((scan.access & Opcodes.ACC_INTERFACE) == 0) {
            Log.warn(TARGET + " is not an interface, leaving it unchanged");
            return bytes;
        }
        // Default methods need class version 52. Raising it is only safe when no existing method carries code
        // (old API copies are bare interfaces), because version 52 code must have stack map frames.
        final boolean bump = (scan.version & 0xFFFF) < Opcodes.V1_8;
        if (bump && scan.hasCode) {
            Log.warn(TARGET + " is a pre-Java-8 copy with method bodies, leaving it unchanged" + sources());
            return bytes;
        }
        ClassWriter writer = new ClassWriter(0);
        reader.accept(new ClassVisitor(Opcodes.ASM5, writer) {
            @Override
            public void visit(int version, int access, String name, String signature, String superName,
                              String[] interfaces) {
                super.visit(bump ? Opcodes.V1_8 : version, access, name, signature, superName, interfaces);
            }

            @Override
            public void visitEnd() {
                MethodVisitor mv = super.visitMethod(Opcodes.ACC_PUBLIC, METHOD, DESC, null, null);
                mv.visitCode();
                mv.visitInsn(Opcodes.RETURN);
                mv.visitMaxs(0, 3);
                mv.visitEnd();
                super.visitEnd();
            }
        }, 0);
        Log.info("loaded copy of " + TARGET + " lacks " + METHOD + "; added the empty default from BaublesExpanded"
                + " 2.2.26" + sources());
        return writer.toByteArray();
    }

    /** Lists every jar on the class path that carries the interface, so the stale copy can be found. */
    static String sources() {
        try {
            if (Launch.classLoader == null) {
                return "";
            }
            List<String> urls = new ArrayList<String>();
            Enumeration<URL> e = Launch.classLoader.getResources(TARGET.replace('.', '/') + ".class");
            while (e.hasMoreElements()) {
                urls.add(e.nextElement().toString());
            }
            return " (copies on the class path, first one wins: " + urls + ")";
        } catch (Throwable t) {
            return "";
        }
    }

    static final class Scan extends ClassVisitor {
        int version;
        int access;
        boolean hasMethod;
        boolean hasCode;

        Scan() {
            super(Opcodes.ASM5);
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName,
                          String[] interfaces) {
            this.version = version;
            this.access = access;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String signature,
                                         String[] exceptions) {
            if (METHOD.equals(name) && DESC.equals(desc)) {
                hasMethod = true;
            }
            if ((access & Opcodes.ACC_ABSTRACT) == 0) {
                hasCode = true;
            }
            return null;
        }
    }
}
