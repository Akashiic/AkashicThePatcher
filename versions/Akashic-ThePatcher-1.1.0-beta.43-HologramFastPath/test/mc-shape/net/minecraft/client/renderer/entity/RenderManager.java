package net.minecraft.client.renderer.entity;
/** Gate shape. Never shipped. */
public class RenderManager {
    public static RenderManager instance = new RenderManager();
    public static double renderPosX, renderPosY, renderPosZ;
    public net.minecraft.entity.EntityLivingBase livingPlayer;
    public float playerViewY, playerViewX;
    public int drawn;
    public net.minecraft.client.gui.FontRenderer getFontRenderer() { return new net.minecraft.client.gui.FontRenderer(); }

    public boolean doRenderEntity(net.minecraft.entity.Entity entity, double x, double y, double z, float yaw, float partialTicks, boolean hideBox) {
        drawn++;
        return true;
    }
}
