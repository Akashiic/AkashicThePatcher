package net.minecraft.client.renderer.entity;
public class RenderManager {
    public static RenderManager instance;
    public static double renderPosX, renderPosY, renderPosZ;
    public net.minecraft.entity.EntityLivingBase livingPlayer;
    public float playerViewY, playerViewX;
    public net.minecraft.client.gui.FontRenderer getFontRenderer() { return null; }
}
