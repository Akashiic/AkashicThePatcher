package net.minecraft.entity.projectile;
/** Gate shape: the vanilla update moves, ray-traces and spawns smoke. Never shipped. */
public abstract class EntityFireball extends net.minecraft.entity.Entity {
    public static int smoke;
    @Override
    public void onUpdate() {
        vanillaTicks++;
        posX += motionX;
        smoke++;
    }
}
