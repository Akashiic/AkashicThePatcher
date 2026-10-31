package akashic.gate;

import baubles.api.BaubleType;
import baubles.api.expanded.IBaubleExpanded;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;

/** Test shape: an IBaubleExpanded item that overrides onSlotContentsChanged and counts the calls. Never shipped. */
public final class TrackingBauble implements IBaubleExpanded {
    public static int changes;

    public String[] getBaubleTypes(ItemStack stack) { return new String[] {"ring"}; }
    public BaubleType getBaubleType(ItemStack stack) { return null; }
    public void onWornTick(ItemStack stack, EntityLivingBase player) {}
    public void onEquipped(ItemStack stack, EntityLivingBase player) {}
    public void onUnequipped(ItemStack stack, EntityLivingBase player) {}
    public boolean canEquip(ItemStack stack, EntityLivingBase player) { return true; }
    public boolean canUnequip(ItemStack stack, EntityLivingBase player) { return true; }

    @Override
    public void onSlotContentsChanged(ItemStack stack, EntityLivingBase player) {
        changes++;
    }
}
