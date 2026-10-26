package net.minecraft.entity.item;
/** Gate shape. Never shipped. */
public class EntityItem extends net.minecraft.entity.Entity {
    public int age;
    @Override
    public void onUpdate() {
        vanillaTicks++;
        age++;
        motionY -= 0.04;
        posY += motionY;
    }
}
