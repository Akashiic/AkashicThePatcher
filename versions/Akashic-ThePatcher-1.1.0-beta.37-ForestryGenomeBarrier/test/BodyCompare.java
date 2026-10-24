import java.nio.file.*;
import java.util.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/**
 * Proves the barrier kept Forestry's code: canonical form of Genome.getChromosomes (instructions with
 * symbolic operands, labels renumbered by first use, line numbers, try/catch blocks, local variables)
 * of the original equals that of the patched method after skipping the GOTO + NOP x PAD + ATHROW head.
 * Also checks every OTHER method of the class is unchanged. args: original.class patched.class pad
 */
public final class BodyCompare {
    static MethodNode find(ClassNode node, String name, String desc) {
        for (Object o : node.methods) {
            MethodNode m = (MethodNode) o;
            if (m.name.equals(name) && m.desc.equals(desc)) return m;
        }
        throw new AssertionError("missing " + name + desc);
    }

    static List<String> canonical(MethodNode m, int skipReal) {
        Map<LabelNode, Integer> ids = new HashMap<LabelNode, Integer>();
        List<String> out = new ArrayList<String>();
        AbstractInsnNode insn = m.instructions.getFirst();
        for (int skipped = 0; skipped < skipReal; insn = insn.getNext()) {
            if (insn.getOpcode() >= 0) skipped++;
        }
        // Labels only matter where something refers to them (the skipped GOTO does not count).
        Set<LabelNode> used = new HashSet<LabelNode>();
        for (AbstractInsnNode n = insn; n != null; n = n.getNext()) {
            if (n instanceof JumpInsnNode) used.add(((JumpInsnNode) n).label);
            if (n instanceof LineNumberNode) used.add(((LineNumberNode) n).start);
        }
        for (Object o : m.tryCatchBlocks) {
            TryCatchBlockNode t = (TryCatchBlockNode) o;
            used.add(t.start); used.add(t.end); used.add(t.handler);
        }
        if (m.localVariables != null) for (Object o : m.localVariables) {
            LocalVariableNode v = (LocalVariableNode) o;
            used.add(v.start); used.add(v.end);
        }
        for (; insn != null; insn = insn.getNext()) {
            if (insn instanceof FrameNode) continue; // frames are re-encoded by ASM; the verifier checks them
            if (insn instanceof LabelNode && !used.contains(insn)) continue;
            out.add(describe(insn, ids));
        }
        for (Object o : m.tryCatchBlocks) {
            TryCatchBlockNode t = (TryCatchBlockNode) o;
            out.add("TRY " + id(t.start, ids) + " " + id(t.end, ids) + " " + id(t.handler, ids) + " " + t.type);
        }
        if (m.localVariables != null) for (Object o : m.localVariables) {
            LocalVariableNode v = (LocalVariableNode) o;
            out.add("LOCAL " + v.name + " " + v.desc + " " + v.index + " " + id(v.start, ids) + " " + id(v.end, ids));
        }
        out.add("ACCESS " + m.access + " EXCEPTIONS " + m.exceptions);
        return out;
    }

    static String id(LabelNode l, Map<LabelNode, Integer> ids) {
        Integer i = ids.get(l);
        if (i == null) ids.put(l, i = ids.size());
        return "L" + i;
    }

    static String describe(AbstractInsnNode n, Map<LabelNode, Integer> ids) {
        String op = n.getOpcode() >= 0 ? String.valueOf(n.getOpcode()) : n.getClass().getSimpleName();
        if (n instanceof LabelNode) return id((LabelNode) n, ids) + ":";
        if (n instanceof LineNumberNode) return "LINE " + ((LineNumberNode) n).line + " " + id(((LineNumberNode) n).start, ids);
        if (n instanceof VarInsnNode) return op + " " + ((VarInsnNode) n).var;
        if (n instanceof IntInsnNode) return op + " " + ((IntInsnNode) n).operand;
        if (n instanceof LdcInsnNode) return op + " " + ((LdcInsnNode) n).cst.getClass().getSimpleName() + ":" + ((LdcInsnNode) n).cst;
        if (n instanceof TypeInsnNode) return op + " " + ((TypeInsnNode) n).desc;
        if (n instanceof FieldInsnNode) { FieldInsnNode f = (FieldInsnNode) n; return op + " " + f.owner + "." + f.name + f.desc; }
        if (n instanceof MethodInsnNode) { MethodInsnNode f = (MethodInsnNode) n; return op + " " + f.owner + "." + f.name + f.desc + " " + f.itf; }
        if (n instanceof JumpInsnNode) return op + " " + id(((JumpInsnNode) n).label, ids);
        if (n instanceof IincInsnNode) return op + " " + ((IincInsnNode) n).var + " " + ((IincInsnNode) n).incr;
        if (n instanceof MultiANewArrayInsnNode) return op + " " + ((MultiANewArrayInsnNode) n).desc + " " + ((MultiANewArrayInsnNode) n).dims;
        if (n instanceof TableSwitchInsnNode || n instanceof LookupSwitchInsnNode || n instanceof InvokeDynamicInsnNode) {
            throw new AssertionError("unexpected instruction kind in target: " + n.getClass().getSimpleName());
        }
        if (n instanceof InsnNode) return op;
        throw new AssertionError("unhandled " + n.getClass());
    }

    static ClassNode read(String path) throws Exception {
        ClassNode node = new ClassNode();
        new ClassReader(Files.readAllBytes(Paths.get(path))).accept(node, 0);
        return node;
    }

    public static void main(String[] args) throws Exception {
        final String name = "getChromosomes";
        final String desc = "(Lnet/minecraft/nbt/NBTTagCompound;Lforestry/api/genetics/ISpeciesRoot;)[Lforestry/api/genetics/IChromosome;";
        final int pad = Integer.parseInt(args[2]);
        ClassNode original = read(args[0]);
        ClassNode patched = read(args[1]);
        MethodNode p = find(patched, name, desc);
        // head: GOTO, NOP x pad, ATHROW
        AbstractInsnNode insn = p.instructions.getFirst();
        List<Integer> head = new ArrayList<Integer>();
        for (; insn != null && head.size() < pad + 2; insn = insn.getNext()) if (insn.getOpcode() >= 0) head.add(insn.getOpcode());
        if (head.get(0) != Opcodes.GOTO || head.get(head.size() - 1) != Opcodes.ATHROW
            || new HashSet<Integer>(head.subList(1, head.size() - 1)).size() != 1 || head.get(1) != Opcodes.NOP) {
            throw new AssertionError("unexpected barrier head");
        }
        List<String> a = canonical(find(original, name, desc), 0);
        List<String> b = canonical(p, pad + 2);
        if (!a.equals(b)) throw new AssertionError("BODY DIFFERS\n" + a + "\n" + b);
        int others = 0;
        for (Object o : original.methods) {
            MethodNode m = (MethodNode) o;
            if (m.name.equals(name) && m.desc.equals(desc)) continue;
            if (!canonical(m, 0).equals(canonical(find(patched, m.name, m.desc), 0))) throw new AssertionError("changed " + m.name + m.desc);
            others++;
        }
        if (original.methods.size() != patched.methods.size() || original.fields.size() != patched.fields.size()
            || !original.name.equals(patched.name) || !original.superName.equals(patched.superName)
            || !original.interfaces.equals(patched.interfaces) || original.access != patched.access || original.version != patched.version) {
            throw new AssertionError("class shape changed");
        }
        System.out.println("BODY_COMPARE PASS target=" + a.size() + " canonical entries, other methods unchanged=" + others);
    }
}
