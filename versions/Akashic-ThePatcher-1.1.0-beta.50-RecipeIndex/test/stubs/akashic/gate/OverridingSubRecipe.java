package akashic.gate;

import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.ShapedRecipes;

/** Gate stub: a subclass that overrides getRecipeOutput with a changing output (not trusted). */
public class OverridingSubRecipe extends ShapedRecipes {
    public final ItemStack[] outputs;
    private int calls;

    public OverridingSubRecipe(int cost, ItemStack... outputs) {
        super(outputs[0], cost);
        this.outputs = outputs;
    }

    @Override
    public ItemStack func_77571_b() {
        return outputs[(calls++ / 5) % outputs.length];
    }
}
