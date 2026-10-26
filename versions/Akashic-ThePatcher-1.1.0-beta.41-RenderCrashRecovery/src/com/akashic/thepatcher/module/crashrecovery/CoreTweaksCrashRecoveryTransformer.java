package com.akashic.thepatcher.module.crashrecovery;

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
 * CoreTweaks crash handler x Angelica render state (beta.41).
 *
 * CoreTweaks' crash handler (tweak.crashhandler) keeps the game running after an
 * exception in the game loop: it writes the crash report, calls
 * CrashHandler.resetState() (GL state, Tessellator) and shows its error screen.
 * Angelica only resets its own render state through BetterCrashes
 * (BetterCrashesCompat -> RenderRecovery.resetAfterCrash), so after a CoreTweaks
 * recovery Angelica's state is whatever the exception left behind. An exception
 * inside a TESR leaves Iris' GbufferPrograms in the block-entity phase
 * (celeritas$renderTileEntities never reaches endBlockEntities), and every later
 * frame that draws the block outline crashes with
 * "GbufferPrograms in weird state, tried to call begin function when
 * entities = false, blockEntities = true, outline = false" until the game is
 * restarted.
 *
 * resetState() gets {@code AngelicaCrashRecoveryHooks.afterCoreTweaksReset()}
 * before each return. Fails closed: only the exact static resetState()V is
 * edited, and only if it passes the ASM verifier afterwards.
 * Kill-switch: -Dakashic.crashRecoveryBridge.disable=true
 */
public final class CoreTweaksCrashRecoveryTransformer
    implements IClassTransformer {

    public static final String DISABLE_PROPERTY =
        "akashic.crashRecoveryBridge.disable";

    static final String CRASH_HANDLER = "makamys.coretweaks.tweak.crashhandler.CrashHandler";

    private static final String RESET_STATE = "resetState";

    private static final String HOOKS = "com/akashic/thepatcher/module/crashrecovery/AngelicaCrashRecoveryHooks";
    private static final String HOOK = "afterCoreTweaksReset";

    private static volatile boolean disabledLogged;

    @Override
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        final String target = transformedName == null ? name : transformedName;
        if (!CRASH_HANDLER.equals(target)) {
            return bytes;
        }
        if (Boolean.getBoolean(DISABLE_PROPERTY)) {
            if (!disabledLogged) {
                disabledLogged = true;
                BundleLog.info("CoreTweaks crash recovery bridge DISABLED by -D" + DISABLE_PROPERTY + "=true.");
            }
            return bytes;
        }
        if (!RuntimeSide.isClient()) {
            return bytes;
        }
        try {
            final ClassNode node = new ClassNode();
            new ClassReader(bytes).accept(node, 0);
            final String result = patch(node);
            if (result != null) {
                BundleLog.warn("CoreTweaks crash recovery bridge skipped for " + target + ": " + result
                    + "; original bytecode retained.");
                return bytes;
            }
            final ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(writer);
            BundleLog.info("CoreTweaks crash recovery bridge applied: " + target + "." + RESET_STATE
                + "() now also resets Angelica/Iris render state.");
            return writer.toByteArray();
        } catch (ThreadDeath | VirtualMachineError fatal) {
            throw fatal;
        } catch (Throwable error) {
            BundleLog.error("CoreTweaks crash recovery bridge rejected " + target + "; original bytecode retained.", error);
            return bytes;
        }
    }

    /** Returns null when the class was patched, otherwise why it was left alone. */
    static String patch(ClassNode node) throws AnalyzerException {
        MethodNode method = null;
        for (Object methodObject : node.methods) {
            final MethodNode candidate = (MethodNode) methodObject;
            if (RESET_STATE.equals(candidate.name) && "()V".equals(candidate.desc)) {
                method = candidate;
            }
        }
        if (method == null || (method.access & Opcodes.ACC_STATIC) == 0) {
            return "static " + RESET_STATE + "()V not found";
        }
        int returns = 0;
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof MethodInsnNode && HOOKS.equals(((MethodInsnNode) insn).owner)) {
                return "already patched";
            }
            if (insn.getOpcode() == Opcodes.RETURN) {
                method.instructions.insertBefore(insn, new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, HOOK, "()V", false));
                returns++;
            }
        }
        if (returns == 0) {
            return RESET_STATE + "() has no return";
        }
        new Analyzer(new BasicVerifier()).analyze(node.name, method);
        return null;
    }
}
