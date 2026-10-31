import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Paths;
import java.util.Collections;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;

/**
 * Immersive Petroleum's projector lookup with Backhand's offhand slot selected (shape of ProjectorClient, run
 * through the jar under test).
 *
 *   java ProjectorGate <shapes dir> guarded|crash
 *
 * Hotbar slots 0..8 return their own projection either way; with no player the answer is null. Slot 36
 * (Backhand's offhand) and -1: guarded -> null, crash -> ArrayIndexOutOfBoundsException (the original code).
 * Run with -Xverify:all: the edited bytecode goes through the JVM verifier.
 */
public final class ProjectorGate {
    static final String TARGET = "flaxbeard.immersivepetroleum.client.projector.ProjectorClient";

    public static void main(String[] args) throws Exception {
        final boolean guarded = args[1].equals("guarded");
        final TransformingLoader loader = new TransformingLoader(ProjectorGate.class.getClassLoader(),
            Paths.get(args[0]), Collections.singletonList(TARGET),
            "com.akashic.thepatcher.module.immersivepetroleum.ProjectorSlotTransformer");
        final Class<?> client = Class.forName(TARGET, true, loader);
        final Class<?> view = Class.forName("flaxbeard.immersivepetroleum.client.projector.ProjectorView");
        final Method put = client.getMethod("put", int.class, view);
        final Method selected = client.getMethod("selectedView");
        final Object[] views = new Object[9];
        for (int slot = 0; slot < 9; slot++) {
            views[slot] = view.getConstructor(String.class).newInstance("slot" + slot);
            put.invoke(null, slot, views[slot]);
        }

        final Minecraft mc = Minecraft.func_71410_x();
        check(selected.invoke(null) == null, "no player: expected null");
        System.out.println("A player=null -> null");
        mc.field_71439_g = new EntityClientPlayerMP();
        for (int slot = 0; slot < 9; slot++) {
            mc.field_71439_g.field_71071_by.field_70461_c = slot;
            check(selected.invoke(null) == views[slot], "slot " + slot + ": wrong projection");
        }
        System.out.println("A slots 0..8 -> their own projection");
        for (int slot : new int[] {36, -1, 9}) {
            mc.field_71439_g.field_71071_by.field_70461_c = slot;
            String outcome;
            try {
                final Object answer = selected.invoke(null);
                check(answer == null, "slot " + slot + ": expected null, got " + answer);
                outcome = "null";
            } catch (InvocationTargetException thrown) {
                if (!(thrown.getCause() instanceof ArrayIndexOutOfBoundsException)) throw thrown;
                outcome = thrown.getCause().toString();
            }
            System.out.println("A slot=" + slot + " -> " + outcome);
            check(guarded == outcome.equals("null"), "slot " + slot + ": " + outcome);
        }
        System.out.println("PROJECTOR_GATE PASS expect=" + args[1] + " transformed=" + loader.transformed);
    }

    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
