package com.akashic.thepatcher.module.immersivepetroleum;

import com.akashic.thepatcher.util.BundleLog;

/**
 * Runtime side of ProjectorSlotTransformer (beta.46): replaces {@code VIEWS[currentItem]} in Immersive Petroleum's
 * ProjectorClient.selectedView. Only installed on the client.
 */
public final class ProjectorSlotHooks {

    private static volatile boolean logged;

    private ProjectorSlotHooks() {}

    /** views[slot], or null (no projection held) when the selected slot is not one of the array's hotbar slots. */
    public static Object view(Object[] views, int slot) {
        if (slot >= 0 && slot < views.length) {
            return views[slot];
        }
        if (!logged) {
            logged = true;
            BundleLog.warn("Immersive Petroleum projector slot guard: selected slot " + slot + " is outside the "
                + views.length + " hotbar slots (Backhand's offhand slot while it runs an offhand action); no"
                + " projection is shown for it instead of the ArrayIndexOutOfBoundsException crash (-D"
                + ProjectorSlotTransformer.DISABLE_PROPERTY + "=true restores the original lookup).");
        }
        return null;
    }
}
