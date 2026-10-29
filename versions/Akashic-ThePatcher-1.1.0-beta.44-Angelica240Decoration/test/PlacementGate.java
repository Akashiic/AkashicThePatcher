import java.nio.file.Files;
import java.nio.file.Paths;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Checks where the endpoint fix landed in a post-processed BatchingFontRenderer:
 * exactly one "fstore curX; fload curX; fstore underlineEndX; fload curX;
 * fstore strikethroughEndX; goto", i.e. the whitespace branch now moves both
 * decoration endpoints before its continue.
 *
 *   java PlacementGate <BatchingFontRenderer.class> <curX> <underlineEndX> <strikethroughEndX>
 */
public final class PlacementGate {
    public static void main(String[] args) throws Exception {
        final ClassNode node = new ClassNode();
        new ClassReader(Files.readAllBytes(Paths.get(args[0]))).accept(node, 0);
        final int curX = Integer.parseInt(args[1]);
        final int[][] expected = { { Opcodes.FSTORE, curX }, { Opcodes.FLOAD, curX }, { Opcodes.FSTORE, Integer.parseInt(args[2]) },
            { Opcodes.FLOAD, curX }, { Opcodes.FSTORE, Integer.parseInt(args[3]) } };
        int hits = 0;
        for (Object o : node.methods) {
            final MethodNode m = (MethodNode) o;
            if (!m.name.equals("drawString") || !m.desc.equals("(FFIZZLjava/lang/CharSequence;II)F")) {
                continue;
            }
            for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                AbstractInsnNode cur = real(insn);
                if (cur != insn) {
                    continue;
                }
                boolean match = true;
                for (int[] step : expected) {
                    if (cur == null || cur.getOpcode() != step[0] || ((VarInsnNode) cur).var != step[1]) {
                        match = false;
                        break;
                    }
                    cur = real(cur.getNext());
                }
                if (match && cur != null && cur.getOpcode() == Opcodes.GOTO) {
                    hits++;
                }
            }
        }
        System.out.println((hits == 1 ? "PLACEMENT PASS" : "PLACEMENT FAIL") + " sites=" + hits);
    }

    static AbstractInsnNode real(AbstractInsnNode insn) {
        while (insn != null && insn.getOpcode() < 0) {
            insn = insn.getNext();
        }
        return insn;
    }
}
