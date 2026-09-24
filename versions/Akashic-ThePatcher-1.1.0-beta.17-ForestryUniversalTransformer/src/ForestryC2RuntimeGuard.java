package com.akashic.thepatcher.module.forestry;

import java.lang.management.ManagementFactory;
import java.util.List;

import com.akashic.thepatcher.util.BundleLog;

public final class ForestryC2RuntimeGuard {

    public static final String REQUIRED_COMPILE_COMMAND =
        "-XX:CompileCommand=exclude,forestry/core/genetics/Genome.getChromosomes";

    private static final boolean UNIVERSAL_PATCH_BUNDLED = true;

    private static volatile boolean checked;
    private static volatile boolean active;

    private ForestryC2RuntimeGuard() {}

    public static void verify() {
        if (checked) return;
        synchronized (ForestryC2RuntimeGuard.class) {
            if (checked) return;

            final List<String> arguments =
                ManagementFactory.getRuntimeMXBean().getInputArguments();
            active = hasRequiredExclusion(arguments);
            checked = true;

            final String javaVersion =
                System.getProperty("java.version", "unknown");

            if (active) {
                BundleLog.info(
                    "[Forestry-C2] CompilerOracle exclusion active for "
                        + "Genome.getChromosomes; bundled universal rewrite also armed; java="
                        + javaVersion);
            } else {
                BundleLog.info(
                    "[Forestry-C2] JVM CompileCommand not present; "
                        + "bundled zero-config Genome.getChromosomes rewrite is armed; java="
                        + javaVersion);
            }

            System.setProperty(
                "akashic.forestryC2Guard.active",
                Boolean.toString(active));
            System.setProperty(
                "akashic.forestryUniversalPatch.bundled",
                Boolean.toString(UNIVERSAL_PATCH_BUNDLED));
        }
    }

    static boolean hasRequiredExclusion(List<String> arguments) {
        if (arguments == null) return false;
        for (String argument : arguments) {
            if (argument == null) continue;
            if (argument.equals(REQUIRED_COMPILE_COMMAND)
                || argument.equals(
                    "-XX:CompileCommand=exclude,forestry/core/genetics/Genome::getChromosomes")) {
                return true;
            }
        }
        return false;
    }

    public static boolean isActive() {
        return active;
    }

    public static boolean isUniversalPatchBundled() {
        return UNIVERSAL_PATCH_BUNDLED;
    }

    public static boolean isProtected() {
        return active || UNIVERSAL_PATCH_BUNDLED;
    }
}