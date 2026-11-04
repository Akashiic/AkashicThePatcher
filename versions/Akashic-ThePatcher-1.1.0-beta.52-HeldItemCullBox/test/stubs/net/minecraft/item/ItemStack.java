package net.minecraft.item;

public final class ItemStack {
    private final Item item;

    public ItemStack(Item item) {
        this.item = item;
    }

    /** getItem */
    public Item func_77973_b() {
        return item;
    }
}
