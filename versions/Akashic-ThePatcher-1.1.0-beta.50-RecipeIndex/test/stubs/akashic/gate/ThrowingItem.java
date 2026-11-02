package akashic.gate;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/** Gate stub: getDamage throws for damage 13 (exercises the Eln fallback). */
public class ThrowingItem extends Item {
    @Override
    public int getDamage(ItemStack stack) {
        if (stack.damage == 13) throw new IllegalStateException("damage 13");
        return stack.damage;
    }
}
