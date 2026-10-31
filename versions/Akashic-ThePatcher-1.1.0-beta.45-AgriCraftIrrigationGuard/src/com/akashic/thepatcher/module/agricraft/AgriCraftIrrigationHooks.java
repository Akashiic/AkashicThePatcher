package com.akashic.thepatcher.module.agricraft;

import com.akashic.thepatcher.util.BundleLog;

/**
 * Runtime side of AgriCraftIrrigationTransformer (beta.45): called from AgriCraft's
 * ConfigurationHandler with the value read for "Disable Irrigation", right before it is
 * stored. Only installed on the client.
 */
public final class AgriCraftIrrigationHooks {

    private static volatile boolean logged;

    private AgriCraftIrrigationHooks() {}

    /** The value to store in ConfigurationHandler.disableIrrigation: always false (irrigation on). */
    public static boolean clientIrrigationDisabled(boolean configured) {
        if (configured && !logged) {
            logged = true;
            BundleLog.warn("AgriCraft irrigation guard: config/agricraft/Configuration.cfg has"
                + " \"Disable Irrigation\"=true; ignored on the client, so the irrigation blocks (water tank,"
                + " channels, valve, sprinkler) stay registered and joining a server that has them no longer"
                + " fails with \"Fatally missing blocks and items\" (-D"
                + AgriCraftIrrigationTransformer.DISABLE_PROPERTY + "=true keeps the config value).");
        }
        return false;
    }
}
