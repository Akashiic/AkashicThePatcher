package net.minecraft.item;

/** Test double: an item name and a count. */
public final class ItemStack {
    public final String item;
    public int size;

    public ItemStack(String item, int size) {
        this.item = item;
        this.size = size;
    }

    public static ItemStack func_77944_b(ItemStack s) {
        return s == null ? null : new ItemStack(s.item, s.size);
    }

    public static boolean same(ItemStack a, ItemStack b) {
        return a == null ? b == null : b != null && a.item.equals(b.item) && a.size == b.size;
    }

    @Override
    public String toString() {
        return size + "x" + item;
    }
}
