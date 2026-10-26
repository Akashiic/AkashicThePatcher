package net.minecraft.client.renderer.culling;
public interface ICamera {
    boolean isBoundingBoxInFrustum(net.minecraft.util.AxisAlignedBB box);
}
