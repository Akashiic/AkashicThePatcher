package elytratest;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.registry.GameRegistry;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/** Headless elytra probe: creates a flat SP world, equips an elytra 150 blocks up, presses jump, logs state. */
@Mod(modid = "elytratest", name = "ElytraTest", version = "1")
public class ElytraTest {
    static void log(String s) { System.out.println("[ELYTRATEST] " + s); }

    static Method m(Class<?> c, String name, Class<?>... args) throws Exception {
        for (Class<?> k = c; k != null; k = k.getSuperclass()) {
            try { Method x = k.getDeclaredMethod(name, args); x.setAccessible(true); return x; } catch (NoSuchMethodException e) {}
        }
        throw new NoSuchMethodException(c + "." + name);
    }
    static Field f(Class<?> c, String name) throws Exception {
        for (Class<?> k = c; k != null; k = k.getSuperclass()) {
            try { Field x = k.getDeclaredField(name); x.setAccessible(true); return x; } catch (NoSuchFieldException e) {}
        }
        throw new NoSuchFieldException(c + "." + name);
    }
    static Object get(Object o, String name) throws Exception { return f(o.getClass(), name).get(o); }
    static Object call(Object o, String name) throws Exception { return m(o.getClass(), name).invoke(o); }

    int clientTicks, worldTicks, jumpAt = -1;
    boolean launched, equipped, done, second, described;
    int directAt = -1;
    Object mc;

    @Mod.EventHandler
    public void init(FMLInitializationEvent e) {
        FMLCommonHandler.instance().bus().register(this);
        log("registered");
    }

    String state(Object p) {
        try {
            Boolean fly = null;
            try { fly = (Boolean) m(p.getClass(), "etfu$isElytraFlying").invoke(p); } catch (Throwable t) { fly = null; }
            Object chest = m(p.getClass(), "func_71124_b", int.class).invoke(p, 3);
            Object flags = m(p.getClass(), "func_70083_f", int.class).invoke(p, 7);
            return String.format("y=%.2f my=%.3f mx=%.3f mz=%.3f onGround=%s flying(efr)=%s flag7=%s capFly=%s chest=%s pitch=%.1f",
                f(p.getClass(), "field_70163_u").getDouble(p), f(p.getClass(), "field_70181_x").getDouble(p),
                f(p.getClass(), "field_70159_w").getDouble(p), f(p.getClass(), "field_70179_y").getDouble(p),
                f(p.getClass(), "field_70122_E").getBoolean(p), fly, flags,
                f(get(p, "field_71075_bZ").getClass(), "field_75100_b").getBoolean(get(p, "field_71075_bZ")), chest,
                f(p.getClass(), "field_70125_A").getFloat(p));
        } catch (Throwable t) { return "state-error " + t; }
    }

    @SubscribeEvent
    public void serverTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || done) return;
        try {
            Class<?> ms = Class.forName("net.minecraft.server.MinecraftServer");
            Object server = m(ms, "func_71276_C").invoke(null);
            Object scm = m(server.getClass(), "func_71203_ab").invoke(server);
            List<?> players = (List<?>) f(scm.getClass(), "field_72404_b").get(scm);
            if (players.isEmpty()) return;
            Object p = players.get(0);
            if (!equipped && worldTicks > 100) {
                Object item = GameRegistry.class.getMethod("findItem", String.class, String.class).invoke(null, "etfuturum", "elytra");
                log("elytra item = " + item);
                Class<?> isc = Class.forName("net.minecraft.item.ItemStack");
                Object stack = isc.getConstructor(Class.forName("net.minecraft.item.Item")).newInstance(item);
                m(p.getClass(), "func_70062_b", int.class, isc).invoke(p, 3, stack);
                Object nh = get(p, "field_71135_a");
                double x = f(p.getClass(), "field_70165_t").getDouble(p), z = f(p.getClass(), "field_70161_v").getDouble(p);
                m(nh.getClass(), "func_147364_a", double.class, double.class, double.class, float.class, float.class).invoke(nh, x, 150.0, z, 0f, 10f);
                log("server: equipped + teleported; class=" + p.getClass().getName() + " interfaces IElytraPlayer=" + Class.forName("ganymedes01.etfuturum.elytra.IElytraPlayer").isInstance(p));
                equipped = true;
            }
            if (equipped && !second && worldTicks >= 300) {
                Object nh = get(p, "field_71135_a");
                double x = f(p.getClass(), "field_70165_t").getDouble(p), z = f(p.getClass(), "field_70161_v").getDouble(p);
                m(nh.getClass(), "func_147364_a", double.class, double.class, double.class, float.class, float.class).invoke(nh, x, 150.0, z, 0f, 10f);
                m(p.getClass(), "func_70606_j", float.class).invoke(p, 20f);
                second = true;
                log("server: second teleport");
            }
            if (equipped && worldTicks % 2 == 0 && worldTicks < 460) log("S t=" + worldTicks + " " + state(p) + " hp=" + m(p.getClass(), "func_110143_aJ").invoke(p));
        } catch (Throwable t) { log("server error " + t); t.printStackTrace(); }
    }

    @SubscribeEvent
    public void clientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END || done) return;
        clientTicks++;
        try {
            if (mc == null) mc = m(Class.forName("net.minecraft.client.Minecraft"), "func_71410_x").invoke(null);
            Object world = get(mc, "field_71441_e");
            Object player = get(mc, "field_71439_g");
            if (!launched && world == null && clientTicks > 200) {
                Class<?> gt = Class.forName("net.minecraft.world.WorldSettings$GameType");
                Object survival = m(gt, "func_77146_a", int.class).invoke(null, 0);
                Class<?> wt = Class.forName("net.minecraft.world.WorldType");
                Object flat = m(wt, "func_77130_a", String.class).invoke(null, "flat");
                Class<?> ws = Class.forName("net.minecraft.world.WorldSettings");
                Object settings = ws.getConstructor(long.class, gt, boolean.class, boolean.class, wt).newInstance(1234L, survival, false, false, flat);
                log("launching integrated server");
                launched = true;
                m(mc.getClass(), "func_71371_a", String.class, String.class, ws).invoke(mc, System.getProperty("elytratest.world", "elytratest"), System.getProperty("elytratest.world", "elytratest"), settings);
                return;
            }
            if (world == null || player == null) return;
            worldTicks++;
            Object screen = get(mc, "field_71462_r");
            if (screen != null && worldTicks > 40) {
                log("closing screen " + screen.getClass().getName());
                m(mc.getClass(), "func_147108_a", Class.forName("net.minecraft.client.gui.GuiScreen")).invoke(mc, (Object) null);
            }
            Object gs = get(mc, "field_71474_y");
            Object kb = get(gs, "field_74314_A");
            int key = (Integer) m(kb.getClass(), "func_151463_i").invoke(kb);
            Method setState = m(kb.getClass(), "func_74510_a", int.class, boolean.class);
            if (equipped && jumpAt < 0 && !f(player.getClass(), "field_70122_E").getBoolean(player)
                    && f(player.getClass(), "field_70163_u").getDouble(player) > 100) jumpAt = worldTicks + 10;
            if (jumpAt > 0) {
                // press jump at jumpAt for 2 ticks, retry at +40 and +80
                int d = worldTicks - jumpAt;
                boolean press = (d >= 0 && d < 2) || (d >= 40 && d < 42) || (d >= 80 && d < 82);
                setState.invoke(null, key, press);
                if (press) log("C t=" + worldTicks + " JUMP pressed");
            }
            if (!described) {
                described = true;
                StringBuilder sb = new StringBuilder("client player class chain:");
                for (Class<?> k = player.getClass(); k != null; k = k.getSuperclass()) sb.append(' ').append(k.getName());
                log(sb.toString());
                Class<?> sp = Class.forName("net.minecraft.client.entity.EntityPlayerSP");
                StringBuilder mm = new StringBuilder("EntityPlayerSP mixin members:");
                for (Field fl : sp.getDeclaredFields()) if (fl.getName().contains("etfu") || fl.getName().contains("Elytra")) mm.append(" F:").append(fl.getName());
                for (Method me : sp.getDeclaredMethods()) if (me.getName().toLowerCase().contains("elytra") || me.getName().contains("etfu")) mm.append(" M:").append(me.getName());
                log(mm.toString());
            }
            Object mi = get(player, "field_71158_b");
            boolean jumpIn = f(mi.getClass(), "field_78901_c").getBoolean(mi);
            if (jumpIn) log("C t=" + worldTicks + " movementInput.jump=true onGround=" + f(player.getClass(), "field_70122_E").getBoolean(player));
            if (second && directAt < 0 && !f(player.getClass(), "field_70122_E").getBoolean(player) && f(player.getClass(), "field_70163_u").getDouble(player) > 100) directAt = worldTicks + 10;
            if (directAt > 0 && worldTicks == directAt) {
                Object wrapper = Class.forName("ganymedes01.etfuturum.EtFuturum").getField("networkWrapper").get(null);
                Object msg = Class.forName("ganymedes01.etfuturum.network.StartElytraFlyingMessage").newInstance();
                m(wrapper.getClass(), "sendToServer", Class.forName("cpw.mods.fml.common.network.simpleimpl.IMessage")).invoke(wrapper, msg);
                log("C t=" + worldTicks + " sent StartElytraFlyingMessage directly");
            }
            if (equipped && worldTicks % 2 == 0) log("C t=" + worldTicks + " " + state(player) + " screen=" + (screen == null ? null : screen.getClass().getSimpleName()));
            if (worldTicks > 470) {
                done = true;
                log("DONE");
                Runtime.getRuntime().halt(0);
            }
        } catch (Throwable t) { log("client error " + t); t.printStackTrace(); }
    }
}
