package net.minecraft.client.renderer.culling;

import net.minecraft.util.AxisAlignedBB;

public interface ICamera {
    boolean isBoundingBoxInFrustum(AxisAlignedBB box);
}
