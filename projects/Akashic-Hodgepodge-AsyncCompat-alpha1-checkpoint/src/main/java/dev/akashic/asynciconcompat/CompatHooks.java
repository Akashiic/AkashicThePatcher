package dev.akashic.asynciconcompat;

import java.util.List;
import cpw.mods.fml.common.eventhandler.EventBus;

public final class CompatHooks {
    private CompatHooks() {}

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static boolean addChiselSubmapSynchronized(List list, Object value) {
        synchronized (list) {
            return list.add(value);
        }
    }

    public static void registerForgeEventHandlerSynchronized(EventBus bus, Object value) {
        synchronized (bus) {
            bus.register(value);
        }
    }
}