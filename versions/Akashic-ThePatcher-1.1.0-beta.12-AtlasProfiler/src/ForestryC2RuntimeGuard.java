package com.akashic.thepatcher.module.forestry;

import java.lang.management.ManagementFactory;
import java.util.List;

import com.akashic.thepatcher.util.BundleLog;

public final class ForestryC2RuntimeGuard {

    public static final String REQUIRED_COMPILE_COMMAND =
        "-XX:CompileCommand=exclude,forestry/core/genetics/Genome.getChromosomes";

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

            final String javaVersion = System.getProperty("java.version", "unknown");
            if (active) {
                BundleLog.info(
                    "[Forestry-C2] CompilerOracle exclusion active for "
                        + "Genome.getChromosomes; java=" + javaVersion);
            } else {
                BundleLog.warn(
                    "[Forestry-C2] REQUIRED JVM safety exclusion is missing: "
                        + REQUIRED_COMPILE_COMMAND
                        + " ; java=" + javaVersion
                        + ". Java 8u51 previously crashed in C2 while compiling "
                        + "forestry.core.genetics.Genome::getChromosomes.");
            }

            System.setProperty(
                "akashic.forestryC2Guard.active",
                Boolean.toString(active));
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
}