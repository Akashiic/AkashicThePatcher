package com.akashic.thepatcher.module.helditemcull;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.client.IItemRenderer;
import net.minecraftforge.client.MinecraftForgeClient;

import com.akashic.thepatcher.util.BundleLog;

/**
 * Runtime side of HeldItemCullTransformer (beta.52, client only): the box the entity culling tests use.
 *
 * This package is in ThePatcher's TransformerExclusions, so FML does not remap it: Minecraft members are written with
 * their SRG names (func_70694_bm = EntityLivingBase.getHeldItem, func_72330_a = AxisAlignedBB.getBoundingBox,
 * field_72340_a..field_72334_f = minX, minY, minZ, maxX, maxY, maxZ), as in a running client.
 */
public final class HeldItemCullHooks {

    static final String MARGIN_PROPERTY = "akashic.heldItemCull.margin";
    /** Blocks added on every side of the box of an entity holding an item with a custom renderer. */
    static final double MARGIN = margin();

    /** Boxes grown so far (diagnostics). */
    public static volatile long grown;
    private static volatile boolean failed;
    private static volatile boolean firstLogged;

    private HeldItemCullHooks() {}

    /**
     * The box to cull {@code entity} with: {@code box} itself, or a copy grown by {@link #MARGIN} when the entity is
     * living and holds an item whose IItemRenderer draws it in the hand (EQUIPPED). Never changes {@code box}.
     */
    public static AxisAlignedBB cullBox(Entity entity, AxisAlignedBB box) {
        if (box == null || failed || MARGIN <= 0 || !(entity instanceof EntityLivingBase)) {
            return box;
        }
        try {
            final ItemStack held = ((EntityLivingBase) entity).func_70694_bm();
            if (held == null || MinecraftForgeClient.getItemRenderer(held, IItemRenderer.ItemRenderType.EQUIPPED) == null) {
                return box;
            }
            grown++;
            if (!firstLogged) {
                firstLogged = true;
                BundleLog.info("Held item cull box: first grown box (" + entity.getClass().getName() + " holding "
                    + (held.func_77973_b() == null ? "?" : held.func_77973_b().getClass().getName()) + ", +" + MARGIN + " blocks).");
            }
            final double m = MARGIN;
            return AxisAlignedBB.func_72330_a(box.field_72340_a - m, box.field_72338_b - m, box.field_72339_c - m,
                box.field_72336_d + m, box.field_72337_e + m, box.field_72334_f + m);
        } catch (RuntimeException | LinkageError error) {
            failed = true;
            BundleLog.error("Held item cull box turned off for this session; entities keep their own box.", error);
            return box;
        }
    }

    private static double margin() {
        final String value = System.getProperty(MARGIN_PROPERTY);
        if (value == null) {
            return 3.0;
        }
        try {
            final double parsed = Double.parseDouble(value.trim());
            return parsed > 0 && parsed <= 16 ? parsed : 0;
        } catch (NumberFormatException bad) {
            return 3.0;
        }
    }
}
