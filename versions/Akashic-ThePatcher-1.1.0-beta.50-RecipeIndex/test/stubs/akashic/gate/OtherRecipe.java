package akashic.gate;

import net.minecraft.item.ItemStack;

/** Gate stub: a recipe class the index does not trust; its output may change between calls. */
public class OtherRecipe implements net.minecraft.item.crafting.IRecipe {
    public final ItemStack[] outputs;
    public final int cost;
    private int calls;

    public OtherRecipe(int cost, ItemStack... outputs) {
        this.outputs = outputs;
        this.cost = cost;
    }

    public ItemStack func_77571_b() {
        return outputs[(calls++ / 7) % outputs.length];
    }
}
