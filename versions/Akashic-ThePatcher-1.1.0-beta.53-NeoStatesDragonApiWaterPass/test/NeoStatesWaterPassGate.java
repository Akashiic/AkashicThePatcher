import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.launchwrapper.IClassTransformer;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * beta.53 gate (run with -Xverify:all). The classpath decides what is "installed": Neo States' API (test/neo), its
 * mixin (the real alpha.10 source or the upstream-fixed one), DragonAPI (the RenderBlockAtPosEvent shape) and
 * optionally Angelica's jar.
 *
 *   java NeoStatesWaterPassGate mixin  <mixin shape dir>  <patched|unpatched>
 *   java NeoStatesWaterPassGate dragon <dragon shape dir> <patched|unpatched>
 *
 * mixin:  runs the bundled transformer over Neo States' mixin class as Mixin would read it; patched = the
 *         @WrapOperation of neostates$holdersNeedTheWaterPass gains require=0 and nothing else of the class changes.
 * dragon: loads DragonAPI's RenderBlockAtPosEvent through the transformer and calls getMaxRenderPass: patched = a
 *         block that may hold water in the opaque pass asks for pass 1 (Neo States' rule), every other answer is the
 *         original one (opaque block 0, translucent block 1, a listener's higher pass wins).
 */
public final class NeoStatesWaterPassGate {

    static final String TRANSFORMER = "com.akashic.thepatcher.module.neostates.NeoStatesWaterPassTransformer";
    static final String MIXIN = "com.akashic.neostates.mixin.early.client.MixinWorldRenderer_WaterPass";
    static final String DRAGON = "Reika.DragonAPI.Instantiable.Event.Client.RenderBlockAtPosEvent";

    public static void main(String[] args) throws Exception {
        final String mode = args[0];
        final Path shapes = Paths.get(args[1]);
        final boolean patched = "patched".equals(args[2]);
        if ("mixin".equals(mode)) {
            mixin(shapes, patched);
        } else if ("dragon".equals(mode)) {
            dragon(shapes, patched);
        } else {
            throw new IllegalArgumentException(mode);
        }
        System.out.println("NEOSTATES_WATER_PASS_GATE PASS mode=" + mode + " expect=" + args[2]);
    }

    static IClassTransformer transformer() throws Exception {
        try {
            return (IClassTransformer) Class.forName(TRANSFORMER).newInstance();
        } catch (ClassNotFoundException absent) {
            System.out.println("GATE transformer not in the jar under test");
            return null;
        }
    }

    static void mixin(Path shapes, boolean patched) throws Exception {
        final byte[] original = Files.readAllBytes(shapes.resolve(MIXIN.replace('.', '/') + ".class"));
        final IClassTransformer t = transformer();
        final byte[] out = t == null ? original : t.transform(MIXIN, MIXIN, original);
        final ClassNode before = node(original), after = node(out);
        check(before.methods.size() == after.methods.size(), "method count changed");
        for (int i = 0; i < before.methods.size(); i++) {
            final MethodNode a = (MethodNode) before.methods.get(i), b = (MethodNode) after.methods.get(i);
            check(a.name.equals(b.name) && a.desc.equals(b.desc), "method changed: " + a.name);
            check(a.instructions.size() == b.instructions.size(), "code changed: " + a.name);
        }
        final Object require = require(after);
        System.out.println("GATE mixin require=" + require + " same-bytes=" + Arrays.equals(original, out));
        if (patched) {
            check(Integer.valueOf(0).equals(require), "require=0 expected, got " + require);
            check(require(before) == null, "the alpha.10 shape has no require");
            // a second pass changes nothing (Mixin may read the class twice)
            check(Arrays.equals(out, t.transform(MIXIN, MIXIN, out)), "second pass changed the class");
        } else {
            check(Arrays.equals(original, out), "class must be returned unchanged");
        }
    }

    static Object require(ClassNode node) {
        for (Object o : node.methods) {
            final MethodNode m = (MethodNode) o;
            if (!"neostates$holdersNeedTheWaterPass".equals(m.name) || m.visibleAnnotations == null) {
                continue;
            }
            for (Object a : m.visibleAnnotations) {
                final AnnotationNode an = (AnnotationNode) a;
                if (an.desc.endsWith("/WrapOperation;") && an.values != null) {
                    for (int i = 0; i + 1 < an.values.size(); i += 2) {
                        if ("require".equals(an.values.get(i))) {
                            return an.values.get(i + 1);
                        }
                    }
                }
            }
        }
        return null;
    }

    static void dragon(Path shapes, boolean patched) throws Exception {
        final List<String> owned = Arrays.asList(DRAGON, DRAGON + "$AdvancedBlockRenderWatcher");
        final TransformingLoader loader = new TransformingLoader(NeoStatesWaterPassGate.class.getClassLoader(), shapes,
            owned, TRANSFORMER);
        final Class<?> event = Class.forName(DRAGON, true, loader);
        final Method max = event.getMethod("getMaxRenderPass", Block.class, int.class, int.class, int.class);
        System.out.println("GATE dragon transformed=" + loader.transformed);
        check(loader.transformed == (patched ? 1 : 0), "transformed=" + loader.transformed);

        final Block holder = new Block(0, true), opaque = new Block(0, false), glass = new Block(1, false);
        final Block holderGlass = new Block(1, true);
        check(pass(max, opaque) == 0, "opaque block must stay in pass 0");
        check(pass(max, glass) == 1, "translucent block must stay in pass 1");
        check(pass(max, holderGlass) == 1, "translucent holder stays 1");
        check(pass(max, holder) == (patched ? 1 : 0), "holder: " + pass(max, holder));

        // a listener asking for a higher pass still wins
        final Class<?> watcher = Class.forName(DRAGON + "$AdvancedBlockRenderWatcher", true, loader);
        final Object two = java.lang.reflect.Proxy.newProxyInstance(loader, new Class<?>[] { watcher },
            (proxy, method, a) -> "getMaxRenderPass".equals(method.getName()) ? Integer.valueOf(2) : Boolean.FALSE);
        final Object array = java.lang.reflect.Array.newInstance(watcher, 1);
        java.lang.reflect.Array.set(array, 0, two);
        event.getField("advancedListeners").set(null, array);
        check(pass(max, holder) == 2 && pass(max, opaque) == 2, "listener pass must win");
        event.getField("advancedListeners").set(null, null);
        System.out.println("GATE dragon holder=" + pass(max, holder) + " opaque=" + pass(max, opaque) + " glass="
            + pass(max, glass) + " neostatesCalls=" + com.akashic.neostates.api.NeoStates.calls);
        if (!patched) {
            check(com.akashic.neostates.api.NeoStates.calls == 0, "Neo States must not be asked when unpatched");
        }
    }

    static int pass(Method max, Block block) throws Exception {
        return ((Integer) max.invoke(null, block, 0, 64, 0)).intValue();
    }

    static ClassNode node(byte[] bytes) {
        final ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        return node;
    }

    static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
