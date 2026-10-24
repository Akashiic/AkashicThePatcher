package com.akashic.thepatcher.module.forestry;

import java.lang.management.ManagementFactory;
import java.util.List;

import com.akashic.thepatcher.util.BundleLog;

/**
 * Forestry Genome.getChromosomes / HotSpot 8u51 C2 crash guard (beta.37).
 *
 * The fix itself is ForestryGenomeC2Transformer. It pads Forestry's original,
 * otherwise unchanged method past HotSpot's HugeMethodLimit, so with
 * DontCompileHugeMethods=true (the product default) the method is never
 * JIT-compiled. This guard runs at coremod init, before Genome loads. It
 * reports whether that protection is armed and whether the legacy
 * CompileCommand exclusion is also present (optional since beta.36).
 */
public final class ForestryC2RuntimeGuard {

    public static final String REQUIRED_COMPILE_COMMAND =
        "-XX:CompileCommand=exclude,forestry/core/genetics/Genome.getChromosomes";

    private static final String GENOME_EXCLUSION =
        "forestry/core/genetics/Genome.getChromosomes";

    private static volatile boolean checked;
    private static volatile boolean active;
    private static volatile int matchedCount;
    private static volatile boolean universalRewriteBundled;
    private static volatile boolean compileBarrierActive;

    private ForestryC2RuntimeGuard() {}

    public static void verify() {
        if (checked) return;
        synchronized (ForestryC2RuntimeGuard.class) {
            if (checked) return;

            final List<String> arguments =
                ManagementFactory.getRuntimeMXBean().getInputArguments();
            active = containsExclusion(arguments, GENOME_EXCLUSION);
            matchedCount = active ? 1 : 0;
            universalRewriteBundled = hasTransformer();
            final boolean disabled = Boolean.getBoolean("akashic.forestryGenomeBarrier.disable");
            final String hugeFlag = readHugeMethodsFlag();
            compileBarrierActive =
                universalRewriteBundled && !disabled && "true".equals(hugeFlag);
            checked = true;

            final String javaVersion =
                System.getProperty("java.version", "unknown");

            if (compileBarrierActive) {
                BundleLog.info(
                    "[Forestry-C2] Genome compile barrier armed: Forestry's original "
                        + "Genome.getChromosomes will be padded past HugeMethodLimit ("
                        + ForestryGenomeC2Transformer.HOTSPOT_HUGE_METHOD_LIMIT
                        + ") when it loads, DontCompileHugeMethods=true, so it is never JIT-compiled; "
                        + "launcher CompileCommand not required (" + (active ? "also present" : "absent")
                        + "); java=" + javaVersion);
            } else if (active) {
                BundleLog.info(
                    "[Forestry-C2] Genome compile barrier inactive (disabled=" + disabled
                        + ", DontCompileHugeMethods=" + hugeFlag
                        + ") but CompilerOracle exclusion for Genome.getChromosomes is present; java="
                        + javaVersion);
            } else {
                BundleLog.warn(
                    "[Forestry-C2] UNPROTECTED: Genome compile barrier inactive (transformer="
                        + universalRewriteBundled + ", disabled=" + disabled
                        + ", DontCompileHugeMethods=" + hugeFlag
                        + ") and no " + REQUIRED_COMPILE_COMMAND
                        + ". Java 8u51 may crash natively in a C2 CompilerThread; java=" + javaVersion);
            }

            System.setProperty("akashic.forestryC2Guard.active", Boolean.toString(active));
            System.setProperty("akashic.forestryC2Guard.compilerOracleCount", Integer.toString(matchedCount));
            System.setProperty("akashic.forestryC2Guard.compilerOracleRequired", "0");
            System.setProperty("akashic.forestryC2Guard.universalRewrite", Boolean.toString(universalRewriteBundled));
            System.setProperty("akashic.forestryC2Guard.compileBarrier", Boolean.toString(compileBarrierActive));
        }
    }

    private static boolean containsExclusion(List<String> arguments, String required) {
        if (arguments == null) return false;
        for (String argument : arguments) {
            if (argument == null) continue;
            if (!argument.startsWith("-XX:CompileCommand=exclude,")) continue;
            final String target = argument
                .substring("-XX:CompileCommand=exclude,".length())
                .replace("::", ".");
            if (required.equals(target)) return true;
        }
        return false;
    }

    private static boolean hasTransformer() {
        try {
            return ForestryC2RuntimeGuard.class.getClassLoader().getResource(
                "com/akashic/thepatcher/module/forestry/ForestryGenomeC2Transformer.class") != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** "true"/"false", or a diagnostic string when the flag cannot be read. */
    private static String readHugeMethodsFlag() {
        try {
            final com.sun.management.HotSpotDiagnosticMXBean bean =
                ManagementFactory.getPlatformMXBean(com.sun.management.HotSpotDiagnosticMXBean.class);
            if (bean == null) return "unavailable";
            return bean.getVMOption("DontCompileHugeMethods").getValue();
        } catch (Throwable t) {
            return "unreadable(" + t.getClass().getSimpleName() + ")";
        }
    }

    public static boolean isActive() {
        return active;
    }

    public static int getMatchedCount() {
        return matchedCount;
    }

    /** Since beta.37: whether the Genome compile-barrier transformer is bundled. */
    public static boolean isUniversalRewriteBundled() {
        return universalRewriteBundled;
    }

    public static boolean isCompileBarrierActive() {
        return compileBarrierActive;
    }
}
