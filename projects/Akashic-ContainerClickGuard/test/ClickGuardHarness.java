import com.akashic.containerclickguard.ClickGuardTransformer;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.Attribute;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.zip.ZipFile;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.network.NetHandlerPlayServer;
import wanion.avaritiaddons.block.chest.infinity.ContainerInfinityChest;

/**
 * Runs the real InventoryTweaks and Avaritiaddons packet handlers, unpatched and patched by ClickGuardTransformer,
 * against test doubles of the server (test/harness-stubs: a Crucible-like processClickWindow with an
 * InventoryClickEvent "plugin").
 *
 *   java -cp <guard.jar:harness classes:stubs:forge-universal:netty:asm-all:launchwrapper:log4j> ClickGuardHarness \
 *        <InventoryTweaks.jar> <Avaritiaddons.jar>
 */
public final class ClickGuardHarness {
    static final String CLICK = "invtweaks.network.handlers.ClickMessageHandler";
    static final String PACKET = "invtweaks.network.packets.ITPacketClick";
    static final String SYNC_ALL = "wanion.avaritiaddons.network.InfinityChestSyncAllSlots$Handler";
    static final String SLOT_SYNC = "wanion.avaritiaddons.network.InfinityChestSlotSync$Handler";
    static final String INF_CLICK = "wanion.avaritiaddons.network.InfinityChestClick$Handler";
    static int failures;

    /** Child-first for the mod JAR's classes; the targets are optionally passed through ClickGuardTransformer. */
    static final class ModLoader extends ClassLoader {
        final ZipFile jar;
        final boolean patch;

        ModLoader(String jar, boolean patch) throws Exception {
            super(ClickGuardHarness.class.getClassLoader());
            this.jar = new ZipFile(jar);
            this.patch = patch;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> c = findLoadedClass(name);
                if (c == null && jar.getEntry(name.replace('.', '/') + ".class") != null
                        && !name.startsWith("wanion.avaritiaddons.block.")) {
                    byte[] b = read(name);
                    if (patch) {
                        b = new ClickGuardTransformer().transform(name, name, b);
                    }
                    c = defineClass(name, b, 0, b.length);
                }
                return c != null ? c : super.loadClass(name, resolve);
            }
        }

        byte[] read(String name) throws ClassNotFoundException {
            try (InputStream in = jar.getInputStream(jar.getEntry(name.replace('.', '/') + ".class"))) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                for (int n; (n = in.read(buf)) > 0; ) {
                    out.write(buf, 0, n);
                }
                return out.toByteArray();
            } catch (Exception e) {
                throw new ClassNotFoundException(name, e);
            }
        }
    }

    static final class World {
        final EntityPlayerMP player = new EntityPlayerMP();
        final NetHandlerPlayServer net = new NetHandlerPlayServer();
        final Container menu;

        World(Container menu, boolean pluginCancelsTopClicks) {
            this.menu = menu;
            player.field_71135_a = net;
            net.field_147369_b = player;
            player.field_71070_bA = menu;
            if (pluginCancelsTopClicks) {
                net.plugin = click -> click.func_149544_d() < menu.top; // a menu plugin: no click on its items
            }
        }

        ItemStack at(int slot) {
            return menu.func_75139_a(slot).stack;
        }

        int count(String item) {
            int n = 0;
            for (int i = menu.top; i < menu.field_75151_b.size(); i++) {
                ItemStack s = menu.func_75139_a(i).stack;
                n += s != null && s.item.equals(item) ? s.size : 0;
            }
            return n;
        }
    }

    public static void main(String[] args) throws Exception {
        String invtweaks = args[0], avaritiaddons = args[1];

        // 1. InvTweaks, plugin menu that cancels every click on its items.
        for (boolean patched : new boolean[] {false, true}) {
            ModLoader l = new ModLoader(invtweaks, patched);
            for (int mode : new int[] {1, 0}) { // shift-click; pick up, then close the GUI
                World w = menu(true);
                click(l, w, 0, 0, mode, w.menu.field_75152_c);
                w.menu.close(w.player);
                boolean stolen = w.count("menu_icon") > 0;
                String what = (patched ? "patched  " : "unpatched") + " InvTweaks mode " + mode
                        + " on a plugin menu: events=" + w.net.events + " slot0=" + w.at(0) + " stolen=" + stolen;
                check(what, patched ? !stolen && w.net.events == 1 && w.player.resyncs == 1 && w.at(0) != null : stolen);
            }
        }

        ModLoader patchedTweaks = new ModLoader(invtweaks, true);
        // 2. Ordinary chest (no plugin): InvTweaks still works, and a sort keeps going after a clickedItem mismatch.
        World chest = menu(false);
        chest.menu.func_75139_a(1).stack = new ItemStack("cobble", 64);
        click(patchedTweaks, chest, 0, 0, 1, chest.menu.field_75152_c);  // shift: menu_icon -> inventory
        click(patchedTweaks, chest, 5, 0, 1, chest.menu.field_75152_c);  // empty slot: result null == expected null
        click(patchedTweaks, chest, 1, 0, 0, chest.menu.field_75152_c);  // pick up cobble
        click(patchedTweaks, chest, 2, 0, 0, chest.menu.field_75152_c);  // put it down
        check("patched InvTweaks on a plain chest: moves go through (events=" + chest.net.events + ")",
                chest.count("menu_icon") == 1 && chest.at(2) != null && chest.at(2).item.equals("cobble")
                        && chest.net.events == 4 && chest.menu.func_75129_b(chest.player));
        // 3. Bad slot numbers are dropped instead of throwing; another window id is ignored (InvTweaks' own check).
        World bad = menu(false);
        click(patchedTweaks, bad, 9999, 0, 0, bad.menu.field_75152_c);
        click(patchedTweaks, bad, -5, 0, 0, bad.menu.field_75152_c);
        click(patchedTweaks, bad, 0, 0, 1, bad.menu.field_75152_c + 1);
        check("patched InvTweaks: slots 9999/-5 dropped, wrong window ignored (events=" + bad.net.events + ")",
                bad.net.events == 0 && bad.player.resyncs == 2 && bad.at(0) != null);
        // 4. Dead player: processClickWindow's own check applies.
        World dead = menu(false);
        dead.player.dead = true;
        click(patchedTweaks, dead, 0, 0, 1, dead.menu.field_75152_c);
        check("patched InvTweaks: dead player's click ignored", dead.at(0) != null && dead.net.events == 0);

        // 5. Avaritiaddons.
        ModLoader unpatchedAva = new ModLoader(avaritiaddons, false), patchedAva = new ModLoader(avaritiaddons, true);
        World plugin = menu(true);
        MessageContext server = new MessageContext(plugin.net, Side.SERVER);
        check("patched InfinityChestSyncAllSlots (client -> server) dropped",
                onMessage(patchedAva, SYNC_ALL, server) == null);
        check("patched InfinityChestSlotSync (client -> server) dropped",
                onMessage(patchedAva, SLOT_SYNC, server) == null);
        check("patched InfinityChestClick with a plugin menu open dropped",
                onMessage(patchedAva, INF_CLICK, server) == null && plugin.at(0) != null);
        check("unpatched InfinityChestSyncAllSlots runs on the server (the hole)",
                throwsFrom(() -> onMessage(unpatchedAva, SYNC_ALL, server)));
        check("guard lets InfinityChestClick through while an Infinity Chest is open", !blocked(new ContainerInfinityChest(7)));
        check("guard ignores the client side",
                !com.akashic.containerclickguard.ClickGuardHooks.blockServerBoundSync(new MessageContext(plugin.net, Side.CLIENT)));

        System.out.println(failures == 0 ? "HARNESS PASS" : "HARNESS FAIL " + failures);
        System.exit(failures == 0 ? 0 : 1);
    }

    static World menu(boolean plugin) {
        Container c = new Container(3, 27, 36);
        c.func_75139_a(0).stack = new ItemStack("menu_icon", 1);
        return new World(c, plugin);
    }

    static void click(ModLoader l, World w, int slot, int data, int action, int window) throws Exception {
        Object handler = l.loadClass(CLICK).getConstructor().newInstance();
        Object packet = l.loadClass(PACKET).getConstructor(int.class, int.class, int.class, int.class)
                .newInstance(slot, data, action, window);
        Attribute<?> attr = (Attribute<?>) Proxy.newProxyInstance(ClickGuardHarness.class.getClassLoader(),
                new Class<?>[] {Attribute.class}, (p, m, a) -> m.getName().equals("get") ? w.net : null);
        Channel channel = (Channel) Proxy.newProxyInstance(ClickGuardHarness.class.getClassLoader(),
                new Class<?>[] {Channel.class}, (p, m, a) -> m.getName().equals("attr") ? attr : null);
        ChannelHandlerContext ctx = (ChannelHandlerContext) Proxy.newProxyInstance(ClickGuardHarness.class.getClassLoader(),
                new Class<?>[] {ChannelHandlerContext.class}, (p, m, a) -> m.getName().equals("channel") ? channel : null);
        Method read = handler.getClass().getDeclaredMethod("channelRead0", ChannelHandlerContext.class, packet.getClass());
        read.setAccessible(true);
        read.invoke(handler, ctx, packet);
    }

    /** Calls onMessage(null message, ctx): the guard returns before the message is read. */
    static Object onMessage(ModLoader l, String handler, MessageContext ctx) throws Exception {
        Class<?> h = l.loadClass(handler);
        Class<?> message = l.loadClass(handler.substring(0, handler.lastIndexOf('$')));
        Method m = h.getMethod("onMessage", message, MessageContext.class);
        try {
            return m.invoke(h.getConstructor().newInstance(), null, ctx);
        } catch (java.lang.reflect.InvocationTargetException e) {
            throw new RuntimeException(e.getCause());
        }
    }

    static boolean blocked(Container open) {
        World w = new World(open, false);
        return com.akashic.containerclickguard.ClickGuardHooks.blockInfinityChestClick(new MessageContext(w.net, Side.SERVER));
    }

    interface Call {
        void run() throws Exception;
    }

    static boolean throwsFrom(Call c) {
        try {
            c.run();
            return false;
        } catch (Throwable t) {
            return true; // reached Avaritiaddons' own code (its proxy is not set up here)
        }
    }

    static void check(String what, boolean ok) {
        System.out.println((ok ? "PASS " : "FAIL ") + what);
        if (!ok) {
            failures++;
        }
    }
}
