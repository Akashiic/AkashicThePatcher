package dev.akashic.asynciconcompat;

public final class CompatState {
    private static volatile boolean chiselPatched;
    private static volatile boolean mekanismPatched;

    private CompatState() {}

    public static void markChiselPatched() {
        chiselPatched = true;
    }

    public static void markMekanismPatched() {
        mekanismPatched = true;
    }

    public static boolean isChiselPatched() {
        return chiselPatched;
    }

    public static boolean isMekanismPatched() {
        return mekanismPatched;
    }
}