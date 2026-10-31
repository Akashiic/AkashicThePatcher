package com.akashic.thepatcher.module.angelica;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import com.akashic.thepatcher.util.BundleLog;

/**
 * Runtime side of AngelicaGlLookupTransformer (beta.47): stands in for MethodHandles.Lookup.findStatic in
 * AkashicBackpackCompat's GlApi. Only installed on the client.
 */
public final class AngelicaGlLookupHooks {

    static final String GLSM = "com.gtnewhorizons.angelica.glsm.GLStateManager";
    static final String OWNERS = "com.gtnewhorizons.angelica.glsm.redirect.GLSMOwners";

    /** Method name -> owner class (internal name), from Angelica's GLSMOwners.METHODS; empty when there is none. */
    private static volatile Map<String, String> moved;
    private static volatile boolean logged;

    private AngelicaGlLookupHooks() {}

    /**
     * lookup.findStatic(owner, name, type); when owner is Angelica's GLStateManager and it no longer has the method
     * (Angelica 2.2.40+), the method is looked up in the class Angelica's GLSMOwners table moved it to.
     */
    public static MethodHandle findStatic(MethodHandles.Lookup lookup, Class<?> owner, String name, MethodType type)
        throws NoSuchMethodException, IllegalAccessException {
        try {
            return lookup.findStatic(owner, name, type);
        } catch (NoSuchMethodException missing) {
            if (owner == null || !GLSM.equals(owner.getName())) {
                throw missing;
            }
            final String internal = moved(owner.getClassLoader()).get(name);
            if (internal == null) {
                throw missing;
            }
            final Class<?> target;
            try {
                target = Class.forName(internal.replace('/', '.'), false, owner.getClassLoader());
            } catch (ClassNotFoundException absent) {
                throw missing;
            }
            final MethodHandle handle = lookup.findStatic(target, name, type);
            if (!logged) {
                logged = true;
                BundleLog.info("Angelica GL lookup bridge: " + name + type + " is no longer in GLStateManager (Angelica"
                    + " 2.2.40+); resolved in " + target.getName() + " (further moved methods are resolved the same way,"
                    + " silently).");
            }
            return handle;
        }
    }

    private static Map<String, String> moved(ClassLoader loader) {
        Map<String, String> table = moved;
        if (table == null) {
            table = new HashMap<>();
            try {
                final Field field = Class.forName(OWNERS, true, loader).getDeclaredField("METHODS");
                field.setAccessible(true);
                for (String[] group : (String[][]) field.get(null)) {
                    for (int i = 1; i < group.length; i++) {
                        table.put(group[i], group[0]);
                    }
                }
            } catch (Throwable error) {
                BundleLog.warn("Angelica GL lookup bridge: no usable " + OWNERS + ".METHODS table (" + error
                    + "); moved GL methods cannot be resolved.");
            }
            moved = table;
        }
        return table;
    }
}
