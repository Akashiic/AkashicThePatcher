package cpw.mods.fml.common;
import cpw.mods.fml.relauncher.Side;
/** Test-only physical-side fixture. Never packaged with ThePatcher. */
public final class FMLCommonHandler {
    private static final FMLCommonHandler INSTANCE=new FMLCommonHandler();
    public static FMLCommonHandler instance(){return INSTANCE;}
    public Side getSide(){return Side.valueOf(System.getProperty("fixture.side","CLIENT"));}
    public Side getEffectiveSide(){return getSide();}
    public cpw.mods.fml.common.eventhandler.EventBus bus(){throw new AssertionError("Fixture does not emulate game boot");}
}
