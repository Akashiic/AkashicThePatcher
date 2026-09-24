import com.akashic.thepatcher.module.forestry.ForestryC2RuntimeGuard;

public final class GuardHarness {
    public static void main(String[] args) {
        ForestryC2RuntimeGuard.verify();
        System.out.println(
            "active=" + ForestryC2RuntimeGuard.isActive()
                + " compiler="
                + ForestryC2RuntimeGuard.isCompilerOracleActive()
                + " shim="
                + ForestryC2RuntimeGuard.isUniversalShimBundled());
    }
}