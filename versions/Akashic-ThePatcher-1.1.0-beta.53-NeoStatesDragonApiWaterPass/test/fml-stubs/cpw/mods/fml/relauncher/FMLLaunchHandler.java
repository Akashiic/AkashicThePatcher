package cpw.mods.fml.relauncher;

/** Test stub: -Dstub.side=SERVER selects the dedicated-server side. Never shipped. */
public final class FMLLaunchHandler {
    private FMLLaunchHandler() {}

    public static Side side() {
        return Side.valueOf(System.getProperty("stub.side", "CLIENT"));
    }
}
