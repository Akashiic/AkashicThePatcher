package com.akashic.baublesexpandedapifix;

import cpw.mods.fml.relauncher.IFMLLoadingPlugin;
import java.util.Map;

/**
 * Stops the server tick loop from dying with
 * {@code NoSuchMethodError: IBaubleExpanded.onSlotContentsChanged(ItemStack, EntityLivingBase)} when a player puts
 * an Et Futurum elytra (or any other IBaubleExpanded item) into a Baubles Expanded slot.
 *
 * BaublesExpanded 2.2.26-GTNH declares onSlotContentsChanged as an empty default method and SlotBauble.onSlotChanged
 * calls it. Another jar in the pack ships an older copy of baubles.api.expanded.IBaubleExpanded without that
 * method, and that copy is the one the class loader picks. {@link InterfaceRepairTransformer} adds the missing
 * empty default to whichever copy gets loaded; the correct copy passes through byte-for-byte unchanged.
 *
 * No mod container, so it is invisible to the FML handshake: server-only installs need nothing on the client.
 */
@IFMLLoadingPlugin.Name("AkashicBaublesExpandedApiFix")
@IFMLLoadingPlugin.MCVersion("1.7.10")
@IFMLLoadingPlugin.SortingIndex(1002)
@IFMLLoadingPlugin.TransformerExclusions("com.akashic.baublesexpandedapifix")
public final class BaublesExpandedApiFixPlugin implements IFMLLoadingPlugin {

    @Override
    public String[] getASMTransformerClass() {
        if (Boolean.getBoolean("akashic.baublesExpandedApiFix.disable")) {
            Log.info("disabled by -Dakashic.baublesExpandedApiFix.disable=true");
            return new String[0];
        }
        return new String[] {InterfaceRepairTransformer.class.getName()};
    }

    @Override
    public String getModContainerClass() {
        return null;
    }

    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {
    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }
}
