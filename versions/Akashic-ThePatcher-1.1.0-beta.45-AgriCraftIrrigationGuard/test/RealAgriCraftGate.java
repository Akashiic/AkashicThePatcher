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
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicVerifier;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceMethodVisitor;

/**
 * The transformer of the jar under test on the real AgriCraft 1.7.10 ConfigurationHandler.
 *
 *   java RealAgriCraftGate <AgriCraft jar> patched|untouched
 *
 * patched:   only loadConfiguration changes, by exactly one instruction - the hook call between
 *            Configuration.getBoolean("Disable Irrigation", ...) and PUTSTATIC disableIrrigation; every
 *            method passes the ASM verifier; transforming the result again changes nothing.
 * untouched: the bytes come back unchanged (older jar, kill-switch, server side).
 */
public final class RealAgriCraftGate {
    static final String TARGET = "com.InfinityRaider.AgriCraft.handler.ConfigurationHandler";
    static final String HOOKS = "com/akashic/thepatcher/module/agricraft/AgriCraftIrrigationHooks";

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
                "com.akashic.thepatcher.module.agricraft.AgriCraftIrrigationTransformer").newInstance();
        } catch (ClassNotFoundException absent) {
            System.out.println("A transformer not in the jar under test");
        }
        final byte[] result = transformer == null ? original : transformer.transform(TARGET, TARGET, original);
        final boolean changed = !Arrays.equals(original, result);
        System.out.println("A changed=" + changed);
        if (!patched) {
            check(!changed, "the class was edited");
            System.out.println("REAL_AGRICRAFT PASS expect=untouched");
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
        check(differing.equals(Arrays.asList("loadConfiguration()V")), "differing methods " + differing);

        final MethodNode load = find(after, "loadConfiguration");
        check(load.instructions.size() == find(before, "loadConfiguration").instructions.size() + 1,
            "more than one instruction added");
        int hooks = 0;
        for (AbstractInsnNode insn = load.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (!(insn instanceof MethodInsnNode) || !HOOKS.equals(((MethodInsnNode) insn).owner)) continue;
            hooks++;
            final MethodInsnNode hook = (MethodInsnNode) insn;
            final AbstractInsnNode read = hook.getPrevious();
            final AbstractInsnNode write = hook.getNext();
            check(hook.getOpcode() == Opcodes.INVOKESTATIC && hook.name.equals("clientIrrigationDisabled")
                && hook.desc.equals("(Z)Z"), "unexpected hook call " + hook.name + hook.desc);
            check(read instanceof MethodInsnNode && ((MethodInsnNode) read).name.equals("getBoolean")
                && ((MethodInsnNode) read).owner.equals("net/minecraftforge/common/config/Configuration"),
                "hook is not fed by Configuration.getBoolean");
            check(write.getOpcode() == Opcodes.PUTSTATIC && ((FieldInsnNode) write).name.equals("disableIrrigation"),
                "hook result is not stored in disableIrrigation");
            System.out.println("A hook: " + ((MethodInsnNode) read).name + " -> " + hook.name + " -> PUTSTATIC "
                + ((FieldInsnNode) write).name);
        }
        check(hooks == 1, hooks + " hook calls");
        check(Arrays.equals(result, transformer.transform(TARGET, TARGET, result)), "second pass changed the class");
        System.out.println("A second pass: unchanged");
        System.out.println("REAL_AGRICRAFT PASS expect=patched");
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
