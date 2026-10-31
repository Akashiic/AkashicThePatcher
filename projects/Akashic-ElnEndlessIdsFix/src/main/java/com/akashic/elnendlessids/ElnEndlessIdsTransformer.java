package com.akashic.elnendlessids;

import net.minecraft.launchwrapper.IClassTransformer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Makes Electrical Age (1.24.6 to 1.24.8) work with EndlessIDs.
 *
 * <ol>
 *   <li>{@code PortableOreScannerItem$RenderStorage.generate} (client: Portable Ore Scanner, Auto Miner screen) read
 *       {@code ExtendedBlockStorage.getBlockLSBArray()}; EndlessIDs crashes the game on purpose when anything reads
 *       that array. The block-key computation (source lines 410-414) is replaced by
 *       {@code ElnEndlessIdsHooks.scannerBlockKey(storage, x, y, z)}.
 *   <li>{@code AutoMinerSlowProcess.checkIsOre} (server) indexes a 65536-entry array with
 *       {@code getIdFromBlock(block) + meta << 12}, which an id above 4095 runs past. The id goes through
 *       {@code ElnEndlessIdsHooks.oreMapBlockId} and the index through {@code ElnEndlessIdsHooks.oreMapIndex}.
 * </ol>
 *
 * Every rewrite checks the exact shape it expects first and leaves the class untouched (with a log line) otherwise.
 * Kill-switches: {@code -Dakashic.elnOreScanner.disable=true}, {@code -Dakashic.elnAutoMinerOreKey.disable=true}.
 */
public final class ElnEndlessIdsTransformer implements IClassTransformer {
    static final Logger LOG = LogManager.getLogger("Akashic-ElnEndlessIdsFix");

    static final String RENDER_STORAGE = "mods.eln.item.electricalitem.PortableOreScannerItem$RenderStorage";
    static final String AUTO_MINER_PROCESS = "mods.eln.transparentnode.autominer.AutoMinerSlowProcess";

    private static final String HOOKS = "com/akashic/elnendlessids/hook/ElnEndlessIdsHooks";
    private static final String EBS = "net/minecraft/world/chunk/storage/ExtendedBlockStorage";
    private static final String BLOCK = "net/minecraft/block/Block";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) {
            return null;
        }
        try {
            if (RENDER_STORAGE.equals(transformedName)) {
                if (Boolean.getBoolean("akashic.elnOreScanner.disable")) {
                    LOG.info("Electrical Age ore scanner fix disabled by -Dakashic.elnOreScanner.disable=true");
                    return basicClass;
                }
                return patch(basicClass, true);
            }
            if (AUTO_MINER_PROCESS.equals(transformedName)) {
                if (Boolean.getBoolean("akashic.elnAutoMinerOreKey.disable")) {
                    LOG.info("Electrical Age auto miner ore key fix disabled by -Dakashic.elnAutoMinerOreKey.disable=true");
                    return basicClass;
                }
                return patch(basicClass, false);
            }
        } catch (Throwable t) {
            LOG.error("Could not patch " + transformedName + "; left unchanged", t);
        }
        return basicClass;
    }

    private static byte[] patch(byte[] bytes, boolean scanner) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, ClassReader.EXPAND_FRAMES);
        String result = null;
        for (Object o : node.methods) {
            MethodNode m = (MethodNode) o;
            if (scanner && "generate".equals(m.name) && m.desc.startsWith("(Lnet/minecraft/world/World;")) {
                result = patchScanner(m);
            } else if (!scanner && "checkIsOre".equals(m.name) && "(Lmods/eln/misc/Coordinate;)Z".equals(m.desc)) {
                result = patchCheckIsOre(m);
            }
            if (result != null) {
                break;
            }
        }
        String what = scanner ? "PortableOreScannerItem$RenderStorage.generate" : "AutoMinerSlowProcess.checkIsOre";
        if (result == null || !result.startsWith("OK")) {
            LOG.warn("Electrical Age " + what + " not patched: " + (result == null ? "method not found" : result)
                    + ". Unsupported Electrical Age version?");
            return bytes;
        }
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(cw);
        LOG.info("Patched Electrical Age " + what + " for EndlessIDs (" + result + ")");
        return cw.toByteArray();
    }

    // ---------------------------------------------------------------------------------------------- ore scanner

    /**
     * Expected shape (Kotlin, PortableOreScannerItem.kt:410-414):
     * <pre>
     *   ALOAD storage; INVOKEVIRTUAL EBS.getBlockLSBArray()[B ... ILOAD x; ILOAD y; ILOAD z;
     *   INVOKEVIRTUAL EBS.getExtBlockMetadata(III)I ... ISTORE blockKey
     *   L_after:   (the target of the earlier "storage == null" IFNULL)
     * </pre>
     * Everything from {@code ALOAD storage} to {@code ISTORE blockKey} becomes
     * {@code ALOAD storage; ILOAD x; ILOAD y; ILOAD z; INVOKESTATIC scannerBlockKey; I2S; ISTORE blockKey}.
     */
    static String patchScanner(MethodNode m) {
        InsnList insns = m.instructions;
        MethodInsnNode lsb = null;
        int lsbCount = 0;
        for (Iterator<?> it = insns.iterator(); it.hasNext(); ) {
            AbstractInsnNode n = (AbstractInsnNode) it.next();
            if (isCall(n, Opcodes.INVOKEVIRTUAL, EBS, "func_76658_g", "getBlockLSBArray", "()[B")) {
                lsb = (MethodInsnNode) n;
                lsbCount++;
            }
        }
        if (lsbCount == 0) {
            return "no getBlockLSBArray call (already patched or changed)";
        }
        if (lsbCount != 1) {
            return lsbCount + " getBlockLSBArray calls, expected 1";
        }
        AbstractInsnNode start = prevReal(lsb);
        if (start == null || start.getOpcode() != Opcodes.ALOAD) {
            return "getBlockLSBArray receiver is not a local";
        }
        int storageVar = ((VarInsnNode) start).var;

        // The "if (storage != null)" guard right before: ALOAD storage; IFNULL L_after.
        LabelNode after = null;
        for (AbstractInsnNode n = start.getPrevious(); n != null; n = n.getPrevious()) {
            if (n.getOpcode() == Opcodes.IFNULL) {
                AbstractInsnNode load = prevReal(n);
                if (load != null && load.getOpcode() == Opcodes.ALOAD && ((VarInsnNode) load).var == storageVar) {
                    after = ((JumpInsnNode) n).label;
                }
                break;
            }
        }
        if (after == null) {
            return "storage null-check not found";
        }
        if (insns.indexOf(after) <= insns.indexOf(start)) {
            return "storage null-check jumps backwards";
        }
        AbstractInsnNode end = prevReal(after);
        if (end == null || end.getOpcode() != Opcodes.ISTORE) {
            return "block key store not found before the null-check target";
        }
        int keyVar = ((VarInsnNode) end).var;

        // The x/y/z locals, taken from the getExtBlockMetadata(x, y, z) call inside the range.
        MethodInsnNode meta = null;
        for (AbstractInsnNode n = start; n != end; n = n.getNext()) {
            if (isCall(n, Opcodes.INVOKEVIRTUAL, EBS, "func_76665_b", "getExtBlockMetadata", "(III)I")) {
                if (meta != null) {
                    return "two getExtBlockMetadata calls in the block key range";
                }
                meta = (MethodInsnNode) n;
            }
        }
        if (meta == null) {
            return "getExtBlockMetadata not found in the block key range";
        }
        int[] xyz = new int[3];
        AbstractInsnNode p = prevReal(meta);
        for (int i = 2; i >= 0; i--) {
            if (p == null || p.getOpcode() != Opcodes.ILOAD) {
                return "getExtBlockMetadata arguments are not three locals";
            }
            xyz[i] = ((VarInsnNode) p).var;
            p = prevReal(p);
        }
        if (p == null || p.getOpcode() != Opcodes.ALOAD || ((VarInsnNode) p).var != storageVar) {
            return "getExtBlockMetadata receiver is not the storage local";
        }

        // Collect the range and make sure nothing outside it jumps into it.
        Set<LabelNode> removedLabels = new HashSet<LabelNode>();
        List<AbstractInsnNode> range = new ArrayList<AbstractInsnNode>();
        for (AbstractInsnNode n = start; ; n = n.getNext()) {
            range.add(n);
            if (n instanceof LabelNode) {
                removedLabels.add((LabelNode) n);
            }
            if (n == end) {
                break;
            }
        }
        Set<AbstractInsnNode> rangeSet = new HashSet<AbstractInsnNode>(range);
        for (Iterator<?> it = insns.iterator(); it.hasNext(); ) {
            AbstractInsnNode n = (AbstractInsnNode) it.next();
            boolean inside = rangeSet.contains(n);
            for (LabelNode target : targets(n)) {
                if (!inside && removedLabels.contains(target)) {
                    return "a jump from outside targets the block key range";
                }
                if (inside && !removedLabels.contains(target)) {
                    return "the block key range jumps outside itself";
                }
            }
        }
        for (Object o : m.tryCatchBlocks) {
            TryCatchBlockNode tc = (TryCatchBlockNode) o;
            if (removedLabels.contains(tc.start) || removedLabels.contains(tc.end)
                    || removedLabels.contains(tc.handler)) {
                return "a try/catch block touches the block key range";
            }
        }

        InsnList repl = new InsnList();
        repl.add(new VarInsnNode(Opcodes.ALOAD, storageVar));
        repl.add(new VarInsnNode(Opcodes.ILOAD, xyz[0]));
        repl.add(new VarInsnNode(Opcodes.ILOAD, xyz[1]));
        repl.add(new VarInsnNode(Opcodes.ILOAD, xyz[2]));
        repl.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, "scannerBlockKey",
                "(L" + EBS + ";III)I", false));
        repl.add(new InsnNode(Opcodes.I2S));
        repl.add(new VarInsnNode(Opcodes.ISTORE, keyVar));
        insns.insertBefore(start, repl);
        for (AbstractInsnNode n : range) {
            insns.remove(n);
        }
        // Debug info of the removed temporaries (blockId and friends).
        for (Iterator<?> it = m.localVariables == null
                ? new ArrayList<Object>().iterator() : m.localVariables.iterator(); it.hasNext(); ) {
            LocalVariableNode lv = (LocalVariableNode) it.next();
            if (removedLabels.contains(lv.start) || removedLabels.contains(lv.end)) {
                it.remove();
            }
        }
        return "OK storage=" + storageVar + " xyz=" + xyz[0] + "," + xyz[1] + "," + xyz[2] + " key=" + keyVar
                + " removed=" + range.size();
    }

    // ---------------------------------------------------------------------------------------- auto miner (server)

    /**
     * Expected shape: {@code GETSTATIC OreColorMapping.INSTANCE; INVOKEVIRTUAL getMap()[F; ALOAD block;
     * INVOKESTATIC Block.getIdFromBlock; ...; FALOAD} (exactly one FALOAD and one getIdFromBlock in the method).
     */
    static String patchCheckIsOre(MethodNode m) {
        MethodInsnNode getId = null;
        AbstractInsnNode faload = null;
        int ids = 0;
        int faloads = 0;
        int hooked = 0;
        for (Iterator<?> it = m.instructions.iterator(); it.hasNext(); ) {
            AbstractInsnNode n = (AbstractInsnNode) it.next();
            if (isCall(n, Opcodes.INVOKESTATIC, BLOCK, "func_149682_b", "getIdFromBlock", "(L" + BLOCK + ";)I")) {
                getId = (MethodInsnNode) n;
                ids++;
            } else if (n.getOpcode() == Opcodes.FALOAD) {
                faload = n;
                faloads++;
            } else if (n instanceof MethodInsnNode && HOOKS.equals(((MethodInsnNode) n).owner)) {
                hooked++;
            }
        }
        if (hooked != 0) {
            return "already patched";
        }
        if (ids != 1 || faloads != 1) {
            return "found " + ids + " getIdFromBlock and " + faloads + " FALOAD, expected 1 and 1";
        }
        if (m.instructions.indexOf(getId) >= m.instructions.indexOf(faload)) {
            return "getIdFromBlock is not part of the ore map index";
        }
        boolean mapLoaded = false;
        for (AbstractInsnNode n = getId.getPrevious(); n != null; n = n.getPrevious()) {
            if (n instanceof MethodInsnNode && "getMap".equals(((MethodInsnNode) n).name)
                    && "()[F".equals(((MethodInsnNode) n).desc)) {
                mapLoaded = true;
                break;
            }
        }
        if (!mapLoaded) {
            return "OreColorMapping.getMap() not found before getIdFromBlock";
        }
        getId.owner = HOOKS;
        getId.name = "oreMapBlockId";
        getId.itf = false;
        m.instructions.insertBefore(faload, new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, "oreMapIndex", "(I)I",
                false));
        return "OK";
    }

    // ------------------------------------------------------------------------------------------------- helpers

    private static boolean isCall(AbstractInsnNode n, int opcode, String owner, String srg, String mcp, String desc) {
        if (n.getOpcode() != opcode || !(n instanceof MethodInsnNode)) {
            return false;
        }
        MethodInsnNode mi = (MethodInsnNode) n;
        return owner.equals(mi.owner) && desc.equals(mi.desc) && (srg.equals(mi.name) || mcp.equals(mi.name));
    }

    /** Previous instruction that is not a label, line number or frame. */
    private static AbstractInsnNode prevReal(AbstractInsnNode n) {
        AbstractInsnNode p = n.getPrevious();
        while (p != null && (p instanceof LabelNode || p instanceof LineNumberNode || p instanceof FrameNode)) {
            p = p.getPrevious();
        }
        return p;
    }

    private static List<LabelNode> targets(AbstractInsnNode n) {
        List<LabelNode> out = new ArrayList<LabelNode>();
        if (n instanceof JumpInsnNode) {
            out.add(((JumpInsnNode) n).label);
        } else if (n instanceof TableSwitchInsnNode) {
            out.add(((TableSwitchInsnNode) n).dflt);
            for (Object l : ((TableSwitchInsnNode) n).labels) {
                out.add((LabelNode) l);
            }
        } else if (n instanceof LookupSwitchInsnNode) {
            out.add(((LookupSwitchInsnNode) n).dflt);
            for (Object l : ((LookupSwitchInsnNode) n).labels) {
                out.add((LabelNode) l);
            }
        }
        return out;
    }
}
