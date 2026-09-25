package dev.akashic.asynciconcompat.graphics;

public final class NuminaFovSafetyTest {
    private static int checks;

    private static void expect(boolean expected, boolean actual, String label) {
        checks++;
        if (expected != actual) {
            throw new AssertionError(
                label + " expected=" + expected + " actual=" + actual);
        }
    }

    private static void expectFloat(float expected, float actual, String label) {
        checks++;
        if (Float.floatToIntBits(expected) != Float.floatToIntBits(actual)) {
            throw new AssertionError(
                label + " expected=" + expected + " actual=" + actual);
        }
    }

    private static void expectNear(double expected, double actual, String label) {
        checks++;
        if (Math.abs(expected - actual) > 1.0E-6D) {
            throw new AssertionError(
                label + " expected=" + expected + " actual=" + actual);
        }
    }

    public static void main(String[] args) {
        expect(true, NuminaFovSafety.shouldBypass(0.0D, 0.0F), "zero/zero");
        expect(true, NuminaFovSafety.shouldBypass(0.1D, 0.0F), "zero walk");
        expect(true, NuminaFovSafety.shouldBypass(Double.NaN, 0.1F), "nan movement");
        expect(true, NuminaFovSafety.shouldBypass(
            Double.POSITIVE_INFINITY, 0.1F), "infinite movement");
        expect(true, NuminaFovSafety.shouldBypass(-0.1D, 0.1F), "zero divisor");
        expect(false, NuminaFovSafety.shouldBypass(0.1D, 0.1F), "normal");

        expectNear(1.0D, NuminaFovSafety.divisor(0.1D, 0.1F), "normal divisor");
        expectFloat(0.75F, NuminaFovSafety.safeBaseline(0.75F, 1.0F),
            "preserve incoming");
        expectFloat(0.8F, NuminaFovSafety.safeBaseline(Float.NaN, 0.8F),
            "fallback base");
        expectFloat(1.0F, NuminaFovSafety.safeBaseline(Float.NaN, Float.NaN),
            "hard fallback");
        expect(false, NuminaFovSafety.isFinitePositive(0.0F), "zero not positive");
        expect(true, NuminaFovSafety.isFinitePositive(1.0F), "one positive");

        System.out.println("NUMINA_FOV_SAFETY PASS checks=" + checks);
    }
}
