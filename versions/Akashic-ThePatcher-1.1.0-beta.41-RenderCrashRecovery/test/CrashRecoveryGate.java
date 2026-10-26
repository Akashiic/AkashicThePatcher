import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Paths;
import java.util.Collections;

/**
 * CoreTweaks crash recovery x Iris GbufferPrograms, on the real Angelica 2.2.37 GbufferPrograms class.
 *
 *   java CrashRecoveryGate <shapes dir> fixed|broken <expected RenderRecovery calls>
 *
 * Puts GbufferPrograms in the state a TESR exception leaves behind (blockEntities = true; 10:19 crash), checks
 * that the next phase begin throws the 10:21/10:22 crash message, runs CoreTweaks' CrashHandler.resetState()
 * (the shape, transformed like LaunchClassLoader would) and checks the phase begin again.
 * fixed: it no longer throws and every phase field is idle. broken: it still throws.
 * Class path: test classes, the jar under test, [test/recovery-stub,] angelica-2.2.37.jar, fastutil.
 */
public final class CrashRecoveryGate {
    static final String CRASH_HANDLER = "makamys.coretweaks.tweak.crashhandler.CrashHandler";
    static final String PROGRAMS = "net.coderbot.iris.layer.GbufferPrograms";
    static final String CRASH_MESSAGE = "GbufferPrograms in weird state, tried to call begin function when entities = false,"
        + " blockEntities = true, outline = false";

    public static void main(String[] args) throws Exception {
        final boolean fixed = args[1].equals("fixed");
        final int expectedCalls = Integer.parseInt(args[2]);
        final Class<?> programs = Class.forName(PROGRAMS);
        final Method checkReentrancy = programs.getDeclaredMethod("checkReentrancy");
        checkReentrancy.setAccessible(true);

        // What celeritas$renderTileEntities leaves when a TESR throws between begin and endBlockEntities.
        set(programs, "blockEntities", true);
        final String before = reentrancy(checkReentrancy);
        System.out.println("A before recovery: " + before);
        check(CRASH_MESSAGE.equals(before), "crash not reproduced: " + before);

        final TransformingLoader loader = new TransformingLoader(CrashRecoveryGate.class.getClassLoader(), Paths.get(args[0]),
            Collections.singletonList(CRASH_HANDLER), "com.akashic.thepatcher.module.crashrecovery.CoreTweaksCrashRecoveryTransformer");
        final Class<?> handler = Class.forName(CRASH_HANDLER, true, loader);
        handler.getMethod("resetState").invoke(null);
        check(handler.getField("steps").getInt(null) == (Boolean.getBoolean("stub.coretweaks.stepFails") ? 1 : 2),
            "CoreTweaks' own steps must still run");

        final String after = reentrancy(checkReentrancy);
        final int calls = recoveryCalls();
        System.out.println("A after recovery: " + after);
        System.out.println("A RenderRecovery.resetAfterCrash calls = " + calls + ", transformed = " + loader.transformed);
        check(calls == expectedCalls, "RenderRecovery calls " + calls + " != " + expectedCalls);
        if (fixed) {
            check(loader.transformed == 1, "CrashHandler must be transformed");
            check(after.equals("ok"), "still poisoned: " + after);
            for (String flag : new String[] { "entities", "blockEntities", "outline", "entityLoop", "particles" }) {
                check(!get(programs, flag).equals(Boolean.TRUE), flag + " still set");
            }
            check(get(programs, "particleDepth").equals(0), "particleDepth");
        } else {
            check(loader.transformed == 0, "nothing may be transformed");
            check(CRASH_MESSAGE.equals(after), "expected the crash to persist: " + after);
        }
        System.out.println("CRASH_RECOVERY_GATE PASS expect=" + args[1] + " calls=" + calls);
    }

    static String reentrancy(Method checkReentrancy) throws Exception {
        try {
            checkReentrancy.invoke(null);
            return "ok";
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof IllegalStateException) return e.getCause().getMessage();
            throw e;
        }
    }

    static int recoveryCalls() throws Exception {
        try {
            return Class.forName("com.gtnewhorizons.angelica.rendering.RenderRecovery").getField("calls").getInt(null);
        } catch (NoSuchFieldException realAngelicaClass) {
            return -1;
        }
    }

    static void set(Class<?> owner, String name, Object value) throws Exception {
        final Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(null, value);
    }

    static Object get(Class<?> owner, String name) throws Exception {
        final Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(null);
    }

    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
