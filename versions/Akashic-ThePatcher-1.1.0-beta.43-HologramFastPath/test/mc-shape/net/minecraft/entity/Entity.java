package net.minecraft.entity;

import net.minecraft.world.World;

/** Gate shape of the vanilla class (MCP names, the members HologramFastPath uses + the hooked method). Never shipped. */
public abstract class Entity {
    public World worldObj;
    public Entity riddenByEntity, ridingEntity;
    public boolean isDead;
    public int ticksExisted;
    public double posX, posY, posZ, prevPosX, prevPosY, prevPosZ, lastTickPosX, lastTickPosY, lastTickPosZ;
    public double motionX, motionY, motionZ;
    public float rotationYaw, rotationPitch, prevRotationYaw, prevRotationPitch;
    /** Gate only: counts runs of the vanilla update body. */
    public int vanillaTicks;

    public boolean isInvisibleToPlayer(net.minecraft.entity.player.EntityPlayer player) { return false; }

    public boolean isInRangeToRender3d(double x, double y, double z) {
        final double dx = posX - x, dy = posY - y, dz = posZ - z;
        return dx * dx + dy * dy + dz * dz < 4096.0 * 4096.0;
    }

    public void onUpdate() {
        vanillaTicks++;
        posX += motionX;
        posY += motionY;
        posZ += motionZ;
    }
}
