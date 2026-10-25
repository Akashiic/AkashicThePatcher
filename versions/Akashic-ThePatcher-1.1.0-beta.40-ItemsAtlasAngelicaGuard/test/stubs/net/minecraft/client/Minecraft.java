package net.minecraft.client;

import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.settings.GameSettings;

/** SRG-named stub: getMinecraft, theWorld, gameSettings. */
public class Minecraft {
    private static final Minecraft INSTANCE = new Minecraft();
    public WorldClient field_71441_e;
    public GameSettings field_71474_y = new GameSettings();

    public static Minecraft func_71410_x() { return INSTANCE; }
}
