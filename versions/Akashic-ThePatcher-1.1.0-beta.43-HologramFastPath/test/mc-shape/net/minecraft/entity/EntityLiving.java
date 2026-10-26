package net.minecraft.entity;
/** Gate shape. Never shipped. */
public abstract class EntityLiving extends EntityLivingBase {
    public String customName = "";
    public boolean alwaysRender = true;
    public String getCustomNameTag() { return customName; }
    public boolean getAlwaysRenderNameTagForRender() { return alwaysRender; }
}
