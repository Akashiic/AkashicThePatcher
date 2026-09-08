package com.akashic.thepatcher.module.dimdoors;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;

/** Optional ABI access, resolved only after an actual DimDoors tile notification. */
final class DimDoorsReflectionAccess implements DeferredLinks.Access {
    interface ClientContext { Object world(); Object network(); boolean local(); }
    private final ClientContext context;
    DimDoorsReflectionAccess() { this(new MinecraftContext()); }
    DimDoorsReflectionAccess(ClientContext context) { this.context = context; }
    private final ClassValue<TileAccess> tiles = new ClassValue<TileAccess>() {
        @Override protected TileAccess computeValue(Class<?> type) { return new TileAccess(type); }
    };
    private final ClassValue<PocketAccess> pockets = new ClassValue<PocketAccess>() {
        @Override protected PocketAccess computeValue(Class<?> type) { return new PocketAccess(type.getClassLoader()); }
    };
    @Override public Object world(Object tile) { return get(tiles.get(tile.getClass()).world, tile); }
    @Override public boolean current(Object tile, Object world, Object net) {
        if (context.world() != world || context.network() != net || world(tile) != world) return false;
        TileAccess a = tiles.get(tile.getClass());
        if (Boolean.TRUE.equals(invoke(a.invalid, tile))) return false;
        if (!Boolean.TRUE.equals(get(field(world.getClass(), "field_72995_K", "isRemote"), world))) return false;
        return invoke(a.lookup, world, get(a.x, tile), get(a.y, tile), get(a.z, tile)) == tile;
    }
    @Override public boolean ready(Object watcher, Object link) {
        PocketAccess a = pockets.get(watcher.getClass());
        return a.ready(context.local()) && a.dimension(link) != null;
    }
    @Override public boolean apply(Object watcher, Object link) {
        invoke(method(watcher.getClass(), new Class<?>[]{Object.class}, "onCreated"), watcher, link);
        Object dimension = pockets.get(watcher.getClass()).dimension(link);
        if (dimension == null) return false;
        Object point = get(field(link.getClass(), "point"), link);
        return invoke(method(dimension.getClass(), new Class<?>[]{point.getClass()}, "getLink"), dimension, point) != null;
    }
    private static final class TileAccess {
        final Field world, x, y, z;
        final Method invalid, lookup;
        TileAccess(Class<?> tile) {
            world = field(tile, "field_145850_b", "worldObj");
            x = field(tile, "field_145851_c", "xCoord"); y = field(tile, "field_145848_d", "yCoord");
            z = field(tile, "field_145849_e", "zCoord");
            invalid = method(tile, new Class<?>[0], "func_145837_r", "isInvalid");
            lookup = method(world.getType(), new Class<?>[]{int.class,int.class,int.class}, "func_147438_o", "getTileEntity");
        }
    }
    private static final class PocketAccess {
        final Field dimensions, loading, connected;
        final Method loaded;
        PocketAccess(ClassLoader loader) {
            Class<?> pocket = type("org.dimdev.dimdoors.core.PocketManager", loader);
            dimensions = field(pocket, "dimensionData"); loading = field(pocket, "isLoading");
            connected = field(pocket, "isConnected"); loaded = method(pocket, new Class<?>[0], "isLoaded");
        }
        boolean ready(boolean local) {
            return Boolean.TRUE.equals(invoke(loaded, null)) && !Boolean.TRUE.equals(get(loading, null))
                && (local || Boolean.TRUE.equals(get(connected, null)));
        }
        Object dimension(Object link) {
            Object point = get(field(link.getClass(), "point"), link);
            Object id = invoke(method(point.getClass(), new Class<?>[0], "getDimension"), point);
            return DimDoorsClientRuntime.safeMapGet((Map<?, ?>) get(dimensions, null), id);
        }
    }
    static Field field(Class<?> type, String... names) {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) for (String name : names) {
            try { Field f = c.getDeclaredField(name); f.setAccessible(true); return f; }
            catch (NoSuchFieldException absent) { /* try the audited alternative */ }
        }
        throw new IllegalStateException("DimDoors ABI field unavailable: " + type + " / " + names[0]);
    }
    static Method method(Class<?> type, Class<?>[] args, String... names) {
        for (String name : names) try {
            Method m = type.getMethod(name, args); m.setAccessible(true); return m;
        } catch (NoSuchMethodException absent) { /* MCP or SRG */ }
        throw new IllegalStateException("DimDoors ABI method unavailable: " + type + " / " + names[0]);
    }
    static Object get(Field field, Object target) {
        try { return field.get(target); }
        catch (IllegalAccessException e) { throw new IllegalStateException(e); }
    }
    static Object invoke(Method method, Object target, Object... args) {
        try { return method.invoke(target, args); }
        catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw new IllegalStateException("DimDoors callback failed", cause);
        } catch (IllegalAccessException e) { throw new IllegalStateException(e); }
    }
    static Class<?> type(String name, ClassLoader loader) {
        try { return Class.forName(name, false, loader); }
        catch (ClassNotFoundException e) { throw new IllegalStateException(e); }
    }
    private static final class MinecraftContext implements ClientContext {
        private Class<?> mcType;
        private Object minecraft() {
            if (mcType == null) mcType = type("net.minecraft.client.Minecraft", DimDoorsReflectionAccess.class.getClassLoader());
            return invoke(method(mcType, new Class<?>[0], "func_71410_x", "getMinecraft"), null);
        }
        @Override public Object world() {
            Object mc = minecraft();
            return mc == null ? null : get(field(mcType, "field_71441_e", "theWorld"), mc);
        }
        @Override public Object network() {
            Object mc = minecraft();
            if (mc == null) return null;
            Object handler = invoke(method(mcType, new Class<?>[0], "func_147114_u", "getNetHandler"), mc);
            return handler == null ? null : invoke(method(handler.getClass(), new Class<?>[0],
                "func_147298_b", "getNetworkManager"), handler);
        }
        @Override public boolean local() {
            Object mc = minecraft();
            return mc != null && Boolean.TRUE.equals(invoke(method(mcType, new Class<?>[0],
                "func_71387_A", "isIntegratedServerRunning"), mc));
        }
    }
}
