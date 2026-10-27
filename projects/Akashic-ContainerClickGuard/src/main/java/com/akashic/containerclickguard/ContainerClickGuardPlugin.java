package com.akashic.containerclickguard;

import cpw.mods.fml.relauncher.FMLLaunchHandler;
import cpw.mods.fml.relauncher.IFMLLoadingPlugin;
import java.util.Map;

/**
 * Server-side guard for mod packets that move items in the player's open container without going through
 * NetHandlerPlayServer.processClickWindow - the only place where Crucible (Bukkit) fires InventoryClickEvent.
 * A plugin GUI (menu) cancels that event to keep its items in place; a click that never reaches it cannot be
 * cancelled, so the player takes the item.
 *
 * <ul>
 * <li>InventoryTweaks' ITPacketClick (sent by every InvTweaks client when the server also has InvTweaks: sorting
 * and the Ctrl / Ctrl+Shift / Space / Alt / W / S / 1-9 click shortcuts) now goes through processClickWindow.</li>
 * <li>Avaritiaddons' InfinityChestClick is only accepted while an Infinity Chest is open, and its two
 * server-to-client sync messages are dropped when a client sends them to the server (they set any item).</li>
 * </ul>
 *
 * Only the dedicated server is patched; on a client the plugin does nothing.
 */
@IFMLLoadingPlugin.Name("AkashicContainerClickGuard")
@IFMLLoadingPlugin.MCVersion("1.7.10")
@IFMLLoadingPlugin.SortingIndex(1001)
@IFMLLoadingPlugin.TransformerExclusions("com.akashic.containerclickguard")
public final class ContainerClickGuardPlugin implements IFMLLoadingPlugin {
    @Override
    public String[] getASMTransformerClass() {
        if (Boolean.getBoolean("akashic.containerClickGuard.disable")) {
            Log.info("disabled by -Dakashic.containerClickGuard.disable=true");
            return new String[0];
        }
        if (FMLLaunchHandler.side().isClient()) {
            Log.info("client side; nothing to do (this guard is for the dedicated server)");
            return new String[0];
        }
        Log.info("armed (InventoryTweaks click routing, Avaritiaddons Infinity Chest packets)");
        return new String[] {ClickGuardTransformer.class.getName()};
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
