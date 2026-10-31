package br.com.akashic.backpackcompat.client;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Test shape of AkashicBackpackCompat 1.0.0-rc1's GlApi as its crash shows it: the static initializer (GlApi.java:12)
 * calls find (GlApi.java:30-31), which resolves each GL entry point with MethodHandles.Lookup.findStatic on Angelica's
 * GLStateManager and throws IllegalStateException("Missing GL API <name> in class <class>"). The real class is not
 * available; the bridge only depends on the findStatic call. Never shipped.
 */
public final class GlApi {
    static final String GLSM = "com.gtnewhorizons.angelica.glsm.GLStateManager";
    static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();
    static final Map<String, MethodHandle> HANDLES = new LinkedHashMap<>();

    static {
        HANDLES.put("glPushMatrix", find("glPushMatrix", MethodType.methodType(void.class)));
        HANDLES.put("glPopMatrix", find("glPopMatrix", MethodType.methodType(void.class)));
        HANDLES.put("glTranslatef", find("glTranslatef", MethodType.methodType(void.class, float.class, float.class, float.class)));
        HANDLES.put("glRotatef", find("glRotatef", MethodType.methodType(void.class, float.class, float.class, float.class, float.class)));
        HANDLES.put("glScalef", find("glScalef", MethodType.methodType(void.class, float.class, float.class, float.class)));
        HANDLES.put("glEnable", find("glEnable", MethodType.methodType(void.class, int.class)));
        HANDLES.put("glDisable", find("glDisable", MethodType.methodType(void.class, int.class)));
        HANDLES.put("glColor4f", find("glColor4f", MethodType.methodType(void.class, float.class, float.class, float.class, float.class)));
        HANDLES.put("glBindTexture", find("glBindTexture", MethodType.methodType(void.class, int.class, int.class)));
    }

    private GlApi() {}

    private static MethodHandle find(String name, MethodType type) {
        try {
            // Load without initialising: GLStateManager.<clinit> talks to OpenGL (in the game it is long initialised).
            return LOOKUP.findStatic(Class.forName(GLSM, false, GlApi.class.getClassLoader()), name, type);
        } catch (ReflectiveOperationException missing) {
            throw new IllegalStateException("Missing GL API " + name + " in class " + GLSM, missing);
        }
    }

    /** name -> declaring class of the resolved method (nothing is invoked: no GL context in the gate). */
    public static Map<String, String> owners() {
        final Map<String, String> owners = new LinkedHashMap<>();
        for (Map.Entry<String, MethodHandle> e : HANDLES.entrySet()) {
            owners.put(e.getKey(), LOOKUP.revealDirect(e.getValue()).getDeclaringClass().getName());
        }
        return owners;
    }
}
