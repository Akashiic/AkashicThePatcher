package com.akashic.thepatcher.module.forestry;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;

import com.akashic.thepatcher.util.BundleLog;

public final class ForestryC2RuntimeGuard {

    public static final String REQUIRED_COMPILE_COMMAND =
        "-XX:CompileCommand=exclude,forestry/core/genetics/Genome.getChromosomes";

    private static final String[] REQUIRED_EXCLUSIONS = {
        "forestry/core/genetics/Genome.getChromosomes",
        "com/akashic/thepatcher/module/forestry/ForestryGenomeCompat.loadChromosomes",
        "com/akashic/thepatcher/module/forestry/ForestryGenomeCompat.loadStoredChromosome",
        "com/akashic/thepatcher/module/forestry/ForestryGenomeCompat.repairInvalidAlleles",
        "com/akashic/thepatcher/module/forestry/ForestryGenomeCompat.validateChromosome",
        "com/akashic/thepatcher/module/forestry/ForestryGenomeCompat.fillMissingChromosomes"
    };

    private static volatile boolean checked;
    private static volatile boolean active;
    private static volatile int matchedCount;
    private static volatile boolean universalRewriteBundled;

    private ForestryC2RuntimeGuard() {}

    public static void verify() {
        if (checked) return;

        synchronized (ForestryC2RuntimeGuard.class) {
            if (checked) return;

            final List<String> arguments =
                ManagementFactory.getRuntimeMXBean().getInputArguments();

            matchedCount = countRequiredExclusions(arguments);
            active = matchedCount == REQUIRED_EXCLUSIONS.length;
            universalRewriteBundled = hasUniversalRewrite();
            checked = true;

            final String javaVersion =
                System.getProperty("java.version", "unknown");

            if (active) {
                BundleLog.info(
                    "[Forestry-C2] Full CompilerOracle pack protection active ("
                        + matchedCount + "/" + REQUIRED_EXCLUSIONS.length
                        + "); universal rewrite also bundled; java="
                        + javaVersion);
            } else {
                final List<String> missing =
                    missingRequiredExclusions(arguments);

                BundleLog.warn(
                    "[Forestry-C2] INCOMPLETE JVM protection ("
                        + matchedCount + "/" + REQUIRED_EXCLUSIONS.length
                        + "). Missing CompileCommand exclusions: "
                        + missing
                        + ". The bundled Genome rewrite alone is not sufficient "
                        + "for Java 8u51 C2 safety; java="
                        + javaVersion);
            }

            System.setProperty(
                "akashic.forestryC2Guard.active",
                Boolean.toString(active));
            System.setProperty(
                "akashic.forestryC2Guard.compilerOracleCount",
                Integer.toString(matchedCount));
            System.setProperty(
                "akashic.forestryC2Guard.compilerOracleRequired",
                Integer.toString(REQUIRED_EXCLUSIONS.length));
            System.setProperty(
                "akashic.forestryC2Guard.universalRewrite",
                Boolean.toString(universalRewriteBundled));
        }
    }

    static int countRequiredExclusions(List<String> arguments) {
        int count = 0;
        for (String required : REQUIRED_EXCLUSIONS) {
            if (containsExclusion(arguments, required)) {
                count++;
            }
        }
        return count;
    }

    static List<String> missingRequiredExclusions(
        List<String> arguments) {

        final List<String> missing =
            new ArrayList<String>();

        for (String required : REQUIRED_EXCLUSIONS) {
            if (!containsExclusion(arguments, required)) {
                missing.add(required);
            }
        }

        return missing;
    }

    private static boolean containsExclusion(
        List<String> arguments,
        String required) {

        if (arguments == null) return false;

        for (String argument : arguments) {
            if (argument == null) continue;

            final String prefix =
                "-XX:CompileCommand=exclude,";

            if (!argument.startsWith(prefix)) continue;

            String actual =
                argument.substring(prefix.length())
                    .replace("::", ".");

            if (required.equals(actual)) {
                return true;
            }
        }

        return false;
    }

    private static boolean hasUniversalRewrite() {
        try {
            final ClassLoader loader =
                ForestryC2RuntimeGuard.class.getClassLoader();

            return loader.getResource(
                    "com/akashic/thepatcher/module/forestry/ForestryGenomeCompat.class")
                    != null
                && loader.getResource(
                    "com/akashic/thepatcher/module/forestry/ForestryGenomeC2Transformer.class")
                    != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean isActive() {
        return active;
    }

    public static int getMatchedCount() {
        return matchedCount;
    }

    public static boolean isUniversalRewriteBundled() {
        return universalRewriteBundled;
    }
}