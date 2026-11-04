package com.gtnewhorizons.angelica.rendering.celeritas;

import net.minecraft.entity.Entity;
import net.minecraft.util.AxisAlignedBB;

import com.gtnewhorizons.angelica.rendering.TileEntityRenderBoundsRegistry;

/** Gate shape of Angelica 2.2.37-2.2.43's isEntityVisible; the sections "in view" cover x in [minX, maxX]. */
public class CeleritasWorldRenderer {
    public boolean useEntityCulling = true;
    public double minX;
    public double maxX;

    public boolean isEntityVisible(Entity entity) {
        if (!useEntityCulling) {
            return true;
        }
        AxisAlignedBB box = entity.func_70046_E();
        if (box == null) {
            box = entity.boundingBox;
        }
        if (TileEntityRenderBoundsRegistry.isInfiniteExtentsBox(box)) {
            return true;
        }
        return isBoxVisible(box.field_72340_a, box.field_72338_b, box.field_72339_c, box.field_72336_d,
            box.field_72337_e, box.field_72334_f);
    }

    public boolean isBoxVisible(double x1, double y1, double z1, double x2, double y2, double z2) {
        return x2 >= minX && x1 <= maxX;
    }
}
