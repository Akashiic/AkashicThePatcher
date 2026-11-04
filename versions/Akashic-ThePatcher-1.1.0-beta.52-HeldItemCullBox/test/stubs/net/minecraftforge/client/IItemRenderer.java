package net.minecraftforge.client;

import net.minecraft.item.ItemStack;

public interface IItemRenderer {
    enum ItemRenderType { ENTITY, EQUIPPED, EQUIPPED_FIRST_PERSON, INVENTORY, FIRST_PERSON_MAP }

    boolean handleRenderType(ItemStack item, ItemRenderType type);
}
