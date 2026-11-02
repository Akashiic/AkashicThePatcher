package akashic.gate;

import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.ShapedOreRecipe;

/** Gate stub: a mod subclass that keeps ShapedOreRecipe's getRecipeOutput (trusted by the index). */
public class PlainSubRecipe extends ShapedOreRecipe {
    public PlainSubRecipe(ItemStack output, int cost) {
        super(output, cost);
    }
}
