package com.InfinityRaider.AgriCraft.handler;

/**
 * Test shape of AgriCraft 1.5.x's ConfigurationHandler: the parts the irrigation guard touches. The real
 * loadConfiguration() reads Forge's Configuration; here -Dshape.disableIrrigation stands for the config file.
 * Never shipped.
 */
public class ConfigurationHandler {
    public static boolean disableSeedWarehouse;
    public static boolean disableIrrigation;
    public static int channelCapacity;

    public static void init() {
        loadConfiguration();
    }

    /** As onConfigurationChangedEvent: reload. */
    public static void reload() {
        loadConfiguration();
    }

    private static void loadConfiguration() {
        disableSeedWarehouse = read("shape.disableSeedWarehouse");
        disableIrrigation = read("shape.disableIrrigation");
        channelCapacity = 500;
    }

    private static boolean read(String key) {
        return Boolean.getBoolean(key);
    }
}
