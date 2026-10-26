package akashic.holobench;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import io.netty.buffer.Unpooled;
import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.passive.EntityHorse;
import net.minecraft.entity.projectile.EntityWitherSkull;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.server.S0EPacketSpawnObject;
import net.minecraft.network.play.server.S0FPacketSpawnMob;
import net.minecraft.network.play.server.S13PacketDestroyEntities;
import net.minecraft.network.play.server.S1BPacketEntityAttach;
import net.minecraft.network.play.server.S1CPacketEntityMetadata;
import net.minecraft.world.World;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;

/**
 * Benchmark harness: emulates HolographicDisplays 2.2.6 on a 1.7.x client by feeding the client net handler the
 * exact packets HD's server sends (text line = EntityHorse age -1700000 riding an EntityWitherSkull 54.56 above the
 * line; item line = EntityItem (stack size 0 + antistack lore) riding a skull 0.21 below), then records frame times.
 *
 * -Dholobench.phases=0,300,900   hologram line counts measured in order
 * -Dholobench.linesPer=6         lines per hologram (first line of each hologram is an item when holobench.items=true)
 * -Dholobench.warmup=100 -Dholobench.measure=300   ticks
 */
@Mod(modid = "holobench", name = "HoloBench", version = "1", acceptableRemoteVersions = "*")
public class HoloBench {
    static final String[] TEXTS = {
        "§6§lFORBIDDEN §f§lSERIES", "§7Jogadores online: §a%d", "§eClique para entrar",
        "§bMundo: §fSurvival %d", "§c❤ §fEvento §c❤", "§8----------------"};

    int[] phases = parse(System.getProperty("holobench.phases", "0,300,900"));
    int linesPer = Integer.getInteger("holobench.linesPer", 6);
    boolean items = Boolean.parseBoolean(System.getProperty("holobench.items", "true"));
    int warmup = Integer.getInteger("holobench.warmup", 100), measure = Integer.getInteger("holobench.measure", 300);

    boolean launched;
    int menuTicks;
    int worldTicks, phase = -1, phaseTick;
    double ox, oy, oz;
    final List<int[]> spawned = new ArrayList<int[]>();
    long lastFrame;
    long[] frames = new long[200000];
    int nFrames;
    boolean measuring;
    final StringBuilder results = new StringBuilder();

    static int[] parse(String s) {
        String[] p = s.split(",");
        int[] r = new int[p.length];
        for (int i = 0; i < p.length; i++) r[i] = Integer.parseInt(p[i].trim());
        return r;
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent e) {
        if (FMLCommonHandler.instance().getSide().isClient()) FMLCommonHandler.instance().bus().register(this);
    }

    @SubscribeEvent
    public void render(TickEvent.RenderTickEvent e) {
        if (e.phase != TickEvent.Phase.START) return;
        long now = System.nanoTime();
        if (measuring && lastFrame != 0 && nFrames < frames.length) frames[nFrames++] = now - lastFrame;
        lastFrame = now;
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        try {
            if (mc.theWorld == null) {
                menuTicks++;
                // custom title screens (CinematicMenu) are not GuiMainMenu: launch anyway after 10 s on any screen
                if (!launched && (mc.currentScreen instanceof GuiMainMenu || mc.currentScreen != null && menuTicks > 200)) {
                    launched = true;
                    mc.gameSettings.limitFramerate = 260;
                    mc.gameSettings.enableVsync = false;
                    mc.gameSettings.renderDistanceChunks = 6;
                    mc.gameSettings.pauseOnLostFocus = false;
                    WorldSettings ws = new WorldSettings(1234L, net.minecraft.world.WorldSettings$GameType.CREATIVE, false, false, WorldType.FLAT);
                    String name = "holobench" + System.currentTimeMillis();
                    log("launching world " + name);
                    mc.launchIntegratedServer(name, name, ws);
                }
                return;
            }
            if (mc.thePlayer == null) return;
            worldTicks++;
            mc.thePlayer.capabilities.isFlying = true;
            mc.thePlayer.rotationYaw = 0f;
            mc.thePlayer.rotationPitch = 10f;
            if (worldTicks < 200) return;
            if (worldTicks == 200) {
                ox = Math.floor(mc.thePlayer.posX);
                oy = Math.floor(mc.thePlayer.boundingBox.minY);
                oz = Math.floor(mc.thePlayer.posZ);
                log("origin " + ox + " " + oy + " " + oz);
                nextPhase(mc);
                return;
            }
            phaseTick++;
            if (phaseTick == warmup) {
                measuring = true;
                nFrames = 0;
                lastFrame = 0;
            } else if (phaseTick == warmup + measure) {
                measuring = false;
                report(mc);
                nextPhase(mc);
            }
        } catch (Throwable t) {
            log("FAILED " + t);
            t.printStackTrace();
            mc.shutdown();
        }
    }

    void nextPhase(Minecraft mc) throws Exception {
        phase++;
        phaseTick = 0;
        if (phase >= phases.length) {
            File out = new File(mc.mcDataDir, "holobench-results.txt");
            FileWriter w = new FileWriter(out);
            w.write(results.toString());
            w.close();
            log("DONE -> " + out);
            mc.shutdown();
            return;
        }
        setLines(mc, phases[phase]);
    }

    int countLines() {
        int n = 0;
        for (int[] h : spawned) n += h.length / 2;
        return n;
    }

    void setLines(Minecraft mc, int target) throws Exception {
        while (countLines() > target) {
            int[] ids = spawned.remove(spawned.size() - 1);
            process(mc, new S13PacketDestroyEntities(ids));
        }
        int index = spawned.size();
        while (countLines() < target) {
            int lines = Math.min(linesPer, target - countLines());
            // 9 holograms per row, rows every 6 blocks ahead of the player (+Z), all within ~60 blocks.
            double x = ox + ((index % 9) - 4) * 5.0, z = oz + 6 + (index / 9 % 10) * 6.0, y = oy + 1.5 + lines * 0.25;
            int[] ids = new int[lines * 2];
            for (int l = 0; l < lines; l++) {
                double ly = y - l * 0.25;
                if (items && l == 0) {
                    ids[2 * l] = spawnItemLine(mc, x, ly, z);
                } else {
                    ids[2 * l] = spawnTextLine(mc, x, ly, z, String.format(TEXTS[l % TEXTS.length], index * 7 + l));
                }
                ids[2 * l + 1] = lastSkull;
            }
            spawned.add(ids);
            index++;
        }
        log("lines=" + countLines() + " holograms=" + spawned.size() + " entities=" + mc.theWorld.loadedEntityList.size());
    }

    int lastSkull;

    int spawnTextLine(Minecraft mc, double x, double y, double z, String text) throws Exception {
        World w = mc.theWorld;
        EntityWitherSkull skull = new EntityWitherSkull(w);
        skull.setPosition(x, y + 54.56, z);
        EntityHorse horse = new EntityHorse(w);
        horse.setPosition(x, y + 54.56, z);
        horse.setGrowingAge(-1700000);
        horse.setCustomNameTag(text);
        horse.setAlwaysRenderNameTag(true);
        process(mc, new S0EPacketSpawnObject(skull, 66, 0));
        process(mc, new S0FPacketSpawnMob(horse));
        process(mc, new S1BPacketEntityAttach(0, horse, skull));
        lastSkull = skull.getEntityId();
        return horse.getEntityId();
    }

    int spawnItemLine(Minecraft mc, double x, double y, double z) throws Exception {
        World w = mc.theWorld;
        EntityWitherSkull skull = new EntityWitherSkull(w);
        skull.setPosition(x, y - 0.21, z);
        ItemStack stack = new ItemStack(Items.diamond_sword);
        NBTTagCompound tag = new NBTTagCompound();
        NBTTagCompound display = new NBTTagCompound();
        NBTTagList lore = new NBTTagList();
        lore.appendTag(new NBTTagString("§0HolographicDisplays"));
        display.setTag("Lore", lore);
        tag.setTag("display", display);
        stack.setTagCompound(tag);
        stack.stackSize = 0;
        EntityItem item = new EntityItem(w, x, y - 0.21, z);
        item.setEntityItemStack(stack);
        process(mc, new S0EPacketSpawnObject(skull, 66, 0));
        process(mc, new S0EPacketSpawnObject(item, 2, 1));
        process(mc, new S1CPacketEntityMetadata(item.getEntityId(), item.getDataWatcher(), true));
        process(mc, new S1BPacketEntityAttach(0, item, skull));
        lastSkull = skull.getEntityId();
        return item.getEntityId();
    }

    /** Serialize and deserialize like the wire does, then let the real client handler process it. */
    static void process(Minecraft mc, Packet p) throws Exception {
        PacketBuffer buf = new PacketBuffer(Unpooled.buffer());
        p.writePacketData(buf);
        Packet copy = p.getClass().newInstance();
        copy.readPacketData(buf);
        copy.processPacket(mc.getNetHandler());
    }

    void report(Minecraft mc) {
        long[] f = Arrays.copyOf(frames, nFrames);
        Arrays.sort(f);
        long sum = 0;
        for (long v : f) sum += v;
        double avgMs = nFrames == 0 ? 0 : sum / 1e6 / nFrames;
        double p50 = nFrames == 0 ? 0 : f[nFrames / 2] / 1e6, p99 = nFrames == 0 ? 0 : f[(int) (nFrames * 0.99)] / 1e6;
        int tagsInVanillaRange = 0, horses = 0, skulls = 0, nanSkulls = 0;
        for (Object o : mc.theWorld.loadedEntityList) {
            Entity en = (Entity) o;
            if (en instanceof EntityHorse) { horses++; if (((EntityHorse) en).getGrowingAge() < -1000000 && en.getDistanceSqToEntity(mc.thePlayer) < 4096) tagsInVanillaRange++; }
            if (en instanceof EntityWitherSkull) { skulls++; if (Double.isNaN(en.posX) || Double.isNaN(en.posY)) nanSkulls++; }
        }
        String line = String.format(Locale.ROOT,
            "RESULT lines=%d vanillaTags=%d frames=%d avgFps=%.1f avgMs=%.2f p50Ms=%.2f p99Ms=%.2f horses=%d skulls=%d nanSkulls=%d particles=%s entities=%d",
            countLines(), tagsInVanillaRange, nFrames, avgMs == 0 ? 0 : 1000 / avgMs, avgMs, p50, p99, horses, skulls, nanSkulls,
            mc.effectRenderer.getStatistics(), mc.theWorld.loadedEntityList.size());
        log(line);
        results.append(line).append('\n');
    }

    static void log(String s) {
        System.out.println("[HoloBench] " + s);
    }
}
