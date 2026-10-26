package com.gtnewhorizons.angelica.rendering;

/**
 * Test stub of Angelica's RenderRecovery: the real one needs a running Minecraft. Counts the calls;
 * -Dstub.recovery.throw=true makes it fail. Put before the Angelica jar on the class path. Never shipped.
 */
public final class RenderRecovery {
    public static int calls;

    private RenderRecovery() {}

    public static void resetAfterCrash() {
        calls++;
        if (Boolean.getBoolean("stub.recovery.throw")) {
            throw new IllegalStateException("stub RenderRecovery failure");
        }
    }
}
