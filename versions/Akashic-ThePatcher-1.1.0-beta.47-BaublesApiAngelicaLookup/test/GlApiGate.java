import java.nio.file.Paths;
import java.util.Collections;
import java.util.Map;

/**
 * AkashicBackpackCompat's GlApi (shape) initialised against the Angelica jar on the class path.
 *
 *   java GlApiGate <shapes dir> moved|glsm|crash
 *
 * moved: initialises, glPushMatrix/glTranslatef... resolve in GLSMMatrices, glEnable in GLSMEnable... (Angelica 2.2.40
 *        with beta.47); glsm: initialises, everything resolves in GLStateManager (Angelica 2.2.37, any jar);
 * crash: ExceptionInInitializerError caused by "Missing GL API glPushMatrix ..." (Angelica 2.2.40 without the bridge).
 * Nothing is invoked (no GL context). Run with -Xverify:all.
 */
public final class GlApiGate {
    static final String TARGET = "br.com.akashic.backpackcompat.client.GlApi";

    public static void main(String[] args) throws Exception {
        final String expect = args[1];
        final TransformingLoader loader = new TransformingLoader(GlApiGate.class.getClassLoader(),
            Paths.get(args[0]), Collections.singletonList(TARGET),
            "com.akashic.thepatcher.module.angelica.AngelicaGlLookupTransformer");
        Map<?, ?> owners = null;
        String failure = null;
        try {
            owners = (Map<?, ?>) Class.forName(TARGET, true, loader).getMethod("owners").invoke(null);
        } catch (ExceptionInInitializerError error) {
            failure = String.valueOf(error.getCause());
        }
        System.out.println("A GlApi -> " + (owners != null ? owners : failure));
        switch (expect) {
            case "moved":
                check(owners != null, "GlApi did not initialise: " + failure);
                check(owners.get("glPushMatrix").equals("com.gtnewhorizons.angelica.glsm.GLSMMatrices")
                    && owners.get("glTranslatef").equals("com.gtnewhorizons.angelica.glsm.GLSMMatrices")
                    && owners.get("glEnable").equals("com.gtnewhorizons.angelica.glsm.GLSMEnable"), "wrong owners");
                for (Object owner : owners.values()) {
                    check(!owner.equals("com.gtnewhorizons.angelica.glsm.GLStateManager"), "still GLStateManager");
                }
                break;
            case "glsm":
                check(owners != null, "GlApi did not initialise: " + failure);
                for (Object owner : owners.values()) {
                    check(owner.equals("com.gtnewhorizons.angelica.glsm.GLStateManager"), "owner " + owner);
                }
                break;
            default:
                check(failure != null && failure.contains("Missing GL API glPushMatrix"), "expected the crash");
        }
        System.out.println("GLAPI_GATE PASS expect=" + expect + " transformed=" + loader.transformed);
    }

    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
