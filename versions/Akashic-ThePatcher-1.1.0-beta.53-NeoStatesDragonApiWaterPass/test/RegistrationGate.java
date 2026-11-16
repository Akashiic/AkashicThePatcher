import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

/**
 * Calls the bundled AkashicThePatcherLoadingPlugin.getASMTransformerClass()
 * (constructor skipped: it bootstraps configs and guards that need a game
 * directory) and prints the list. Run with -Xverify:all; -Dstub.side=SERVER
 * selects the dedicated server.
 */
public final class RegistrationGate {
    public static void main(String[] args) throws Exception {
        final Class<?> plugin = Class.forName("com.akashic.thepatcher.core.AkashicThePatcherLoadingPlugin");
        final Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        final Field field = unsafeClass.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        final Object instance = unsafeClass.getMethod("allocateInstance", Class.class).invoke(field.get(null), plugin);
        final List<String> list = Arrays.asList((String[]) plugin.getMethod("getASMTransformerClass").invoke(instance));
        System.out.println("REGISTRATION side=" + System.getProperty("stub.side", "CLIENT") + " count=" + list.size() + " list=" + list);
    }
}
