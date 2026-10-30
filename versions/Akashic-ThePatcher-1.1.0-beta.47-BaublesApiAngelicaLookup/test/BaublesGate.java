import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A bauble slot change with Et Futurum's elytra when the Baubles API copy in use is older than 2.2.26.
 *
 *   java BaublesGate <dir: baubles/api/** of the API copy under test + compiled baubles shapes> guarded|crash
 *
 * SlotBaubleShape (compiled against 2.2.26, INVOKEINTERFACE onSlotContentsChanged) is called with ElytraShape (no
 * override) and TrackingBauble (override counting calls). guarded: no error, the override runs once, the elytra's
 * default does nothing. crash: java.lang.NoSuchMethodError on the elytra, as in the pack's crash report.
 * Run with -Xverify:all: the edited interface goes through the JVM verifier.
 */
public final class BaublesGate {
    public static void main(String[] args) throws Exception {
        final Path dir = Paths.get(args[0]);
        final boolean guarded = args[1].equals("guarded");
        final List<String> owned;
        try (Stream<Path> files = Files.walk(dir)) {
            owned = files.filter(p -> p.toString().endsWith(".class"))
                .map(p -> dir.relativize(p).toString().replace('\\', '/').replace('/', '.').replaceAll("\\.class$", ""))
                .sorted().collect(Collectors.toList());
        }
        final TransformingLoader loader = new TransformingLoader(BaublesGate.class.getClassLoader(), dir, owned,
            "com.akashic.thepatcher.module.baubles.BaublesExpandedApiTransformer");
        final Class<?> api = Class.forName("baubles.api.expanded.IBaubleExpanded", true, loader);
        boolean hasMethod = false;
        for (Method m : api.getDeclaredMethods()) hasMethod |= m.getName().equals("onSlotContentsChanged");
        System.out.println("A IBaubleExpanded declares onSlotContentsChanged=" + hasMethod);

        final Method changed = Class.forName("baubles.common.container.SlotBaubleShape", true, loader)
            .getMethod("onSlotChanged", Object.class, Class.forName("net.minecraft.item.ItemStack"),
                Class.forName("net.minecraft.entity.EntityLivingBase"));
        final Class<?> tracking = Class.forName("akashic.gate.TrackingBauble", true, loader);
        final Object stack = Class.forName("net.minecraft.item.ItemStack").newInstance();
        final Object player = Class.forName("net.minecraft.entity.EntityLivingBase").newInstance();

        String elytra;
        try {
            changed.invoke(null, Class.forName("akashic.gate.ElytraShape", true, loader).newInstance(), stack, player);
            elytra = "ok";
        } catch (InvocationTargetException thrown) {
            if (!(thrown.getCause() instanceof NoSuchMethodError)) throw thrown;
            elytra = thrown.getCause().toString();
        }
        System.out.println("A elytra in a bauble slot -> " + elytra);
        if (guarded) {
            check(elytra.equals("ok"), "elytra: " + elytra);
            changed.invoke(null, tracking.newInstance(), stack, player);
            final int changes = tracking.getField("changes").getInt(null);
            System.out.println("A overriding bauble in a bauble slot -> onSlotContentsChanged called " + changes + "x");
            check(changes == 1, "override called " + changes + " times");
        } else {
            check(elytra.contains("NoSuchMethodError") && elytra.contains("onSlotContentsChanged"), "expected the crash");
        }
        System.out.println("BAUBLES_GATE PASS expect=" + args[1] + " transformed=" + loader.transformed);
    }

    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
