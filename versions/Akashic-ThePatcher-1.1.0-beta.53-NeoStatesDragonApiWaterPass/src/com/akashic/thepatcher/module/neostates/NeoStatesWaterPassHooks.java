package com.akashic.thepatcher.module.neostates;

import net.minecraft.block.Block;

import com.akashic.neostates.api.NeoStates;
import com.akashic.thepatcher.util.BundleLog;

/**
 * Runtime side of beta.53, called from DragonAPI's RenderBlockAtPosEvent.getMaxRenderPass with the value of its
 * block.getRenderBlockPass() call. Same rule as Neo States' MixinWorldRenderer_WaterPass on the vanilla call: a block
 * that may hold water asks for the translucent pass. Calls Neo States' public API; only installed when Neo States is
 * present (a missing method switches it off for the session and keeps the pass as it was).
 */
public final class NeoStatesWaterPassHooks {

    private static volatile boolean broken;
    private static volatile boolean announced;

    private NeoStatesWaterPassHooks() {}

    public static int waterPass(int pass, Block block) {
        if (pass >= 1 || broken || block == null) {
            return pass;
        }
        try {
            if (!announced) {
                announced = true;
                BundleLog.info("NeoStates x DragonAPI water pass: first chunk section built through DragonAPI's hook.");
            }
            return NeoStates.canHoldWater(block) ? 1 : pass;
        } catch (LinkageError error) {
            broken = true;
            BundleLog.error("NeoStates x DragonAPI water pass switched off for this session (Neo States API missing).",
                error);
            return pass;
        }
    }
}
