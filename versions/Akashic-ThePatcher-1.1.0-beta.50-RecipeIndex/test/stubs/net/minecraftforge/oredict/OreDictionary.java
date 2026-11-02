package net.minecraftforge.oredict;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/** Compile/gate stub: ore ids per item (any damage). */
public class OreDictionary {
    public static final Map<Item, int[]> IDS = new HashMap<Item, int[]>();

    public static int[] getOreIDs(ItemStack stack) {
        if (stack == null || stack.func_77973_b() == null) {
            throw new IllegalArgumentException("Stack can not be invalid!");
        }
        int[] ids = IDS.get(stack.func_77973_b());
        return ids == null ? new int[0] : ids.clone();
    }
}
