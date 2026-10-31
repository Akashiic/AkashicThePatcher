package com.akashic.thepatcher.module.baubles;

import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import net.minecraft.launchwrapper.IClassTransformer;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.BasicVerifier;

import com.akashic.thepatcher.util.BundleLog;

/**
 * Baubles Expanded API guard (beta.47).
 *
 * Baubles Expanded 2.2.26 added a default method to its API and calls it from its own slot:
 *
 *   IBaubleExpanded   default void onSlotContentsChanged(ItemStack, EntityLivingBase) {}
 *   SlotBauble.onSlotChanged  ((IBaubleExpanded) item).onSlotContentsChanged(stack, player)
 *
 * Several jars of the pack embed the Baubles API ("Found API baubles.api ... embedded in ..."); when one of them
 * carries a pre-2.2.26 copy of IBaubleExpanded and sits earlier on the class path than BaublesExpanded-2.2.26-GTNH.jar,
 * that copy is the one loaded, and putting any IBaubleExpanded item (Et Futurum Requiem's elytra, its lanterns...)
 * in a bauble slot throws
 *
 *   java.lang.NoSuchMethodError: baubles.api.expanded.IBaubleExpanded.onSlotContentsChanged(...)
 *       at baubles.common.container.SlotBauble.func_75218_e(SlotBauble.java:85)
 *
 * Whatever jar the class comes from, the loaded interface gets the same empty default method when it lacks it, and
 * the log names every jar that carries a copy (the first one is the copy in use). A copy that already has the method
 * is left alone. Both sides: SlotBauble runs on the server too.
 *
 * Fails closed: the class must be an interface with the abstract getBaubleTypes(ItemStack) and no method named
 * onSlotContentsChanged. Kill-switch: -Dakashic.baublesExpandedApi.disable=true
 */
public final class BaublesExpandedApiTransformer implements IClassTransformer {

    public static final String DISABLE_PROPERTY = "akashic.baublesExpandedApi.disable";

    static final String TARGET = "baubles.api.expanded.IBaubleExpanded";
    static final String METHOD = "onSlotContentsChanged";
    static final String METHOD_DESC = "(Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/EntityLivingBase;)V";
    private static final String TYPES = "getBaubleTypes";
    private static final String TYPES_DESC = "(Lnet/minecraft/item/ItemStack;)[Ljava/lang/String;";
    private static final String ALREADY = "already has ";

    private static volatile boolean disabledLogged;

    /**
     * Called by AkashicThePatcherLoadingPlugin.getASMTransformerClass right after beta.46's projector slot guard
     * registers; registered on both sides.
     */
    public static void register(List<String> transformers) {
        transformers.add(BaublesExpandedApiTransformer.class.getName());
    }

    @Override
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        final String target = transformedName == null ? name : transformedName;
        if (!TARGET.equals(target)) {
            return bytes;
        }
        if (Boolean.getBoolean(DISABLE_PROPERTY)) {
            if (!disabledLogged) {
                disabledLogged = true;
                BundleLog.info("Baubles Expanded API guard DISABLED by -D" + DISABLE_PROPERTY + "=true.");
            }
            return bytes;
        }
        try {
            final ClassNode node = new ClassNode();
            new ClassReader(bytes).accept(node, 0);
            final String result = patch(node);
            if (result != null) {
                if (result.startsWith(ALREADY)) {
                    // The real Baubles Expanded 2.2.26+ copy: nothing to do.
                    return bytes;
                }
                BundleLog.warn("Baubles Expanded API guard skipped for " + target + ": " + result
                    + "; original bytecode retained.");
                return bytes;
            }
            final ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(writer);
            BundleLog.warn("Baubles Expanded API guard applied: the loaded " + target + " is an older copy without "
                + METHOD + " (Baubles Expanded 2.2.26 calls it when an item enters a bauble slot, e.g. Et Futurum's"
                + " elytra); the empty default method was added. Jars carrying this class, the first one is in use: "
                + sources() + ". Remove the embedded baubles/api/expanded classes from the jar(s) other than"
                + " BaublesExpanded.");
            return writer.toByteArray();
        } catch (ThreadDeath | VirtualMachineError fatal) {
            throw fatal;
        } catch (Throwable error) {
            BundleLog.error("Baubles Expanded API guard rejected " + target + "; original bytecode retained.", error);
            return bytes;
        }
    }

    /** Returns null when the class was patched, otherwise why it was left alone. */
    static String patch(ClassNode node) throws AnalyzerException {
        if ((node.access & Opcodes.ACC_INTERFACE) == 0) {
            return "not an interface";
        }
        boolean types = false;
        for (Object methodObject : node.methods) {
            final MethodNode method = (MethodNode) methodObject;
            if (METHOD.equals(method.name)) {
                return ALREADY + METHOD;
            }
            if (TYPES.equals(method.name) && TYPES_DESC.equals(method.desc)
                && (method.access & Opcodes.ACC_ABSTRACT) != 0) {
                types = true;
            }
        }
        if (!types) {
            return "no abstract " + TYPES + TYPES_DESC;
        }
        // Interface methods with code need class file version 52 (Java 8); the class has only abstract methods,
        // so raising an older version changes nothing else (no stack map frames in abstract methods).
        if ((node.version & 0xFFFF) < Opcodes.V1_8) {
            node.version = Opcodes.V1_8;
        }
        final MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC, METHOD, METHOD_DESC, null, null);
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.maxLocals = 3;
        method.maxStack = 0;
        new Analyzer(new BasicVerifier()).analyze(node.name, method);
        addMethod(node, method);
        return null;
    }

    /** Every class path entry with a copy of the interface, in class loading order. */
    private String sources() {
        final List<String> found = new ArrayList<>();
        try {
            final ClassLoader loader = getClass().getClassLoader();
            final Enumeration<URL> urls = loader.getResources(TARGET.replace('.', '/') + ".class");
            while (urls.hasMoreElements()) {
                String url = urls.nextElement().toString();
                final int bang = url.indexOf("!/");
                if (bang > 0) {
                    url = url.substring(0, bang);
                }
                found.add(url.substring(url.lastIndexOf('/') + 1));
            }
        } catch (Throwable error) {
            found.add("unknown (" + error + ")");
        }
        return found.isEmpty() ? "unknown" : found.toString();
    }

    @SuppressWarnings("unchecked")
    private static void addMethod(ClassNode node, MethodNode method) {
        node.methods.add(method);
    }
}
