package net.minecraft.item;

/** Compile/gate stub with the SRG names the runtime uses. */
public class ItemStack {
    public Item item;
    public int damage;

    public ItemStack(Item item, int size, int damage) {
        this.item = item;
        this.damage = damage;
    }

    /** getItem */
    public Item func_77973_b() {
        return item;
    }

    /** getItemDamage */
    public int func_77960_j() {
        return item.getDamage(this);
    }
}
