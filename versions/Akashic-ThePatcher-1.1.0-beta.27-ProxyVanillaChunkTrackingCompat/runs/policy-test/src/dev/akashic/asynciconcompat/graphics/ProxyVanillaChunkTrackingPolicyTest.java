package dev.akashic.asynciconcompat.graphics;

import com.akashic.forbiddenbackhand.core.BackhandGuardHooks;
import com.gtnewhorizons.angelica.config.AngelicaConfig;

public final class ProxyVanillaChunkTrackingPolicyTest {
    private static int checks;

    private static void expect(boolean expected, boolean actual, String label) {
        checks++;
        if (expected != actual) {
            throw new AssertionError(label + " expected=" + expected + " actual=" + actual);
        }
    }

    public static void main(String[] args) {
        AngelicaConfig.useVanillaChunkTracking = false;

        BackhandGuardHooks.setState(false, false, "DISCONNECTED");
        expect(false, ProxyVanillaChunkTrackingPolicy.useFastTracking(), "disconnected");

        BackhandGuardHooks.setState(true, false, "VANILLA");
        expect(true, ProxyVanillaChunkTrackingPolicy.useFastTracking(), "remote vanilla");

        BackhandGuardHooks.setState(true, false, "BUKKIT");
        expect(true, ProxyVanillaChunkTrackingPolicy.useFastTracking(), "remote bukkit");

        BackhandGuardHooks.setState(true, false, "MODDED");
        expect(false, ProxyVanillaChunkTrackingPolicy.useFastTracking(), "remote modded");

        BackhandGuardHooks.setState(true, false, "TRANSITION");
        expect(false, ProxyVanillaChunkTrackingPolicy.useFastTracking(), "transition");

        BackhandGuardHooks.setState(true, true, "VANILLA");
        expect(false, ProxyVanillaChunkTrackingPolicy.useFastTracking(), "local vanilla");

        AngelicaConfig.useVanillaChunkTracking = true;
        BackhandGuardHooks.setState(true, false, "MODDED");
        expect(true, ProxyVanillaChunkTrackingPolicy.useFastTracking(), "configured override");

        System.out.println("PROXY_CHUNK_TRACKING_POLICY PASS checks=" + checks);
    }
}
