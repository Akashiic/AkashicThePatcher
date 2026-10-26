import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.launchwrapper.IClassTransformer;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Runs the jar's MiraiShaderBridgeTransformer (when it has one) on Angelica's Iris and DHCompat and on Mirai's
 * MixinRenderGlobal, all read from the class path as LaunchClassLoader would, and checks the result
 * instruction by instruction against the original.
 *
 *   java MiraiBridgeGate patched|untouched
 *
 * patched:   Iris and DHCompat differ from the original in exactly one instruction, the probe's LDC, now
 *            Mirai's coremod name; MixinRenderGlobal keeps its methods unchanged and gains one handler
 *            (@Inject sortAndRender, INVOKE EntityRenderer.func_78463_b(D)V, shift AFTER, require 0) whose body
 *            is ILOAD 2, INVOKESTATIC MiraiShaderBridgeHooks.afterTranslucentLightmap(I)V, RETURN.
 * untouched: the three classes come back unchanged (byte-identical); absent ones are skipped.
 */
public final class MiraiBridgeGate {
    static final String TRANSFORMER = "com.akashic.thepatcher.module.mirai.MiraiShaderBridgeTransformer";
    static final String PROBE_OLD = "com.seibel.distanthorizons.DistantHorizonsTweaker";
    static final String PROBE_NEW = "com.seibel.distanthorizons.forge17.DistantHorizonsLoadingPlugin";

    public static void main(String[] args) throws Exception {
        final String expect = args[0];
        IClassTransformer transformer = null;
        try {
            transformer = (IClassTransformer) Class.forName(TRANSFORMER).newInstance();
        } catch (ClassNotFoundException absent) {
            System.out.println("A transformer not in the jar under test");
        }
        final String[] targets = {"net.coderbot.iris.Iris", "net.coderbot.iris.compat.dh.DHCompat",
            "com.seibel.distanthorizons.forge17.mixin.MixinRenderGlobal"};
        for (String target : targets) {
            final byte[] original = read(target);
            if (original == null && "untouched".equals(expect)) {
                System.out.println("A " + target + " absent");
                continue;
            }
            check(original != null, target + " not on the class path");
            final byte[] result = transformer == null ? original : transformer.transform(target, target, original.clone());
            final boolean same = java.util.Arrays.equals(original, result);
            System.out.println("A " + target + " changed=" + !same);
            if ("untouched".equals(expect)) {
                check(same, target + " was changed");
                continue;
            }
            check(!same, target + " was not changed");
            final ClassNode before = node(original), after = node(result);
            if (target.endsWith("MixinRenderGlobal")) {
                checkMixin(before, after);
            } else {
                checkProbe(before, after);
            }
        }
        System.out.println("MIRAI_BRIDGE_GATE PASS expect=" + expect);
    }

    static void checkProbe(ClassNode before, ClassNode after) {
        check(before.methods.size() == after.methods.size(), "method count changed");
        int differences = 0;
        for (int i = 0; i < before.methods.size(); i++) {
            final MethodNode a = (MethodNode) before.methods.get(i), b = (MethodNode) after.methods.get(i);
            check(a.name.equals(b.name) && a.desc.equals(b.desc), "method order changed");
            final List<AbstractInsnNode> x = real(a), y = real(b);
            check(x.size() == y.size(), a.name + " instruction count changed");
            for (int k = 0; k < x.size(); k++) {
                if (!same(x.get(k), y.get(k))) {
                    differences++;
                    check(x.get(k) instanceof LdcInsnNode && PROBE_OLD.equals(((LdcInsnNode) x.get(k)).cst)
                        && PROBE_NEW.equals(((LdcInsnNode) y.get(k)).cst), "unexpected change in " + a.name);
                    final AbstractInsnNode next = y.get(k + 1);
                    check(next instanceof MethodInsnNode && "forName".equals(((MethodInsnNode) next).name), "probe not followed by forName");
                    System.out.println("A   " + before.name + "." + a.name + a.desc + ": LDC " + PROBE_OLD + " -> " + PROBE_NEW);
                }
            }
        }
        check(differences == 1, "expected one changed instruction, got " + differences);
    }

    static void checkMixin(ClassNode before, ClassNode after) {
        check(after.methods.size() == before.methods.size() + 1, "expected one added method");
        MethodNode added = null;
        for (Object o : after.methods) {
            final MethodNode b = (MethodNode) o;
            MethodNode a = null;
            for (Object p : before.methods) {
                final MethodNode m = (MethodNode) p;
                if (m.name.equals(b.name) && m.desc.equals(b.desc)) a = m;
            }
            if (a == null) {
                check(added == null, "two added methods");
                added = b;
                continue;
            }
            final List<AbstractInsnNode> x = real(a), y = real(b);
            check(x.size() == y.size(), a.name + " changed");
            for (int k = 0; k < x.size(); k++) check(same(x.get(k), y.get(k)), a.name + " changed");
        }
        check(added != null && added.name.equals("akashic$renderDeferredLodsDuringTranslucentSetup"), "handler missing");
        check(added.desc.equals("(Lnet/minecraft/entity/EntityLivingBase;IDLorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;)V"),
            "handler descriptor " + added.desc);
        final List<AbstractInsnNode> body = real(added);
        check(body.size() == 3 && body.get(0).getOpcode() == Opcodes.ILOAD && ((VarInsnNode) body.get(0)).var == 2
            && body.get(1) instanceof MethodInsnNode
            && ((MethodInsnNode) body.get(1)).owner.equals("com/akashic/thepatcher/module/mirai/MiraiShaderBridgeHooks")
            && ((MethodInsnNode) body.get(1)).name.equals("afterTranslucentLightmap")
            && ((MethodInsnNode) body.get(1)).desc.equals("(I)V")
            && body.get(2).getOpcode() == Opcodes.RETURN, "unexpected handler body");
        check(added.visibleAnnotations != null && added.visibleAnnotations.size() == 1, "handler annotations");
        final AnnotationNode inject = (AnnotationNode) added.visibleAnnotations.get(0);
        check(inject.desc.equals("Lorg/spongepowered/asm/mixin/injection/Inject;"), "not an @Inject");
        final String text = describe(inject);
        System.out.println("A   handler " + added.name + " " + text);
        check(text.equals("@Inject(method=[sortAndRender], at=[@At(value=INVOKE, target=Lnet/minecraft/client/renderer/EntityRenderer;"
            + "func_78463_b(D)V, shift=AFTER)], require=0)"), "unexpected annotation " + text);
    }

    static String describe(AnnotationNode a) {
        final StringBuilder sb = new StringBuilder("@" + a.desc.substring(a.desc.lastIndexOf('/') + 1, a.desc.length() - 1) + "(");
        for (int i = 0; i < a.values.size(); i += 2) {
            if (i > 0) sb.append(", ");
            sb.append(a.values.get(i)).append('=').append(value(a.values.get(i + 1)));
        }
        return sb.append(')').toString();
    }

    static String value(Object v) {
        if (v instanceof AnnotationNode) return describe((AnnotationNode) v);
        if (v instanceof String[]) return ((String[]) v)[1];
        if (v instanceof List) {
            final StringBuilder sb = new StringBuilder("[");
            for (Object o : (List<?>) v) sb.append(sb.length() > 1 ? ", " : "").append(value(o));
            return sb.append(']').toString();
        }
        return String.valueOf(v);
    }

    static List<AbstractInsnNode> real(MethodNode m) {
        final List<AbstractInsnNode> out = new ArrayList<AbstractInsnNode>();
        for (AbstractInsnNode i = m.instructions.getFirst(); i != null; i = i.getNext()) if (i.getOpcode() >= 0) out.add(i);
        return out;
    }

    static boolean same(AbstractInsnNode a, AbstractInsnNode b) {
        if (a.getOpcode() != b.getOpcode() || a.getClass() != b.getClass()) return false;
        if (a instanceof LdcInsnNode) return ((LdcInsnNode) a).cst.equals(((LdcInsnNode) b).cst);
        if (a instanceof MethodInsnNode) {
            final MethodInsnNode x = (MethodInsnNode) a, y = (MethodInsnNode) b;
            return x.owner.equals(y.owner) && x.name.equals(y.name) && x.desc.equals(y.desc);
        }
        if (a instanceof FieldInsnNode) {
            final FieldInsnNode x = (FieldInsnNode) a, y = (FieldInsnNode) b;
            return x.owner.equals(y.owner) && x.name.equals(y.name) && x.desc.equals(y.desc);
        }
        if (a instanceof VarInsnNode) return ((VarInsnNode) a).var == ((VarInsnNode) b).var;
        if (a instanceof IntInsnNode) return ((IntInsnNode) a).operand == ((IntInsnNode) b).operand;
        if (a instanceof TypeInsnNode) return ((TypeInsnNode) a).desc.equals(((TypeInsnNode) b).desc);
        if (a instanceof IincInsnNode) return ((IincInsnNode) a).var == ((IincInsnNode) b).var && ((IincInsnNode) a).incr == ((IincInsnNode) b).incr;
        return true;
    }

    static ClassNode node(byte[] bytes) {
        final ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        return node;
    }

    static byte[] read(String className) throws Exception {
        try (InputStream in = MiraiBridgeGate.class.getClassLoader().getResourceAsStream(className.replace('.', '/') + ".class")) {
            if (in == null) return null;
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            final byte[] buffer = new byte[8192];
            for (int n; (n = in.read(buffer)) != -1;) out.write(buffer, 0, n);
            return out.toByteArray();
        }
    }

    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
