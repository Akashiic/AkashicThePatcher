package com.akashic.thepatcher.module.agricraft;

import java.util.List;

import net.minecraft.launchwrapper.IClassTransformer;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.BasicVerifier;

import com.akashic.thepatcher.core.RuntimeSide;
import com.akashic.thepatcher.util.BundleLog;

/**
 * AgriCraft irrigation guard (beta.45).
 *
 * AgriCraft 1.7.10 (1.5.x, including the pack's 1.5.2-evernife-v1) only registers its irrigation
 * blocks - waterTank, waterChannel, waterChannelFull, channelValve, sprinkler, and their ItemBlocks -
 * when config/agricraft/Configuration.cfg has {@code "Disable Irrigation"=false}:
 *
 *   ConfigurationHandler.loadConfiguration  disableIrrigation = config.getBoolean("Disable Irrigation", ...)
 *   Blocks.init                             if (!ConfigurationHandler.disableIrrigation) { 5 blocks }
 *
 * A client whose config says true joins a server that has the blocks and FML's handshake ends with
 * "Fatally missing blocks and items" ("Failed to connect to server: there are 10 missing blocks and
 * items", the log lists "Found a missing id from the world AgriCraft:waterTank" and the other four).
 *
 * The single write of the static field goes through AgriCraftIrrigationHooks.clientIrrigationDisabled,
 * which answers false on the client: the blocks are always registered there. A client with blocks
 * the server lacks still connects (FML only refuses ids the client is missing), so this matches a
 * server either way. Only the client is touched; a dedicated server keeps its config value.
 *
 * Fails closed: the class must have exactly one PUTSTATIC of its static boolean disableIrrigation
 * and the edited method must pass the ASM verifier, else AgriCraft's bytecode is kept.
 * Kill-switch: -Dakashic.agricraftIrrigation.disable=true
 */
public final class AgriCraftIrrigationTransformer implements IClassTransformer {

    public static final String DISABLE_PROPERTY = "akashic.agricraftIrrigation.disable";

    static final String TARGET = "com.InfinityRaider.AgriCraft.handler.ConfigurationHandler";
    static final String FIELD = "disableIrrigation";
    private static final String HOOKS = "com/akashic/thepatcher/module/agricraft/AgriCraftIrrigationHooks";
    private static final String HOOK = "clientIrrigationDisabled";

    private static volatile boolean disabledLogged;

    /**
     * Called by AkashicThePatcherLoadingPlugin.getASMTransformerClass right after the hologram fast path's
     * register call, so this transformer is the last one in the list, on the client only.
     */
    public static void register(List<String> transformers) {
        if (RuntimeSide.isClient()) {
            transformers.add(AgriCraftIrrigationTransformer.class.getName());
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
                BundleLog.info("AgriCraft irrigation guard DISABLED by -D" + DISABLE_PROPERTY + "=true.");
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
                BundleLog.warn("AgriCraft irrigation guard skipped for " + target + ": " + result
                    + "; original bytecode retained.");
                return bytes;
            }
            final ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(writer);
            BundleLog.info("AgriCraft irrigation guard applied: " + target + " (the client always registers the"
                + " irrigation blocks, whatever \"Disable Irrigation\" says).");
            return writer.toByteArray();
        } catch (ThreadDeath | VirtualMachineError fatal) {
            throw fatal;
        } catch (Throwable error) {
            BundleLog.error("AgriCraft irrigation guard rejected " + target + "; original bytecode retained.", error);
            return bytes;
        }
    }

    /** Returns null when the class was patched, otherwise why it was left alone. */
    static String patch(ClassNode node) throws AnalyzerException {
        FieldNode field = null;
        for (Object fieldObject : node.fields) {
            final FieldNode candidate = (FieldNode) fieldObject;
            if (FIELD.equals(candidate.name)) {
                field = candidate;
            }
        }
        if (field == null || !"Z".equals(field.desc) || (field.access & Opcodes.ACC_STATIC) == 0) {
            return "no static boolean " + FIELD;
        }
        MethodNode owner = null;
        FieldInsnNode write = null;
        int writes = 0;
        for (Object methodObject : node.methods) {
            final MethodNode method = (MethodNode) methodObject;
            for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (insn instanceof MethodInsnNode && HOOKS.equals(((MethodInsnNode) insn).owner)) {
                    return "already patched";
                }
                if (insn.getOpcode() == Opcodes.PUTSTATIC) {
                    final FieldInsnNode put = (FieldInsnNode) insn;
                    if (node.name.equals(put.owner) && FIELD.equals(put.name) && "Z".equals(put.desc)) {
                        owner = method;
                        write = put;
                        writes++;
                    }
                }
            }
        }
        if (writes != 1) {
            return writes + " writes of " + FIELD + " (expected 1)";
        }
        // The value on the stack (the config's boolean) goes through the hook; no branch, no new frame.
        owner.instructions.insertBefore(write, new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, HOOK, "(Z)Z", false));
        new Analyzer(new BasicVerifier()).analyze(node.name, owner);
        return null;
    }
}
