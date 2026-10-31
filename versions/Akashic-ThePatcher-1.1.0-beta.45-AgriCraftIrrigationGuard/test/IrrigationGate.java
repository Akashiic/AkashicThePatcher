import java.nio.file.Paths;
import java.util.Collections;

/**
 * AgriCraft "Disable Irrigation" on the client (shape of ConfigurationHandler, run through the jar under test).
 *
 *   java [-Dshape.disableIrrigation=true] IrrigationGate <shapes dir> <expected disableIrrigation>
 *
 * Loads the config twice (init, then a config-changed reload) and checks the stored value both times, that
 * the other fields are untouched, and how many times the class was edited. Run with -Xverify:all: the edited
 * bytecode goes through the JVM verifier.
 */
public final class IrrigationGate {
    static final String TARGET = "com.InfinityRaider.AgriCraft.handler.ConfigurationHandler";

    public static void main(String[] args) throws Exception {
        final boolean expected = Boolean.parseBoolean(args[1]);
        final TransformingLoader loader = new TransformingLoader(IrrigationGate.class.getClassLoader(),
            Paths.get(args[0]), Collections.singletonList(TARGET),
            "com.akashic.thepatcher.module.agricraft.AgriCraftIrrigationTransformer");
        final Class<?> handler = Class.forName(TARGET, true, loader);
        for (String step : new String[] {"init", "reload"}) {
            handler.getMethod(step).invoke(null);
            final boolean stored = handler.getField("disableIrrigation").getBoolean(null);
            final boolean warehouse = handler.getField("disableSeedWarehouse").getBoolean(null);
            final int capacity = handler.getField("channelCapacity").getInt(null);
            System.out.println("A " + step + " config=" + Boolean.getBoolean("shape.disableIrrigation")
                + " disableIrrigation=" + stored + " disableSeedWarehouse=" + warehouse + " channelCapacity=" + capacity);
            if (stored != expected) throw new AssertionError(step + ": disableIrrigation=" + stored + ", expected " + expected);
            if (warehouse != Boolean.getBoolean("shape.disableSeedWarehouse") || capacity != 500) {
                throw new AssertionError(step + ": another field changed");
            }
        }
        System.out.println("IRRIGATION_GATE PASS expect=" + expected + " transformed=" + loader.transformed);
    }
}
