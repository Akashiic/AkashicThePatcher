package com.akashic.thepatcher.module.crashrecovery;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import com.akashic.thepatcher.util.BundleLog;

/**
 * Runtime side of CoreTweaksCrashRecoveryTransformer (beta.41), called at the
 * end of CoreTweaks' CrashHandler.resetState():
 *
 *  1. Angelica's own recovery, RenderRecovery.resetAfterCrash() (what Angelica
 *     runs for BetterCrashes: GL state manager, display list compilation, Iris
 *     pipeline, TESR batches, font batchers, HUD cache);
 *     -Dakashic.crashRecoveryBridge.angelicaReset=false skips it;
 *  2. Iris' GbufferPrograms phase bookkeeping, which resetAfterCrash leaves
 *     alone: the entities / blockEntities / outline / entityLoop / particles
 *     flags, the particle save stack, the override phase stack, the declared
 *     translucency and the special-condition stack go back to their idle
 *     values (the values they have between two frames).
 *
 * Both are looked up by name and skipped when absent (no Angelica, an Angelica
 * without RenderRecovery, a renamed field). Every step catches its own failure:
 * the crash handler must never be the thing that crashes.
 */
public final class AngelicaCrashRecoveryHooks {

    public static final String ANGELICA_RESET_PROPERTY =
        "akashic.crashRecoveryBridge.angelicaReset";

    static final String RENDER_RECOVERY = "com.gtnewhorizons.angelica.rendering.RenderRecovery";
    static final String GBUFFER_PROGRAMS = "net.coderbot.iris.layer.GbufferPrograms";

    /** GbufferPrograms flags that are false between two frames. */
    static final String[] PHASE_FLAGS = { "entities", "blockEntities", "outline", "entityLoop", "particles" };
    static final String[] NULLED = { "overridePhase", "declaredTranslucent", "currentSpecial" };
    static final String[] CLEARED_ARRAYS = { "particlePhaseSaves", "particleTranslucencySaves" };
    static final String[] CLEARED_LISTS = { "phaseStack", "specialStack" };
    static final String DEPTH = "particleDepth";

    private AngelicaCrashRecoveryHooks() {}

    public static void afterCoreTweaksReset() {
        try {
            if (!"false".equalsIgnoreCase(System.getProperty(ANGELICA_RESET_PROPERTY))) {
                runAngelicaRecovery();
            }
            resetIrisPhases();
        } catch (ThreadDeath | VirtualMachineError fatal) {
            throw fatal;
        } catch (Throwable error) {
            BundleLog.error("CoreTweaks crash recovery bridge: render state reset failed.", error);
        }
    }

    static void runAngelicaRecovery() {
        final Class<?> recovery = find(RENDER_RECOVERY);
        if (recovery == null) {
            return;
        }
        try {
            final Method reset = recovery.getMethod("resetAfterCrash");
            if (!Modifier.isStatic(reset.getModifiers())) {
                BundleLog.warn("CoreTweaks crash recovery bridge: " + RENDER_RECOVERY + ".resetAfterCrash is not static; skipped.");
                return;
            }
            reset.invoke(null);
            BundleLog.info("CoreTweaks crash recovery bridge: Angelica RenderRecovery.resetAfterCrash() done.");
        } catch (InvocationTargetException failure) {
            final Throwable cause = failure.getCause() == null ? failure : failure.getCause();
            if (cause instanceof ThreadDeath || cause instanceof VirtualMachineError) {
                throw (Error) cause;
            }
            BundleLog.error("CoreTweaks crash recovery bridge: Angelica RenderRecovery.resetAfterCrash() failed.", cause);
        } catch (ThreadDeath | VirtualMachineError fatal) {
            throw fatal;
        } catch (Throwable error) {
            BundleLog.error("CoreTweaks crash recovery bridge: Angelica RenderRecovery.resetAfterCrash() failed.", error);
        }
    }

    /** Returns the names of the flags that were left set (stale), or null without GbufferPrograms. */
    static List<String> resetIrisPhases() {
        final Class<?> programs = find(GBUFFER_PROGRAMS);
        if (programs == null) {
            return null;
        }
        final List<String> stale = new ArrayList<>();
        final List<String> missing = new ArrayList<>();
        for (String name : PHASE_FLAGS) {
            final Field field = field(programs, name, boolean.class, true, missing);
            if (field != null) {
                reset(field, name, stale, missing, Boolean.FALSE);
            }
        }
        final Field depth = field(programs, DEPTH, int.class, true, missing);
        if (depth != null) {
            reset(depth, DEPTH, stale, missing, Integer.valueOf(0));
        }
        for (String name : NULLED) {
            final Field field = field(programs, name, Object.class, true, missing);
            if (field != null) {
                reset(field, name, stale, missing, null);
            }
        }
        for (String name : CLEARED_ARRAYS) {
            final Field field = field(programs, name, Object[].class, false, missing);
            if (field != null) {
                try {
                    final Object[] array = (Object[]) field.get(null);
                    if (array != null) {
                        Arrays.fill(array, null);
                    }
                } catch (ReflectiveOperationException | RuntimeException error) {
                    missing.add(name + " (" + error + ")");
                }
            }
        }
        for (String name : CLEARED_LISTS) {
            final Field field = field(programs, name, Collection.class, false, missing);
            if (field != null) {
                try {
                    final Collection<?> list = (Collection<?>) field.get(null);
                    if (list != null && !list.isEmpty()) {
                        stale.add(name + "=" + list.size());
                        list.clear();
                    }
                } catch (ReflectiveOperationException | RuntimeException error) {
                    missing.add(name + " (" + error + ")");
                }
            }
        }
        BundleLog.info("CoreTweaks crash recovery bridge: Iris GbufferPrograms reset"
            + (stale.isEmpty() ? " (nothing was left set)" : "; left set by the crash: " + stale)
            + (missing.isEmpty() ? "." : "; not found: " + missing + "."));
        return stale;
    }

    private static void reset(Field field, String name, List<String> stale, List<String> missing, Object idle) {
        try {
            final Object value = field.get(null);
            if (value != null && !value.equals(idle)) {
                stale.add(name + "=" + value);
            }
            field.set(null, idle);
        } catch (ReflectiveOperationException | RuntimeException error) {
            missing.add(name + " (" + error + ")");
        }
    }

    /** A static field of exactly {@code type} (a subtype for reference types), non-final when written; accessible. */
    private static Field field(Class<?> owner, String name, Class<?> type, boolean written, List<String> missing) {
        try {
            final Field field = owner.getDeclaredField(name);
            final int modifiers = field.getModifiers();
            final boolean typeOk = type.isPrimitive() ? type == field.getType() : type.isAssignableFrom(field.getType());
            if (!Modifier.isStatic(modifiers) || !typeOk || (written && Modifier.isFinal(modifiers))) {
                missing.add(name + " (unexpected " + field + ")");
                return null;
            }
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException error) {
            missing.add(name);
            return null;
        } catch (RuntimeException error) {
            missing.add(name + " (" + error + ")");
            return null;
        }
    }

    /** The class if it exists, without initialising it; null when it is not on the class path. */
    private static Class<?> find(String name) {
        try {
            return Class.forName(name, false, AngelicaCrashRecoveryHooks.class.getClassLoader());
        } catch (ClassNotFoundException | LinkageError absent) {
            return null;
        }
    }
}
