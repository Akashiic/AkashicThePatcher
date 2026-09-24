package com.akashic.thepatcher.module.forestry;

import java.lang.management.ManagementFactory;
import java.util.List;

import com.akashic.thepatcher.util.BundleLog;

public final class ForestryC2RuntimeGuard {

    public static final String REQUIRED_COMPILE_COMMAND =
        "-XX:CompileCommand=exclude,forestry/core/genetics/Genome.getChromosomes";

    private static final String ALTERNATE_COMPILE_COMMAND =
        "-XX:CompileCommand=exclude,forestry/core/genetics/Genome::getChromosomes";

    private static final String UNIVERSAL_SHIM_CLASS =
        "dev.akashic.asynciconcompat.forestry.ForestryGenomeC2Shim";

    private static volatile boolean checked;
    private static volatile boolean active;
    private static volatile boolean compilerOracleActive;
    private static volatile boolean universalShimBundled;

    private ForestryC2RuntimeGuard() {}

    public static void verify() {
        if (checked) {
            return;
        }

        synchronized (ForestryC2RuntimeGuard.class) {
            if (checked) {
                return;
            }

            List<String> arguments =
                ManagementFactory.getRuntimeMXBean().getInputArguments();

            compilerOracleActive =
                hasRequiredExclusion(arguments);
            universalShimBundled =
                hasUniversalShim();

            active =
                compilerOracleActive || universalShimBundled;
            checked = true;

            String javaVersion =
                System.getProperty("java.version", "unknown");

            if (universalShimBundled) {
                if (compilerOracleActive) {
                    BundleLog.info(
                        "[Forestry-C2] Universal Genome shim bundled; "
                            + "CompilerOracle exclusion also active; java="
                            + javaVersion);
                } else {
                    BundleLog.info(
                        "[Forestry-C2] Universal Genome shim bundled; "
                            + "JVM CompileCommand not required; java="
                            + javaVersion);
                }
            } else if (compilerOracleActive) {
                BundleLog.info(
                    "[Forestry-C2] CompilerOracle exclusion active for "
                        + "Genome.getChromosomes; java="
                        + javaVersion);
            } else {
                BundleLog.warn(
                    "[Forestry-C2] Neither universal Genome shim nor JVM "
                        + "safety exclusion is available; java="
                        + javaVersion
                        + ". Java 8u51 previously crashed in C2 while "
                        + "compiling forestry.core.genetics.Genome::"
                        + "getChromosomes.");
            }

            System.setProperty(
                "akashic.forestryC2Guard.active",
                Boolean.toString(active));
            System.setProperty(
                "akashic.forestryC2Guard.compilerOracle",
                Boolean.toString(compilerOracleActive));
            System.setProperty(
                "akashic.forestryC2Guard.universalShim",
                Boolean.toString(universalShimBundled));
        }
    }

    static boolean hasRequiredExclusion(
        List<String> arguments) {

        if (arguments == null) {
            return false;
        }

        for (String argument : arguments) {
            if (argument == null) {
                continue;
            }

            if (REQUIRED_COMPILE_COMMAND.equals(argument)
                || ALTERNATE_COMPILE_COMMAND.equals(argument)) {

                return true;
            }
        }

        return false;
    }

    private static boolean hasUniversalShim() {
        try {
            Class.forName(
                UNIVERSAL_SHIM_CLASS,
                false,
                ForestryC2RuntimeGuard.class.getClassLoader());
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean isActive() {
        return active;
    }

    public static boolean isCompilerOracleActive() {
        return compilerOracleActive;
    }

    public static boolean isUniversalShimBundled() {
        return universalShimBundled;
    }
}