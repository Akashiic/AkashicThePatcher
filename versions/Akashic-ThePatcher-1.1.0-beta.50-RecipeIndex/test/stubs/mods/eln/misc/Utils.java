package mods.eln.misc;

import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

/** Gate stub: same logic as Eln 1.24.8's Utils.areSame (bytecode read from the jar). */
public final class Utils {
    public static boolean areSame(ItemStack stack, ItemStack output) {
        if (stack == null || output == null) throw new NullPointerException();
        try {
            if (stack.func_77973_b() == output.func_77973_b() && stack.func_77960_j() == output.func_77960_j()) return true;
            int[] stackIds = OreDictionary.getOreIDs(stack);
            int[] outputIds = OreDictionary.getOreIDs(output);
            for (int i : outputIds) for (int j : stackIds) if (i == j) return true;
        } catch (Exception e) {
            return false;
        }
        return false;
    }

    private Utils() {}
}
