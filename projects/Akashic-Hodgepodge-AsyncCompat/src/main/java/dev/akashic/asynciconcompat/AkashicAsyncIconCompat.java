package dev.akashic.asynciconcompat;

import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLLoadCompleteEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

@Mod(
    modid = AkashicAsyncIconCompat.MOD_ID,
    name = "Akashic Hodgepodge Async Compat",
    version = "0.1.0-alpha.2.8",
    dependencies = "required-after:hodgepodge"
)
public final class AkashicAsyncIconCompat {
    public static final String MOD_ID = "akashicasynccompat";

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        FMLLog.info(
            "[AkashicAsyncCompat] Mixin scheduler observed before preInit=%s.",
            CompatState.isSchedulerObserved());
    }

    @Mod.EventHandler
    public void loadComplete(FMLLoadCompleteEvent event) {
        FMLLog.info(
            "[AkashicAsyncCompat] Scheduler observed=%s; %s",
            CompatState.isSchedulerObserved(),
            CompatHooks.telemetry());
    }
}