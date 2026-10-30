import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.jar.JarFile;

import net.minecraft.launchwrapper.IClassTransformer;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicVerifier;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceMethodVisitor;

/**
 * The transformer of the jar under test on the real ProjectorClient of the pack's Immersive Petroleum
 * (0.1.0-alpha.10 catalyst WIP, the build in use).
 *
 *   java RealProjectorGate <Immersive Petroleum jar> patched|untouched
 *
 * patched:   only selectedView changes: its one AALOAD becomes ProjectorSlotHooks.view + CHECKCAST ProjectorView,
 *            fed by GETFIELD InventoryPlayer.currentItem and followed by ARETURN; every method passes the ASM
 *            verifier; transforming the result again changes nothing.
 * untouched: the bytes come back unchanged (older jar, kill-switch, server side).
 */
public final class RealProjectorGate {
    static final String TARGET = "flaxbeard.immersivepetroleum.client.projector.ProjectorClient";
    static final String HOOKS = "com/akashic/thepatcher/module/immersivepetroleum/ProjectorSlotHooks";
    static final String VIEW = "flaxbeard/immersivepetroleum/client/projector/ProjectorView";

    public static void main(String[] args) throws Exception {
        final boolean patched = args[1].equals("patched");
        final byte[] original;
        try (JarFile jar = new JarFile(args[0]);
             InputStream in = jar.getInputStream(jar.getJarEntry(TARGET.replace('.', '/') + ".class"))) {
            original = TransformingLoader.read(in);
        }
        IClassTransformer transformer = null;
        try {
            transformer = (IClassTransformer) Class.forName(
                "com.akashic.thepatcher.module.immersivepetroleum.ProjectorSlotTransformer").newInstance();
        } catch (ClassNotFoundException absent) {
            System.out.println("A transformer not in the jar under test");
        }
        final byte[] result = transformer == null ? original : transformer.transform(TARGET, TARGET, original);
        final boolean changed = !Arrays.equals(original, result);
        System.out.println("A changed=" + changed);
        if (!patched) {
            check(!changed, "the class was edited");
            System.out.println("REAL_PROJECTOR PASS expect=untouched");
            return;
        }
        check(changed, "the class was not edited");

        final ClassNode before = node(original);
        final ClassNode after = node(result);
        check(before.methods.size() == after.methods.size(), "method count changed");
        final List<String> differing = new ArrayList<>();
        for (int i = 0; i < before.methods.size(); i++) {
            final MethodNode a = (MethodNode) before.methods.get(i);
            final MethodNode b = (MethodNode) after.methods.get(i);
            check(a.name.equals(b.name) && a.desc.equals(b.desc), "method order changed");
            new Analyzer(new BasicVerifier()).analyze(after.name, b);
            if (!text(a).equals(text(b))) differing.add(b.name + b.desc);
        }
        check(differing.equals(Arrays.asList("selectedView()L" + VIEW + ";")), "differing methods " + differing);

        final MethodNode selected = find(after, "selectedView");
        final MethodNode old = find(before, "selectedView");
        check(selected.instructions.size() == old.instructions.size() + 1,
            "expected one AALOAD replaced by two instructions");
        check(count(old, Opcodes.AALOAD) == 1 && count(selected, Opcodes.AALOAD) == 0, "AALOAD not replaced");
        int hooks = 0;
        for (AbstractInsnNode insn = selected.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (!(insn instanceof MethodInsnNode) || !HOOKS.equals(((MethodInsnNode) insn).owner)) continue;
            hooks++;
            final MethodInsnNode hook = (MethodInsnNode) insn;
            final AbstractInsnNode index = real(hook.getPrevious(), false);
            final AbstractInsnNode cast = real(hook.getNext(), true);
            final AbstractInsnNode ret = real(cast.getNext(), true);
            check(hook.getOpcode() == Opcodes.INVOKESTATIC && hook.name.equals("view")
                && hook.desc.equals("([Ljava/lang/Object;I)Ljava/lang/Object;"),
                "unexpected hook call " + hook.name + hook.desc);
            check(index instanceof FieldInsnNode && index.getOpcode() == Opcodes.GETFIELD
                && ((FieldInsnNode) index).owner.equals("net/minecraft/entity/player/InventoryPlayer")
                && ((FieldInsnNode) index).name.equals("field_70461_c"), "hook index is not InventoryPlayer.currentItem");
            check(cast.getOpcode() == Opcodes.CHECKCAST && ((TypeInsnNode) cast).desc.equals(VIEW),
                "hook result is not cast to ProjectorView");
            check(ret.getOpcode() == Opcodes.ARETURN, "the cast result is not returned");
            System.out.println("A hook: GETFIELD " + ((FieldInsnNode) index).name + " -> " + hook.name
                + " -> CHECKCAST ProjectorView -> ARETURN");
        }
        check(hooks == 1, hooks + " hook calls");
        check(Arrays.equals(result, transformer.transform(TARGET, TARGET, result)), "second pass changed the class");
        System.out.println("A second pass: unchanged");
        System.out.println("REAL_PROJECTOR PASS expect=patched");
    }

    /** The nearest real instruction (labels, line numbers and frames skipped) in the given direction. */
    static AbstractInsnNode real(AbstractInsnNode insn, boolean forward) {
        while (insn != null && insn.getOpcode() < 0) insn = forward ? insn.getNext() : insn.getPrevious();
        return insn;
    }

    static int count(MethodNode method, int opcode) {
        int n = 0;
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn.getOpcode() == opcode) n++;
        }
        return n;
    }

    static ClassNode node(byte[] bytes) {
        final ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        return node;
    }

    static MethodNode find(ClassNode node, String name) {
        for (Object m : node.methods) if (((MethodNode) m).name.equals(name)) return (MethodNode) m;
        throw new AssertionError("missing " + name);
    }

    static String text(MethodNode method) {
        final Textifier textifier = new Textifier();
        method.accept(new TraceMethodVisitor(textifier));
        final StringWriter out = new StringWriter();
        textifier.print(new PrintWriter(out));
        return out.toString();
    }

    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
