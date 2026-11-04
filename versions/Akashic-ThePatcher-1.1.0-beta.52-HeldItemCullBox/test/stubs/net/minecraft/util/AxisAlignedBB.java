package net.minecraft.util;

/** Gate stub with the SRG names of a running client. */
public final class AxisAlignedBB {
    public double field_72340_a, field_72338_b, field_72339_c, field_72336_d, field_72337_e, field_72334_f;

    private AxisAlignedBB(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        field_72340_a = minX;
        field_72338_b = minY;
        field_72339_c = minZ;
        field_72336_d = maxX;
        field_72337_e = maxY;
        field_72334_f = maxZ;
    }

    /** getBoundingBox */
    public static AxisAlignedBB func_72330_a(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        return new AxisAlignedBB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    @Override
    public String toString() {
        return "box[" + field_72340_a + "," + field_72338_b + "," + field_72339_c + " -> " + field_72336_d + ","
            + field_72337_e + "," + field_72334_f + "]";
    }
}
