package dhshaderprobe;

import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.enums.rendering.EDhApiRenderPass;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBeforeBufferRenderEvent;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBeforeRenderPassEvent;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiEventParam;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiRenderParam;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Headless probe for Mirai x Angelica shaders: creates a world, looks at the horizon and logs DH/Iris state. */
@Mod(modid = "dhshaderprobe", name = "DhShaderProbe", version = "1")
public class DhShaderProbe {
    static void log(String s) { System.out.println("[DHPROBE] " + s); }
    static Method m(Class<?> c, String name, Class<?>... a) throws Exception {
        for (Class<?> k = c; k != null; k = k.getSuperclass()) {
            try { Method x = k.getDeclaredMethod(name, a); x.setAccessible(true); return x; } catch (NoSuchMethodException e) {}
        }
        throw new NoSuchMethodException(c + "." + name);
    }
    static Field f(Class<?> c, String name) throws Exception {
        for (Class<?> k = c; k != null; k = k.getSuperclass()) {
            try { Field x = k.getDeclaredField(name); x.setAccessible(true); return x; } catch (NoSuchFieldException e) {}
        }
        throw new NoSuchFieldException(c + "." + name);
    }

    static volatile int opaquePasses, transparentPasses, combinedPasses, bufferRenders, bufferRendersInTransparent;
    static volatile boolean inTransparent;
    int clientTicks, worldTicks;
    boolean launched, bound;
    Object mc;

    @Mod.EventHandler
    public void init(FMLInitializationEvent e) {
        FMLCommonHandler.instance().bus().register(this);
        log("registered");
    }

    void bindDh() {
        DhApi.events.bind(DhApiBeforeRenderPassEvent.class, new DhApiBeforeRenderPassEvent() {
            @Override public void beforeRender(DhApiEventParam<DhApiRenderParam> ev) {
                EDhApiRenderPass p = ev.value.renderPass;
                inTransparent = p == EDhApiRenderPass.TRANSPARENT;
                if (p == EDhApiRenderPass.OPAQUE) opaquePasses++;
                else if (p == EDhApiRenderPass.TRANSPARENT) transparentPasses++;
                else combinedPasses++;
            }
        });
        DhApi.events.bind(DhApiBeforeBufferRenderEvent.class, new DhApiBeforeBufferRenderEvent() {
            @Override public void beforeRender(DhApiEventParam<EventParam> ev) {
                bufferRenders++;
                if (inTransparent) bufferRendersInTransparent++;
            }
        });
        bound = true;
        log("DH events bound");
    }

    String state() {
        StringBuilder sb = new StringBuilder();
        try { sb.append("shaderPackInUse=").append(Class.forName("net.irisshaders.iris.api.v0.IrisApi").getMethod("getInstance").invoke(null).getClass().getMethod("isShaderPackInUse").invoke(Class.forName("net.irisshaders.iris.api.v0.IrisApi").getMethod("getInstance").invoke(null))); } catch (Throwable t) { sb.append("shaderPackInUse=?").append(t); }
        try { sb.append(" irisDhPresent=").append(Class.forName("net.coderbot.iris.compat.dh.DHCompat").getMethod("isPresent").invoke(null)); } catch (Throwable t) { sb.append(" irisDhPresent=?"); }
        try { sb.append(" irisDhRendering=").append(Class.forName("net.coderbot.iris.compat.dh.DHCompat").getMethod("hasRenderingEnabled").invoke(null)); } catch (Throwable t) { sb.append(" irisDhRendering=?"); }
        try { sb.append(" deferTransparent=").append(DhApi.Delayed.renderProxy == null ? null : DhApi.Delayed.renderProxy.getDeferTransparentRendering()); } catch (Throwable t) { sb.append(" deferTransparent=?"); }
        try { sb.append(" dhRenderingEnabled=").append(DhApi.Delayed.configs == null ? null : DhApi.Delayed.configs.graphics().renderingEnabled().getValue()); } catch (Throwable t) { sb.append(" dhRenderingEnabled=?"); }
        sb.append(" passes(opaque=").append(opaquePasses).append(" transparent=").append(transparentPasses).append(" combined=").append(combinedPasses)
          .append(") buffers=").append(bufferRenders).append(" buffersInTransparent=").append(bufferRendersInTransparent);
        return sb.toString();
    }

    static Object field(Object o, String name) throws Exception { return f(o.getClass(), name).get(o); }

    void diag() {
        try {
            Class<?> iris = Class.forName("net.coderbot.iris.Iris");
            Object packOpt = iris.getMethod("getCurrentPack").invoke(null);
            Object pack = ((java.util.Optional<?>) packOpt).orElse(null);
            log("diag pack=" + (pack == null ? null : pack.getClass().getName()));
            if (pack != null) {
                Object set = pack.getClass().getMethod("getProgramSet", String.class).invoke(pack, "Overworld");
                for (String fn : new String[] {"dhTerrain", "dhWater", "dhGeneric", "gbuffersTerrain"}) {
                    Object src = field(set, fn);
                    log("diag " + fn + " name=" + field(src, "name") + " vertex=" + (field(src, "vertexSource") != null) + " fragment=" + (field(src, "fragmentSource") != null));
                }
                log("diag disabled=" + field(pack, "dimensionMap") + " folders=" + field(pack, "foldersWithShaderFiles"));
            }
            Object pm = iris.getMethod("getPipelineManager").invoke(null);
            Object pipe = pm.getClass().getMethod("getPipelineNullable").invoke(pm);
            log("diag pipeline=" + (pipe == null ? null : pipe.getClass().getSimpleName()));
            if (pipe != null) {
                Object compat = pipe.getClass().getMethod("getDHCompat").invoke(pipe);
                Object inst = compat == null ? null : compat.getClass().getMethod("getInstance").invoke(compat);
                log("diag dhCompat=" + compat + " instance=" + inst + (inst == null ? "" : " shouldOverride=" + field(inst, "shouldOverride") + " incompatible=" + field(inst, "incompatible")));
                log("diag terrainShader=" + pipe.getClass().getMethod("getDHTerrainShader").invoke(pipe));
            }
        } catch (Throwable t) { log("diag error " + t); t.printStackTrace(); }
    }

    @SubscribeEvent
    public void clientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        clientTicks++;
        try {
            if (mc == null) mc = m(Class.forName("net.minecraft.client.Minecraft"), "func_71410_x").invoke(null);
            if (!bound && DhApi.Delayed.configs != null) bindDh();
            Object world = f(mc.getClass(), "field_71441_e").get(mc);
            Object player = f(mc.getClass(), "field_71439_g").get(mc);
            if (!launched && world == null && clientTicks > 100) {
                Object settingsObj = f(mc.getClass(), "field_71474_y").get(mc);
                f(settingsObj.getClass(), "field_151451_c").setInt(settingsObj, Integer.getInteger("probe.mcRenderDistance", 4));
                Class<?> gt = Class.forName("net.minecraft.world.WorldSettings$GameType");
                Object creative = m(gt, "func_77146_a", int.class).invoke(null, 1);
                Class<?> wt = Class.forName("net.minecraft.world.WorldType");
                Object type = m(wt, "func_77130_a", String.class).invoke(null, System.getProperty("probe.worldType", "default"));
                Class<?> ws = Class.forName("net.minecraft.world.WorldSettings");
                Object settings = ws.getConstructor(long.class, gt, boolean.class, boolean.class, wt).newInstance(Long.getLong("probe.seed", 20261026L), creative, false, false, type);
                m(ws, "func_77166_b").invoke(settings); // enableCommands
                log("launching integrated server " + state());
                launched = true;
                String name = System.getProperty("probe.world", "dhprobe");
                m(mc.getClass(), "func_71371_a", String.class, String.class, ws).invoke(mc, name, name, settings);
                return;
            }
            if (world != null && player != null) {
                worldTicks++;
                // look at the horizon from above the terrain, fixed time and weather
                f(player.getClass(), "field_70177_z").setFloat(player, 0f);
                f(player.getClass(), "field_70125_A").setFloat(player, Float.parseFloat(System.getProperty("probe.pitch", "6")));
                f(player.getClass(), "field_70126_B").setFloat(player, 0f);
                f(player.getClass(), "field_70127_C").setFloat(player, Float.parseFloat(System.getProperty("probe.pitch", "6")));
                Object caps = f(player.getClass(), "field_71075_bZ").get(player);
                f(caps.getClass(), "field_75100_b").setBoolean(caps, true);
                if (worldTicks == 40) {
                    Object nh = f(player.getClass(), "field_71174_a").get(player);
                    Class<?> chat = Class.forName("net.minecraft.client.entity.EntityClientPlayerMP");
                    m(chat, "func_71165_d", String.class).invoke(player, "/tp @p ~ " + Integer.getInteger("probe.y", 120) + " ~");
                    m(chat, "func_71165_d", String.class).invoke(player, "/time set 6000");
                    m(chat, "func_71165_d", String.class).invoke(player, "/gamerule doDaylightCycle false");
                    m(chat, "func_71165_d", String.class).invoke(player, "/weather clear 100000");
                    log("teleported " + nh.getClass().getSimpleName());
                }
                if (worldTicks % 100 == 0) log("t=" + worldTicks + " " + state());
                if (worldTicks == 200 || worldTicks == 1200) diag();
            }
        } catch (Throwable t) { log("error " + t); t.printStackTrace(); }
    }
}
