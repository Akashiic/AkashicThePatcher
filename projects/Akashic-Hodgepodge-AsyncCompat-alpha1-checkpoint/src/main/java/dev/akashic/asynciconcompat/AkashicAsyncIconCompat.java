package dev.akashic.asynciconcompat;

import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLLoadCompleteEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

@Mod(
    modid = AkashicAsyncIconCompat.MOD_ID,
    name = "Akashic Hodgepodge Async Compat",
    version = "0.1.0-alpha.1",
    dependencies = "required-after:hodgepodge"
)
public final class AkashicAsyncIconCompat {
    public static final String MOD_ID = "akashicasynccompat";

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        FMLLog.info("[AkashicAsyncCompat] Compatibility guards armed.");
    }

    @Mod.EventHandler
    public void loadComplete(FMLLoadCompleteEvent event) {
        FMLLog.info(
            "[AkashicAsyncCompat] Guard status: Chisel=%s, Mekanism=%s",
            CompatState.isChiselPatched(),
            CompatState.isMekanismPatched());
    }
}