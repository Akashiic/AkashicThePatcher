package net.minecraft.entity;
import net.minecraft.world.World;
public abstract class Entity {
    public World worldObj;
    public Entity riddenByEntity, ridingEntity;
    public boolean isDead;
    public int ticksExisted;
    public double posX, posY, posZ, prevPosX, prevPosY, prevPosZ, lastTickPosX, lastTickPosY, lastTickPosZ;
    public double motionX, motionY, motionZ;
    public float rotationYaw, rotationPitch, prevRotationYaw, prevRotationPitch;
    public boolean isInvisibleToPlayer(net.minecraft.entity.player.EntityPlayer player) { return false; }
}
