import com.akashic.thepatcher.module.forestry.ForestryC2RuntimeGuard;

/** Runs the real guard against the JAR on the classpath and prints its verdict. */
public final class GuardHarness {
    public static void main(String[] args) {
        ForestryC2RuntimeGuard.verify();
        System.out.println("GUARD active=" + ForestryC2RuntimeGuard.isActive()
            + " matched=" + ForestryC2RuntimeGuard.getMatchedCount()
            + " rewrite=" + ForestryC2RuntimeGuard.isUniversalRewriteBundled()
            + " barrier=" + ForestryC2RuntimeGuard.isCompileBarrierActive());
    }
}
