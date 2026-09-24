public final class Beta20GuardHarness {
    public static void main(String[] args) {
        com.akashic.thepatcher.module.forestry.ForestryC2RuntimeGuard.verify();
        System.out.println(
            "BETA20_GUARD active="
            + com.akashic.thepatcher.module.forestry.ForestryC2RuntimeGuard.isActive()
            + " matched="
            + com.akashic.thepatcher.module.forestry.ForestryC2RuntimeGuard.getMatchedCount()
            + " rewrite="
            + com.akashic.thepatcher.module.forestry.ForestryC2RuntimeGuard.isUniversalRewriteBundled());
    }
}