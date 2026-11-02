package net.minecraft.item.crafting;

import net.minecraft.item.ItemStack;

/** Gate stub: one of the four plain recipe classes the Thaumcraft index trusts (output is a plain field). */
public class ShapedRecipes implements net.minecraft.item.crafting.IRecipe {
    public final ItemStack output;
    public final int cost;

    public ShapedRecipes(ItemStack output, int cost) {
        this.output = output;
        this.cost = cost;
    }

    public ItemStack func_77571_b() {
        return output;
    }
}
