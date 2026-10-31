package akashic.gate;

import baubles.api.BaubleType;
import baubles.api.expanded.IBaubleExpanded;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;

/**
 * Test shape of Et Futurum Requiem's ItemArmorElytra as a bauble: implements IBaubleExpanded and does not override
 * onSlotContentsChanged (checked on the real 2.6.64 jar). Never shipped.
 */
public final class ElytraShape implements IBaubleExpanded {
    public String[] getBaubleTypes(ItemStack stack) { return new String[] {"wings"}; }
    public BaubleType getBaubleType(ItemStack stack) { return null; }
    public void onWornTick(ItemStack stack, EntityLivingBase player) {}
    public void onEquipped(ItemStack stack, EntityLivingBase player) {}
    public void onUnequipped(ItemStack stack, EntityLivingBase player) {}
    public boolean canEquip(ItemStack stack, EntityLivingBase player) { return true; }
    public boolean canUnequip(ItemStack stack, EntityLivingBase player) { return true; }
}
