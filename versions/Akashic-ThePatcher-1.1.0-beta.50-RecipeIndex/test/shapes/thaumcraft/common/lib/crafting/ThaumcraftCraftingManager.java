package thaumcraft.common.lib.crafting;

import java.util.ArrayList;
import java.util.List;

import akashic.gate.OtherRecipe;
import net.minecraft.item.Item;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.ShapedRecipes;
import net.minecraft.item.crafting.ShapelessRecipes;
import net.minecraftforge.oredict.ShapedOreRecipe;
import net.minecraftforge.oredict.ShapelessOreRecipe;
import thaumcraft.api.aspects.AspectList;

/**
 * Gate shape of Thaumcraft 4.2.3.5's generateTagsFromCraftingRecipes: same loop and the same tests on the recipe
 * output (read again each time, as the original does), the cheapest matching recipe wins, first one on ties. Like
 * the original, the loop index is only used for get(q); the winner is reported as the recipe object.
 */
public class ThaumcraftCraftingManager {

    public static AspectList run(Item item, int meta) {
        return generateTagsFromCraftingRecipes(item, meta, new ArrayList<List>());
    }

    private static AspectList generateTagsFromCraftingRecipes(Item item, int meta, ArrayList<List> history) {
        AspectList best = null;
        int min = Integer.MAX_VALUE;
        List recipeList = CraftingManager.func_77594_a().func_77592_b();
        for (int q = 0; q < recipeList.size(); q++) {
            IRecipe recipe = (IRecipe) recipeList.get(q);
            if (recipe == null || recipe.func_77571_b() == null || recipe.func_77571_b().func_77973_b() == null) {
                continue;
            }
            int recipeMeta = recipe.func_77571_b().func_77960_j() == 32767 ? 0 : recipe.func_77571_b().func_77960_j();
            int wanted = meta == 32767 ? 0 : meta;
            if (recipe.func_77571_b().func_77973_b() != item || recipeMeta != wanted) {
                continue;
            }
            int cost = cost(recipeList.get(q));
            if (cost < min) {
                min = cost;
                best = new AspectList(recipeList.get(q), cost);
            }
        }
        return best;
    }

    private static int cost(Object recipe) {
        if (recipe instanceof ShapedRecipes) return ((ShapedRecipes) recipe).cost;
        if (recipe instanceof ShapelessRecipes) return ((ShapelessRecipes) recipe).cost;
        if (recipe instanceof ShapedOreRecipe) return ((ShapedOreRecipe) recipe).cost;
        if (recipe instanceof ShapelessOreRecipe) return ((ShapelessOreRecipe) recipe).cost;
        return ((OtherRecipe) recipe).cost;
    }
}
