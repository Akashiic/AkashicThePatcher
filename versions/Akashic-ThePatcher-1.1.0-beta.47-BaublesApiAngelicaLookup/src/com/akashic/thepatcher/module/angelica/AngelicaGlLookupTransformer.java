package com.akashic.thepatcher.module.angelica;

import java.util.List;

import net.minecraft.launchwrapper.IClassTransformer;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.BasicVerifier;

import com.akashic.thepatcher.core.RuntimeSide;
import com.akashic.thepatcher.util.BundleLog;

/**
 * Angelica 2.2.40 GL lookup bridge for AkashicBackpackCompat (beta.47).
 *
 * Angelica 2.2.40 split GLStateManager: glPushMatrix, glTranslatef, glEnable... moved to GLSMMatrices, GLSMEnable and
 * other classes (918 public static methods in 2.2.37, 86 in 2.2.40), listed in Angelica's own table
 * com.gtnewhorizons.angelica.glsm.redirect.GLSMOwners.METHODS. Angelica rewrites calls compiled against the old
 * GLStateManager, but not reflective lookups. AkashicBackpackCompat 1.0.0-rc1 resolves its GL entry points with
 * MethodHandles.Lookup.findStatic(GLStateManager.class, name, type) in GlApi's static initializer, so with 2.2.40
 * drawing a player wearing an Adventure Backpack (inventory screen, third person) crashes:
 *
 *   java.lang.ExceptionInInitializerError at br.com.akashic.backpackcompat.client.RenderHooks.renderPart(RenderHooks.java:19)
 *   Caused by: java.lang.IllegalStateException: Missing GL API glPushMatrix in class ...glsm.GLStateManager
 *   then: java.lang.NoClassDefFoundError: Could not initialize class br.com.akashic.backpackcompat.client.GlApi
 *
 * Every Lookup.findStatic call of GlApi goes through AngelicaGlLookupHooks.findStatic, which tries the original
 * lookup first and, only when GLStateManager no longer has the method, resolves it in the class GLSMOwners names.
 * Same stack shape (the receiver becomes the first argument), no branch, no new frame. Client only.
 *
 * Fails closed: GlApi must contain at least one Lookup.findStatic(Class, String, MethodType) call and every edited
 * method must pass the ASM verifier, else the class is kept.
 * Kill-switch: -Dakashic.angelicaGlLookup.disable=true
 */
public final class AngelicaGlLookupTransformer implements IClassTransformer {

    public static final String DISABLE_PROPERTY = "akashic.angelicaGlLookup.disable";

    static final String TARGET = "br.com.akashic.backpackcompat.client.GlApi";
    static final String LOOKUP = "java/lang/invoke/MethodHandles$Lookup";
    static final String FIND_STATIC = "findStatic";
    static final String FIND_STATIC_DESC =
        "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;";
    private static final String HOOKS = "com/akashic/thepatcher/module/angelica/AngelicaGlLookupHooks";
    private static final String HOOK_DESC = "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/Class;Ljava/lang/String;"
        + "Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;";

    private static volatile boolean disabledLogged;

    /**
     * Called by AkashicThePatcherLoadingPlugin.getASMTransformerClass right after the Baubles Expanded API guard
     * registers, so this transformer is the last one in the list, on the client only.
     */
    public static void register(List<String> transformers) {
        if (RuntimeSide.isClient()) {
            transformers.add(AngelicaGlLookupTransformer.class.getName());
        }
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
                BundleLog.info("Angelica GL lookup bridge DISABLED by -D" + DISABLE_PROPERTY + "=true.");
            }
            return bytes;
        }
        if (!RuntimeSide.isClient()) {
            return bytes;
        }
        try {
            final ClassNode node = new ClassNode();
            new ClassReader(bytes).accept(node, 0);
            final int calls = patch(node);
            if (calls < 0) {
                BundleLog.warn("Angelica GL lookup bridge skipped for " + target + ": "
                    + (calls == -1 ? "already patched" : "no MethodHandles.Lookup.findStatic call")
                    + "; original bytecode retained.");
                return bytes;
            }
            final ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(writer);
            BundleLog.info("Angelica GL lookup bridge applied: " + target + " (" + calls + " findStatic call(s); GL"
                + " methods that Angelica 2.2.40 moved out of GLStateManager are found in their new class).");
            return writer.toByteArray();
        } catch (ThreadDeath | VirtualMachineError fatal) {
            throw fatal;
        } catch (Throwable error) {
            BundleLog.error("Angelica GL lookup bridge rejected " + target + "; original bytecode retained.", error);
            return bytes;
        }
    }

    /** Returns the number of redirected calls, -1 when already patched, -2 when there is none. */
    static int patch(ClassNode node) throws AnalyzerException {
        int calls = 0;
        for (Object methodObject : node.methods) {
            final MethodNode method = (MethodNode) methodObject;
            boolean edited = false;
            for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (!(insn instanceof MethodInsnNode)) continue;
                final MethodInsnNode call = (MethodInsnNode) insn;
                if (HOOKS.equals(call.owner)) {
                    return -1;
                }
                if (call.getOpcode() == Opcodes.INVOKEVIRTUAL && LOOKUP.equals(call.owner)
                    && FIND_STATIC.equals(call.name) && FIND_STATIC_DESC.equals(call.desc)) {
                    call.setOpcode(Opcodes.INVOKESTATIC);
                    call.owner = HOOKS;
                    call.desc = HOOK_DESC;
                    call.itf = false;
                    calls++;
                    edited = true;
                }
            }
            if (edited) {
                new Analyzer(new BasicVerifier()).analyze(node.name, method);
            }
        }
        return calls == 0 ? -2 : calls;
    }
}
