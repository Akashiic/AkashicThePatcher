package Reika.DragonAPI.Instantiable.Event.Client;

import net.minecraft.block.Block;

/**
 * Shape of DragonAPI V33b's RenderBlockAtPosEvent hooks (javap of the class in TEOF 5.6): getMaxRenderPass reads
 * block.getRenderBlockPass() (func_149701_w) into a local first, then keeps the highest pass of the listeners;
 * checkCanRenderPass asks the listeners, then block.canRenderInPass. Never shipped.
 */
public final class RenderBlockAtPosEvent {

    public interface AdvancedBlockRenderWatcher {
        boolean tryRenderInPass(Block block, int x, int y, int z, int pass);

        int getMaxRenderPass(Block block, int x, int y, int z);
    }

    public static AdvancedBlockRenderWatcher[] advancedListeners;

    private RenderBlockAtPosEvent() {}

    public static boolean checkCanRenderPass(Block block, int pass, int x, int y, int z) {
        if (advancedListeners != null) {
            for (AdvancedBlockRenderWatcher w : advancedListeners) {
                if (w.tryRenderInPass(block, x, y, z, pass)) {
                    return true;
                }
            }
        }
        return block.canRenderInPass(pass);
    }

    public static int getMaxRenderPass(Block block, int x, int y, int z) {
        int pass = block.func_149701_w();
        if (advancedListeners != null) {
            for (AdvancedBlockRenderWatcher w : advancedListeners) {
                pass = Math.max(pass, w.getMaxRenderPass(block, x, y, z));
            }
        }
        return pass;
    }
}
