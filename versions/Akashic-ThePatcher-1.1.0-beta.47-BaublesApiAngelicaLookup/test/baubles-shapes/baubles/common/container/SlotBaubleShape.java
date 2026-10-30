package baubles.common.container;

import baubles.api.expanded.IBaubleExpanded;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;

/**
 * Test shape of Baubles Expanded 2.2.26's SlotBauble.onSlotChanged: compiled against the 2.2.26 API, so it calls
 * IBaubleExpanded.onSlotContentsChanged with INVOKEINTERFACE, like the real slot. Never shipped.
 */
public final class SlotBaubleShape {
    private SlotBaubleShape() {}

    public static void onSlotChanged(Object item, ItemStack stack, EntityLivingBase player) {
        if (item instanceof IBaubleExpanded) {
            ((IBaubleExpanded) item).onSlotContentsChanged(stack, player);
        }
    }
}
