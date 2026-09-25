package dev.akashic.asynciconcompat.graphics;

/**
 * Pure-math safety policy for Numina's legacy FOV speed compensation.
 *
 * <p>Vanilla 1.7.10 explicitly falls back when its FOV multiplier becomes
 * NaN/Infinity or walk speed is zero. Numina's FOV fix re-divides the event
 * value afterwards, so the same invalid inputs must be guarded again.
 */
public final class NuminaFovSafety {
    private static final double MIN_ABS_DIVISOR = 1.0E-6D;

    private NuminaFovSafety() {}

    public static boolean isFinite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }

    public static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    public static boolean isFinitePositive(float value) {
        return isFinite(value) && value > 0.0F;
    }

    public static float safeBaseline(float incoming, float base) {
        if (isFinitePositive(incoming)) {
            return incoming;
        }
        if (isFinitePositive(base)) {
            return base;
        }
        return 1.0F;
    }

    public static double divisor(double movementSpeed, float walkSpeed) {
        return ((movementSpeed / (double) walkSpeed) + 1.0D) / 2.0D;
    }

    public static boolean shouldBypass(double movementSpeed, float walkSpeed) {
        if (!isFinite(movementSpeed) || !isFinite(walkSpeed) || walkSpeed == 0.0F) {
            return true;
        }

        double divisor = divisor(movementSpeed, walkSpeed);
        return !isFinite(divisor) || Math.abs(divisor) < MIN_ABS_DIVISOR;
    }
}
