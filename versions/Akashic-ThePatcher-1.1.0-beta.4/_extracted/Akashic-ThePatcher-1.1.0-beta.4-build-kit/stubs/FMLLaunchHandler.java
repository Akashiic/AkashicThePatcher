package cpw.mods.fml.relauncher;
/** Test-only side selector. Never packaged in the mod. */
public final class FMLLaunchHandler {
    public static Side side(){return Side.valueOf(System.getProperty("fixture.side","CLIENT"));}
}
