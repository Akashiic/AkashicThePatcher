package net.minecraft.item.crafting;

import java.util.ArrayList;
import java.util.List;

/** Compile/gate stub. */
public class CraftingManager {
    private static final CraftingManager INSTANCE = new CraftingManager();
    public List<Object> recipes = new ArrayList<Object>();

    /** getInstance */
    public static CraftingManager func_77594_a() {
        return INSTANCE;
    }

    /** getRecipeList */
    public List<Object> func_77592_b() {
        return recipes;
    }
}
