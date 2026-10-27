package net.minecraft.client;
/** Gate shape: no world loaded, so renderLines returns before any GL call. Never shipped. */
public class Minecraft {
    private static final Minecraft INSTANCE = new Minecraft();
    public net.minecraft.client.multiplayer.WorldClient theWorld;
    public net.minecraft.client.entity.EntityClientPlayerMP thePlayer;
    public static Minecraft getMinecraft() { return INSTANCE; }
    public static boolean isGuiEnabled() { return true; }
}
