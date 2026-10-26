package net.minecraft.entity.passive;
/** Gate shape (no akashic$hologram: HologramFastPathTransformer adds it). Never shipped. */
public class EntityHorse extends net.minecraft.entity.EntityAgeable {
    @Override
    public void onUpdate() {
        vanillaTicks++;
        motionY -= 0.08;
        posY += motionY;
    }
}
