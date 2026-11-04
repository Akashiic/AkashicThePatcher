import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * beta.51 gate: GeckoLib's Animation shape (same fields and serialization copy as 1.0.4) loaded through the bundled
 * transformer, as LaunchClassLoader would. Builds an animation of 3,000 keyframes and checks copy():
 *   always    a new Animation with the same values; its loop type is its own; copy(null) is null
 *   patched   the lists are shared (field copy) and it is fast
 *   unpatched the lists are new (serialization copy)
 * Args: shapes-dir patched|unpatched [other] ("other": the shape whose copy is not the serialization copy).
 */
public final class GeckoAnimationCopyGate {

    static final String ANIMATION = "software.bernie.geckolib3.core.builder.Animation";
    static final String LOOP = "software.bernie.geckolib3.core.builder.ILoopType";
    static final String LOOP_TYPES = LOOP + "$EDefaultLoopTypes";
    static final String BONE = "software.bernie.geckolib3.core.keyframe.BoneAnimation";
    static final String LIST = BONE + "$VectorKeyFrameList";
    static final String KEY = BONE + "$KeyFrame";

    public static void main(String[] args) throws Exception {
        final Path shapes = Paths.get(args[0]);
        final String expect = args[1];
        final boolean other = args.length > 2 && "other".equals(args[2]);
        final List<String> owned = other ? Arrays.asList(ANIMATION, LOOP, LOOP_TYPES)
            : Arrays.asList(ANIMATION, LOOP, LOOP_TYPES, BONE, LIST, KEY);
        final TransformingLoader loader = new TransformingLoader(GeckoAnimationCopyGate.class.getClassLoader(), shapes,
            owned, "com.akashic.thepatcher.module.geckolib.GeckoAnimationCopyTransformer");
        final Class<?> animation = Class.forName(ANIMATION, true, loader);
        final Class<?> loopTypes = Class.forName(LOOP_TYPES, true, loader);
        final Object loop = enumConstant(loopTypes, "LOOP");
        final Object once = enumConstant(loopTypes, "PLAY_ONCE");
        final Method copy = animation.getMethod("copy", animation);

        final Object original = animation.newInstance();
        set(original, "animationName", "animation.vara-akashica.aura");
        set(original, "animationLength", Double.valueOf(4.0));
        set(original, "loop", loop);
        set(original, "boneAnimations", other ? new ArrayList<Object>(Arrays.asList("a", "b")) : bones(loader, 3000));
        check(copy.invoke(null, (Object) null) == null, "copy(null) is not null");

        final Object c = copy.invoke(null, original);
        check(c != null && c != original && c.getClass() == animation, "copy is not a new Animation");
        for (Field f : animation.getFields()) {
            if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
            final Object a = f.get(original), b = f.get(c);
            check(a == null ? b == null : a.equals(b) || sameList(a, b), "field " + f.getName() + " differs");
        }
        set(c, "loop", once);
        check(get(original, "loop") == loop, "setting the copy's loop changed the original's");

        final boolean shared = get(c, "boneAnimations") == get(original, "boneAnimations");
        final int n = 20;
        final long t0 = System.nanoTime();
        for (int i = 0; i < n; i++) copy.invoke(null, original);
        final double micros = (System.nanoTime() - t0) / 1e3 / n;
        System.out.println(String.format(java.util.Locale.ROOT,
            "GATE shape=%s transformed=%d sharedLists=%s copy=%.1f us", other ? "other" : "geckolib-1.0.4",
            loader.transformed, shared, micros));
        if ("patched".equals(expect)) {
            check(loader.transformed == 1, "not transformed");
            check(shared, "lists not shared");
            check(micros < 1000, "patched copy too slow: " + micros + " us");
        } else {
            check(loader.transformed == 0, "transformed");
            check(other || !shared, "lists shared without the patch");
        }
        System.out.println("GECKO_COPY_GATE PASS expect=" + expect + (other ? " shape=other" : ""));
    }

    /** n keyframes over bones of 10 frames x 3 channels x 3 axes. */
    static List<Object> bones(ClassLoader loader, int n) throws Exception {
        final Class<?> bone = Class.forName(BONE, true, loader);
        final Class<?> list = Class.forName(LIST, true, loader);
        final Class<?> key = Class.forName(KEY, true, loader);
        final Constructor<?> newList = list.getConstructor(List.class, List.class, List.class);
        final Constructor<?> newKey = key.getConstructor(Double.class, Object.class, Object.class);
        final List<Object> out = new ArrayList<Object>();
        int made = 0;
        for (int b = 0; made < n; b++) {
            final Object ba = bone.newInstance();
            set(ba, "boneName", "osso" + b);
            for (String channel : new String[] { "rotationKeyFrames", "positionKeyFrames", "scaleKeyFrames" }) {
                final List<Object> x = new ArrayList<Object>(), y = new ArrayList<Object>(), z = new ArrayList<Object>();
                for (int k = 0; k < 10; k++, made++) {
                    x.add(newKey.newInstance(Double.valueOf(k / 60.0), Double.valueOf(k), Double.valueOf(k + 1)));
                    y.add(newKey.newInstance(Double.valueOf(k / 60.0), Double.valueOf(-k), Double.valueOf(1 - k)));
                    z.add(newKey.newInstance(Double.valueOf(k / 60.0), Double.valueOf(0), Double.valueOf(0)));
                }
                set(ba, channel, newList.newInstance(x, y, z));
            }
            out.add(ba);
        }
        return out;
    }

    static boolean sameList(Object a, Object b) {
        return a instanceof List && b instanceof List && ((List<?>) a).size() == ((List<?>) b).size();
    }

    static Object enumConstant(Class<?> type, String name) {
        for (Object c : type.getEnumConstants()) {
            if (((Enum<?>) c).name().equals(name)) return c;
        }
        throw new AssertionError("no " + name);
    }

    static void set(Object o, String field, Object value) throws Exception {
        o.getClass().getField(field).set(o, value);
    }

    static Object get(Object o, String field) throws Exception {
        return o.getClass().getField(field).get(o);
    }

    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
