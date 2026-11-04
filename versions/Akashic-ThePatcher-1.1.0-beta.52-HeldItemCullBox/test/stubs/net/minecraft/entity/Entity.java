package net.minecraft.entity;

import net.minecraft.util.AxisAlignedBB;

/** Gate stub: the members the shapes and the hooks use (MCP field name for the box, as the shapes read it). */
public class Entity {
    public AxisAlignedBB boundingBox;
    public boolean ignoreFrustumCheck;
    public final String label;

    public Entity(String label, double x) {
        this.label = label;
        this.boundingBox = AxisAlignedBB.func_72330_a(x - 0.3, 0, -0.3, x + 0.3, 1.8, 0.3);
    }

    /** getBoundingBox (collision box for boats/minecarts): null for every gate entity. */
    public AxisAlignedBB func_70046_E() {
        return null;
    }

    @Override
    public String toString() {
        return label;
    }
}
