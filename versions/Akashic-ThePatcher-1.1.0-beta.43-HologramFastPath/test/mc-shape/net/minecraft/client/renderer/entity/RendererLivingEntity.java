package net.minecraft.client.renderer.entity;
/** Gate shape. Never shipped. */
public class RendererLivingEntity {
    public int nameTags;
    protected void passSpecialRender(net.minecraft.entity.EntityLivingBase entity, double x, double y, double z) {
        nameTags++;
    }
    public void callPassSpecialRender(net.minecraft.entity.EntityLivingBase entity) {
        passSpecialRender(entity, 0, 0, 0);
    }
}
