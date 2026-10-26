import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;

import net.minecraft.launchwrapper.IClassTransformer;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicVerifier;

/**
 * Runs the bundled CoreTweaksCrashRecoveryTransformer on the real CrashHandler of the CoreTweaks jar and checks
 * the result instruction by instruction (compiled against asm-all-5.0.3, the ASM Forge ships): resetState() gets exactly one hook call before each RETURN and is
 * otherwise unchanged, every other method is unchanged, and the result passes the ASM verifier.
 * (The class itself needs FML to load; its behaviour is covered by CrashRecoveryGate.)
 *
 *   java RealCoreTweaksGate <coretweaks.jar>
 */
public final class RealCoreTweaksGate {
    static final String ENTRY = "makamys/coretweaks/tweak/crashhandler/CrashHandler.class";
    static final String HOOKS = "com/akashic/thepatcher/module/crashrecovery/AngelicaCrashRecoveryHooks";

    public static void main(String[] args) throws Exception {
        final byte[] original;
        try (JarFile jar = new JarFile(args[0]); InputStream in = jar.getInputStream(jar.getJarEntry(ENTRY))) {
            original = TransformingLoader.read(in);
        }
        final IClassTransformer transformer = (IClassTransformer) Class.forName(
            "com.akashic.thepatcher.module.crashrecovery.CoreTweaksCrashRecoveryTransformer").newInstance();
        final String name = "makamys.coretweaks.tweak.crashhandler.CrashHandler";
        final byte[] patched = transformer.transform(name, name, original);
        check(patched != original, "real CrashHandler not transformed");
        final ClassNode a = node(original), b = node(patched);
        check(a.methods.size() == b.methods.size(), "method count changed");
        int hooks = 0, returns = 0;
        for (int i = 0; i < a.methods.size(); i++) {
            final MethodNode ma = (MethodNode) a.methods.get(i), mb = (MethodNode) b.methods.get(i);
            check(ma.name.equals(mb.name) && ma.desc.equals(mb.desc), "method order changed");
            final List<String> sa = ops(ma, false), sb = ops(mb, true);
            check(sa.equals(sb), ma.name + " changed beyond the hook calls");
            for (AbstractInsnNode insn = mb.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (insn instanceof MethodInsnNode && HOOKS.equals(((MethodInsnNode) insn).owner)) {
                    check(ma.name.equals("resetState") && insn.getNext().getOpcode() == Opcodes.RETURN, "hook misplaced");
                    hooks++;
                }
                if (ma.name.equals("resetState") && insn.getOpcode() == Opcodes.RETURN) returns++;
            }
            new Analyzer(new BasicVerifier()).analyze(b.name, mb);
        }
        check(hooks > 0 && hooks == returns, "hooks=" + hooks + " returns=" + returns);
        System.out.println("REAL_CORETWEAKS PASS hooks=" + hooks + " bytes=" + original.length + "->" + patched.length);
    }

    /** Opcodes plus owner.name for calls; with skipHooks the inserted hook calls are left out. */
    static List<String> ops(MethodNode method, boolean skipHooks) {
        final List<String> out = new ArrayList<>();
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn.getOpcode() < 0) continue;
            if (insn instanceof MethodInsnNode) {
                final MethodInsnNode call = (MethodInsnNode) insn;
                if (skipHooks && HOOKS.equals(call.owner)) continue;
                out.add(insn.getOpcode() + " " + call.owner + "." + call.name + call.desc);
            } else {
                out.add(String.valueOf(insn.getOpcode()));
            }
        }
        return out;
    }

    static ClassNode node(byte[] bytes) {
        final ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        return node;
    }

    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
