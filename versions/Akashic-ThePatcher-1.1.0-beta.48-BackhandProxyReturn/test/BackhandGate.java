import java.io.File;
import java.lang.reflect.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;

/**
 * Drives the shipped BackhandGuardHooks (with its own PatchConfig/PatchLog/Reflect from the same JAR, in an isolated
 * class loader) through the event order of latest.log 2026-10-31, and checks the resulting state.
 *
 *   java -Xverify:all -cp <gate classes> BackhandGate <jar> <expect: fixed|bug> [kill]
 *
 * "bug" is the beta.47 expectation (the scenario reproduces the defect), "fixed" the beta.48 one. "kill" sets
 * -Dakashic.backhandProxyReturn.disable=true before the class is loaded (expect: bug).
 */
public final class BackhandGate {
    /** FML's ClientConnectedToServerEvent shape (fields read reflectively by the hooks). */
    public static final class Connected { public Object manager; public boolean isLocal; public String connectionType;
        Connected(Object m, String t) { manager = m; isLocal = false; connectionType = t; } }
    /** FMLHandshakeMessage.ModList shape. */
    public static final class ModList { final Map<String, String> mods;
        ModList(String... ids) { mods = new LinkedHashMap<>(); for (String id : ids) mods.put(id, "1"); }
        public Map<String, String> modList() { return mods; } }
    /** FMLNetworkEvent.CustomPacketRegistrationEvent shape. */
    public static final class Registration { public Object side = "CLIENT", manager; public String operation; public Set<String> registrations;
        Registration(Object m, String op, String... ch) { manager = m; operation = op; registrations = new LinkedHashSet<>(Arrays.asList(ch)); } }

    static Class<?> hooks;
    static int failures;

    static Object call(String name, Object... args) throws Exception {
        for (Method m : hooks.getDeclaredMethods())
            if (m.getName().equals(name) && m.getParameterCount() == args.length) {
                m.setAccessible(true);
                return m.invoke(null, args);
            }
        throw new NoSuchMethodException(name);
    }

    static Object field(String name) throws Exception {
        Field f = hooks.getDeclaredField(name);
        f.setAccessible(true);
        return f.get(null);
    }

    static void check(String step, String type, boolean offhand) throws Exception {
        String actualType = (String) field("connectionType");
        boolean actualOffhand = (Boolean) call("isOffhandActionAllowed");
        boolean ok = type.equals(actualType) && offhand == actualOffhand;
        if (!ok) failures++;
        System.out.println("GATE " + (ok ? "ok  " : "FAIL") + " " + step + ": type=" + actualType + " offhand=" + actualOffhand
                + " (want " + type + "/" + offhand + ")");
    }

    public static void main(String[] args) throws Exception {
        String expect = args[1];
        if (args.length > 2 && "kill".equals(args[2])) System.setProperty("akashic.backhandProxyReturn.disable", "true");
        Path config = Files.createTempDirectory("backhand-gate");
        System.setProperty("minecraft.applet.TargetDirectory", config.toString());
        URLClassLoader loader = new URLClassLoader(new URL[]{new File(args[0]).toURI().toURL()}, null);
        Class<?> cfg = Class.forName("com.akashic.forbiddenbackhand.config.PatchConfig", true, loader);
        cfg.getMethod("resetForTests", File.class, Properties.class).invoke(null, config.toFile(), new Properties());
        hooks = Class.forName("com.akashic.forbiddenbackhand.core.BackhandGuardHooks", true, loader);
        boolean fixed = "fixed".equals(expect);
        Object proxy = new Object();          // one NetworkManager for the whole session, like Velocity
        Object packet = new Object(), handler = new Object();

        // 00:21:45 fresh connection: Velocity's first vanilla JoinGame, then the Forge backend
        call("onClientConnectedEvent", new Connected(proxy, "VANILLA"));
        call("onJoinGamePacket", handler, packet);
        check("fresh connect, vanilla first server", "VANILLA", false);
        call("captureRemoteModList", new ModList("FML", "Forge", "backhand"));
        call("onClientConnectedEvent", new Connected(proxy, "MODDED"));
        call("onJoinGamePacket", handler, packet);
        check("fresh connect, backend JoinGame", "MODDED", true);

        // 00:29:43 backend -> lobby (Paper 1.21 + ViaVersion): JoinGame only, no FML handshake
        call("onJoinGamePacket", handler, packet);
        check("switch to the vanilla lobby", "TRANSITION", false);
        call("onCustomPacketRegistrationEvent", new Registration(proxy, "REGISTER", "BungeeCord"));
        check("lobby channel register", "TRANSITION", false);

        // 00:33:13 lobby -> backend: reset (not seen by the hook in that log), channels, mod list, connected, JoinGame
        call("onCustomPacketRegistrationEvent", new Registration(proxy, "REGISTER", "backhand", "FML|HS"));
        call("captureRemoteModList", new ModList("FML", "Forge", "backhand"));
        check("return, mod list captured", "MODDED", true);
        call("onClientConnectedEvent", new Connected(proxy, "MODDED"));
        call("onJoinGamePacket", handler, packet);
        check("return, backend JoinGame", fixed ? "MODDED" : "TRANSITION", fixed);
        // the Respawn the proxy sends right after is not a JoinGame; a second backend JoinGame without a new mod list
        // (another proxy switch) must not reuse the proof
        call("onJoinGamePacket", handler, packet);
        check("next switch, no new mod list", "TRANSITION", false);
        call("onJoinGamePacket", handler, packet);
        check("and the one after", "TRANSITION", false);

        // the reset hook path (FMLHandshakeClientState saw HandshakeReset): unchanged behaviour
        call("captureHandshakeStateMessage", new HandshakeReset());
        check("FML handshake reset", "TRANSITION", false);
        call("captureRemoteModList", new ModList("FML", "Forge", "backhand"));
        call("onJoinGamePacket", handler, packet);
        check("after reset: mod list then JoinGame", "MODDED", true);

        // a Forge backend without Backhand, through the same path: MODDED but offhand stays off
        call("onJoinGamePacket", handler, packet);
        call("captureRemoteModList", new ModList("FML", "Forge"));
        call("onJoinGamePacket", handler, packet);
        check("return to a backend without backhand", fixed ? "MODDED" : "TRANSITION", false);

        // disconnect clears everything
        call("onClientDisconnectedEvent", new Object());
        check("disconnect", "DISCONNECTED", false);

        System.out.println("BACKHAND_GATE " + (failures == 0 ? "PASS" : "FAIL") + " expect=" + expect + " failures=" + failures);
        System.exit(failures == 0 ? 0 : 1);
    }

    public static final class HandshakeReset {}
}
