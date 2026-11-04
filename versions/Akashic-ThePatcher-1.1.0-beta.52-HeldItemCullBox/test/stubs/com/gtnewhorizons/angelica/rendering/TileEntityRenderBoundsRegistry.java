package com.gtnewhorizons.angelica.rendering;

import net.minecraft.util.AxisAlignedBB;

public final class TileEntityRenderBoundsRegistry {
    public static boolean isInfiniteExtentsBox(AxisAlignedBB box) {
        return box.field_72340_a == Double.NEGATIVE_INFINITY;
    }
}
