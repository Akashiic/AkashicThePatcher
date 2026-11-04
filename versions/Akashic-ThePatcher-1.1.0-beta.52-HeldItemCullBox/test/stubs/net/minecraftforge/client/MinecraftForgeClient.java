package net.minecraftforge.client;

import java.util.IdentityHashMap;
import java.util.Map;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/** Gate stub: Forge's registry and its getItemRenderer rule (the renderer must handle the render type). */
public final class MinecraftForgeClient {
    private static final Map<Item, IItemRenderer> RENDERERS = new IdentityHashMap<Item, IItemRenderer>();

    public static void registerItemRenderer(Item item, IItemRenderer renderer) {
        RENDERERS.put(item, renderer);
    }

    public static IItemRenderer getItemRenderer(ItemStack item, IItemRenderer.ItemRenderType type) {
        final IItemRenderer renderer = RENDERERS.get(item.func_77973_b());
        return renderer != null && renderer.handleRenderType(item, type) ? renderer : null;
    }
}
