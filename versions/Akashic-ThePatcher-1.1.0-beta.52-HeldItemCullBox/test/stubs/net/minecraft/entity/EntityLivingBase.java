package net.minecraft.entity;

import net.minecraft.item.ItemStack;

public class EntityLivingBase extends Entity {
    public ItemStack held;

    public EntityLivingBase(String label, double x, ItemStack held) {
        super(label, x);
        this.held = held;
    }

    /** getHeldItem */
    public ItemStack func_70694_bm() {
        return held;
    }
}
