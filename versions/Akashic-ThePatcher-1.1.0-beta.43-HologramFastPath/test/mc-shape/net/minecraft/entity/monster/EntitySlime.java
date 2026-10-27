package net.minecraft.entity.monster;
/** Gate shape. Never shipped. */
public class EntitySlime extends net.minecraft.entity.EntityLiving {
    @Override
    public void onUpdate() {
        vanillaTicks++;
        posY -= 0.08;
    }
}
