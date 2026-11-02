package mods.eln.craft;

import java.util.ArrayList;
import java.util.List;

import akashic.gate.ElnItems;
import mods.eln.misc.Utils;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;

/**
 * Gate shape of Eln 1.24.8's CraftingRecipes: checkRecipe() asks recipeExists() for each item and records the
 * missing ones (the original logs "No recipe for ..."); recipeExists() is the original loop.
 */
public final class CraftingRecipes {
    public final List<String> missing = new ArrayList<String>();

    public List<String> run() {
        missing.clear();
        checkRecipe();
        return missing;
    }

    private final void checkRecipe() {
        for (int i = 0; i < ElnItems.ITEMS.size(); i++) {
            ItemStack stack = ElnItems.ITEMS.get(i);
            if (!recipeExists(stack)) {
                missing.add("#" + i);
            }
        }
    }

    private final boolean recipeExists(ItemStack stack) {
        if (stack == null) return false;
        List list = CraftingManager.func_77594_a().func_77592_b();
        for (Object o : list) {
            if (o instanceof IRecipe && ((IRecipe) o).func_77571_b() != null) {
                ItemStack output = ((IRecipe) o).func_77571_b();
                if (output == null) throw new NullPointerException("getRecipeOutput(...)");
                if (Utils.areSame(stack, output)) return true;
            }
        }
        return false;
    }
}
