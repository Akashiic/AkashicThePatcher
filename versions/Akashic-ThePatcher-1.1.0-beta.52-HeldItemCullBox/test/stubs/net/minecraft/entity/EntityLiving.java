package net.minecraft.entity;

import net.minecraft.item.ItemStack;

public class EntityLiving extends EntityLivingBase {
    public Entity leashHolder;

    public EntityLiving(String label, double x, ItemStack held) {
        super(label, x, held);
    }
}
