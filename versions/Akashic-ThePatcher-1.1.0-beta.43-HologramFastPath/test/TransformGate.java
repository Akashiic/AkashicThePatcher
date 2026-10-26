import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import net.minecraft.launchwrapper.IClassTransformer;

/**
 * Runs HologramFastPathTransformer on the gate shapes of the vanilla classes (test/mc-shape, MCP names, the
 * hooked methods with vanilla descriptors) inside an isolated class loader, then exercises the result with the
 * (MCP-compiled) hooks:
 *   expect=patched    hologram horse/skull/item stop ticking (position kept, item still spins), normal horse,
 *                     unridden skull, server-side skull and a dropped item tick as before; carriers leave the
 *                     entity pipeline (range false, RenderManager draws nothing, no second name tag) only once
 *                     RenderGlobal is patched; renderEntities calls renderLines once, before "blockentities";
 *                     a second transformation changes nothing ("already patched").
 *   expect=untouched  every class keeps its bytes (kill-switch, server side).
 *   java TransformGate <mc-shape classes dir> <hooks classes dir> patched|untouched
 */
public final class TransformGate {
    static final String[] TARGETS = {
        "net.minecraft.entity.Entity", "net.minecraft.entity.projectile.EntityFireball",
        "net.minecraft.entity.passive.EntityHorse", "net.minecraft.entity.item.EntityItem",
        "net.minecraft.entity.monster.EntitySlime", "net.minecraft.client.renderer.entity.RenderManager",
        "net.minecraft.client.renderer.entity.RendererLivingEntity", "net.minecraft.client.renderer.RenderGlobal"};

    public static void main(String[] args) throws Exception {
        final String shapes = args[0], hooks = args[1], expect = args[2];
        final IClassTransformer transformer = (IClassTransformer) Class.forName(
            "com.akashic.thepatcher.module.hologram.HologramFastPathTransformer").newInstance();
        final Loader loader = new Loader(shapes, hooks, transformer);
        int changed = 0;
        for (String target : TARGETS) {
            final byte[] original = loader.raw(target);
            final byte[] once = transformer.transform(target, target, original);
            final boolean differs = !java.util.Arrays.equals(original, once);
            changed += differs ? 1 : 0;
            if (differs) {
                check(java.util.Arrays.equals(once, transformer.transform(target, target, once)), "second pass changed " + target);
            }
            System.out.println("A " + target + " changed=" + differs);
        }
        if (expect.equals("untouched")) {
            check(changed == 0, "classes changed: " + changed);
            System.out.println("TRANSFORM_GATE PASS expect=untouched");
            return;
        }
        check(changed == TARGETS.length, "patched " + changed + " of " + TARGETS.length);
        final Class<?> state = Class.forName("com.akashic.thepatcher.module.hologram.HologramFastPathTransformer");
        check(state.getField("rendererInstalled").getBoolean(null), "rendererInstalled not set");

        final Object world = loader.loadClass("net.minecraft.client.multiplayer.WorldClient").newInstance();
        final Object server = loader.loadClass("net.minecraft.world.World").newInstance();

        // text line: horse age -1700000 riding a skull 54.56 above the line
        final Object skull = entity(loader, "net.minecraft.entity.projectile.EntityWitherSkull", world, 10, 60.56, 10);
        final Object horse = entity(loader, "net.minecraft.entity.passive.EntityHorse", world, 10, 61.31, 10);
        set(horse, "age", -1700000);
        set(horse, "customName", "§6Hello");
        mount(horse, skull);
        tick(skull);
        tick(horse);
        check(get(skull, "vanillaTicks").equals(0) && get(horse, "vanillaTicks").equals(0), "carrier ticked vanilla");
        check(get(skull, "posX").equals(10.0) && get(horse, "posY").equals(61.31), "carrier moved");
        check(get(horse, "ticksExisted").equals(1) && get(horse, "prevPosY").equals(61.31), "carrier not held");
        check(Boolean.TRUE.equals(get(horse, "akashic$hologram")), "detection not cached");
        check(!range(horse) && !range(skull), "carrier still in the range check");
        final Object manager = loader.loadClass("net.minecraft.client.renderer.entity.RenderManager").getField("instance").get(null);
        check(Boolean.TRUE.equals(render(manager, horse)) && Boolean.TRUE.equals(render(manager, skull))
            && get(manager, "drawn").equals(0), "carrier drawn by RenderManager");
        final Object living = loader.loadClass("net.minecraft.client.renderer.entity.RendererLivingEntity").newInstance();
        living.getClass().getMethod("callPassSpecialRender", loader.loadClass("net.minecraft.entity.EntityLivingBase")).invoke(living, horse);
        check(get(living, "nameTags").equals(0), "second name tag drawn");
        final Class<?> fast = loader.loadClass("com.akashic.hologramfastpath.HologramFastPath");
        final Method offset = fast.getDeclaredMethod("tagOffset", loader.loadClass("net.minecraft.entity.passive.EntityHorse"));
        offset.setAccessible(true);
        final double tag = (Double) offset.invoke(null, horse);
        // vanilla: height = 1.6 * getHorseSize(), getHorseSize = 0.5 + (-24000 - age) / -24000 * 0.5 (float math)
        final float size = 0.5F + (float) (-24000 - -1700000) / -24000.0F * 0.5F;
        check(tag == 1.6F * size + 0.5F && Math.abs(61.31 + tag - 6.75) < 0.02, "tag offset " + tag);
        System.out.println("A text line: frozen, out of the entity pipeline, tag at line+" + (61.31 + tag - 6.0));

        // item line: item riding a skull keeps still but spins
        final Object itemSkull = entity(loader, "net.minecraft.entity.projectile.EntityWitherSkull", world, 0, 4.79, 0);
        final Object item = entity(loader, "net.minecraft.entity.item.EntityItem", world, 0, 4.79, 0);
        mount(item, itemSkull);
        tick(itemSkull);
        tick(item);
        check(get(item, "vanillaTicks").equals(0) && get(item, "age").equals(1) && get(item, "posY").equals(4.79), "item line");
        check(get(itemSkull, "vanillaTicks").equals(0), "item vehicle ticked");
        check(range(item), "item line left the range check (it keeps the vanilla renderer)");

        // not holograms: normal foal, lone skull, dropped item, server-side skull+horse
        final Object foal = entity(loader, "net.minecraft.entity.passive.EntityHorse", world, 0, 4, 0);
        set(foal, "age", -24000);
        final Object loneSkull = entity(loader, "net.minecraft.entity.projectile.EntityWitherSkull", world, 0, 4, 0);
        final Object dropped = entity(loader, "net.minecraft.entity.item.EntityItem", world, 0, 4, 0);
        final Object serverSkull = entity(loader, "net.minecraft.entity.projectile.EntityWitherSkull", server, 0, 70, 0);
        final Object serverHorse = entity(loader, "net.minecraft.entity.passive.EntityHorse", server, 0, 70, 0);
        set(serverHorse, "age", -1700000);
        mount(serverHorse, serverSkull);
        for (Object o : new Object[] {foal, loneSkull, dropped, serverSkull, serverHorse}) {
            tick(o);
            check(get(o, "vanillaTicks").equals(1), "vanilla tick skipped for " + o.getClass().getSimpleName());
            check(range(o), "range check changed for " + o.getClass().getSimpleName());
        }
        check(Boolean.TRUE.equals(render(manager, foal)) && get(manager, "drawn").equals(1), "normal horse not drawn");
        System.out.println("A normal foal, lone skull, dropped item, server-side carriers: vanilla");

        final Object global = loader.loadClass("net.minecraft.client.renderer.RenderGlobal").newInstance();
        final long before = (Long) fast.getMethod("renderPasses").invoke(null);
        global.getClass().getMethod("renderEntities", loader.loadClass("net.minecraft.entity.EntityLivingBase"),
            loader.loadClass("net.minecraft.client.renderer.culling.ICamera"), float.class).invoke(global, null, null, 0.5F);
        check((Long) fast.getMethod("renderPasses").invoke(null) == before + 1, "renderLines not called once");
        final Object profiler = get(global, "theProfiler");
        check(String.valueOf(get(profiler, "sections")).equals("[entities, blockentities, done]"), "profiler sections changed");
        System.out.println("A renderEntities: renderLines once, sections " + get(profiler, "sections"));
        System.out.println("TRANSFORM_GATE PASS expect=patched");
    }

    static boolean range(Object entity) throws Exception {
        return (Boolean) entity.getClass().getMethod("isInRangeToRender3d", double.class, double.class, double.class)
            .invoke(entity, 0.0, 4.0, 0.0);
    }

    static Object render(Object manager, Object entity) throws Exception {
        return manager.getClass().getMethod("doRenderEntity", entity.getClass().getClassLoader()
            .loadClass("net.minecraft.entity.Entity"), double.class, double.class, double.class, float.class, float.class,
            boolean.class).invoke(manager, entity, 0.0, 0.0, 0.0, 0F, 0F, false);
    }

    static void tick(Object entity) throws Exception {
        entity.getClass().getMethod("onUpdate").invoke(entity);
    }

    static Object entity(ClassLoader loader, String name, Object world, double x, double y, double z) throws Exception {
        final Object e = loader.loadClass(name).newInstance();
        set(e, "worldObj", world);
        set(e, "posX", x);
        set(e, "posY", y);
        set(e, "posZ", z);
        return e;
    }

    static void mount(Object rider, Object vehicle) throws Exception {
        set(rider, "ridingEntity", vehicle);
        set(vehicle, "riddenByEntity", rider);
    }

    static Field field(Object o, String name) throws Exception {
        for (Class<?> c = o.getClass(); c != null; c = c.getSuperclass()) {
            try {
                final Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException next) {
                // try the super class
            }
        }
        throw new NoSuchFieldException(name);
    }

    static Object get(Object o, String name) throws Exception {
        return field(o, name).get(o);
    }

    static void set(Object o, String name, Object value) throws Exception {
        field(o, name).set(o, value);
    }

    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    /** Child-first for the shapes (transformed on load) and the hooks; everything else from the parent. */
    static final class Loader extends ClassLoader {
        private final String shapes, hooks;
        private final IClassTransformer transformer;

        Loader(String shapes, String hooks, IClassTransformer transformer) {
            super(TransformGate.class.getClassLoader());
            this.shapes = shapes;
            this.hooks = hooks;
            this.transformer = transformer;
        }

        byte[] raw(String name) throws IOException {
            return read(shapes, name);
        }

        @Override
        protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            final boolean shape = name.startsWith("net.minecraft.") || name.startsWith("net.minecraftforge.");
            final boolean hook = name.startsWith("com.akashic.hologramfastpath.");
            if (!shape && !hook) {
                return super.loadClass(name, resolve);
            }
            Class<?> c = findLoadedClass(name);
            if (c == null) {
                try {
                    byte[] bytes = read(shape ? shapes : hooks, name);
                    if (shape) bytes = transformer.transform(name, name, bytes);
                    c = defineClass(name, bytes, 0, bytes.length);
                } catch (IOException e) {
                    throw new ClassNotFoundException(name, e);
                }
            }
            if (resolve) resolveClass(c);
            return c;
        }

        private static byte[] read(String dir, String name) throws IOException {
            try (InputStream in = new java.io.FileInputStream(dir + "/" + name.replace('.', '/') + ".class")) {
                final ByteArrayOutputStream out = new ByteArrayOutputStream();
                final byte[] buffer = new byte[8192];
                for (int n; (n = in.read(buffer)) != -1;) out.write(buffer, 0, n);
                return out.toByteArray();
            }
        }
    }

}
