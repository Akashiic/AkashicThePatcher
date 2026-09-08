package com.akashic.thepatcher.module.dimdoors;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;

public final class DimDoorsClientLifecycle {
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) DimDoorsClientRuntime.tick();
    }
    @SubscribeEvent public void disconnected(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        DimDoorsClientRuntime.invalidate();
    }
    @SubscribeEvent public void connected(FMLNetworkEvent.ClientConnectedToServerEvent event) {
        DimDoorsClientRuntime.invalidate();
    }
}
