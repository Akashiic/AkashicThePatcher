package com.akashic.thepatcher.module.forestry;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.akashic.thepatcher.util.BundleLog;

/**
 * Forestry 4.11.36 / HotSpot 8u51 C2 crash guard (beta.36).
 *
 * beta.35 only counted the six CompileCommand exclusions on the JVM command
 * line. Those arguments were delivered through the Technic modpack's
 * version.json; any other launcher (SKLauncher, vanilla, Prism...) drops them
 * and the client dies natively in a C2 CompilerThread a few seconds after
 * joining a world.
 *
 * beta.36 ships ForestryGenomeCompat with every Genome helper padded past
 * HotSpot's HugeMethodLimit (8000 bytecode bytes). With the product default
 * DontCompileHugeMethods=true, HotSpot never compiles (C1, C2 or OSR) nor
 * inlines those methods - the same effect as the CompileCommand exclusions,
 * but carried inside the JAR, independent of the launcher.
 *
 * This guard proves both conditions at runtime: it reads the bundled class
 * bytes and checks each helper's Code length, and reads
 * DontCompileHugeMethods from the HotSpot diagnostic MXBean.
 */
public final class ForestryC2RuntimeGuard {

    public static final String REQUIRED_COMPILE_COMMAND =
        "-XX:CompileCommand=exclude,forestry/core/genetics/Genome.getChromosomes";

    /** HotSpot's (develop, non-tunable) HugeMethodLimit. */
    public static final int HOTSPOT_HUGE_METHOD_LIMIT = 8000;

    private static final String COMPAT_RESOURCE =
        "com/akashic/thepatcher/module/forestry/ForestryGenomeCompat.class";

    private static final String[] REQUIRED_EXCLUSIONS = {
        "forestry/core/genetics/Genome.getChromosomes",
        "com/akashic/thepatcher/module/forestry/ForestryGenomeCompat.loadChromosomes",
        "com/akashic/thepatcher/module/forestry/ForestryGenomeCompat.loadStoredChromosome",
        "com/akashic/thepatcher/module/forestry/ForestryGenomeCompat.repairInvalidAlleles",
        "com/akashic/thepatcher/module/forestry/ForestryGenomeCompat.validateChromosome",
        "com/akashic/thepatcher/module/forestry/ForestryGenomeCompat.fillMissingChromosomes",
    };

    /** Helpers that must stay interpreter-only (the Genome wrapper only calls loadChromosomes). */
    static final String[] BARRIER_METHODS = {
        "loadChromosomes",
        "loadStoredChromosome",
        "repairInvalidAlleles",
        "validateChromosome",
        "fillMissingChromosomes",
    };

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
            matchedCount = countRequiredExclusions(arguments);
            active = matchedCount == REQUIRED_EXCLUSIONS.length;
            universalRewriteBundled = hasUniversalRewrite();

            final String hugeFlag = readHugeMethodsFlag();
            final List<String> shortMethods = methodsBelowHugeLimit();
            compileBarrierActive =
                universalRewriteBundled
                    && shortMethods != null
                    && shortMethods.isEmpty()
                    && "true".equals(hugeFlag);
            checked = true;

            final String javaVersion =
                System.getProperty("java.version", "unknown");

            if (active) {
                BundleLog.info(
                    "[Forestry-C2] Full CompilerOracle pack protection active ("
                        + matchedCount + "/" + REQUIRED_EXCLUSIONS.length
                        + "); in-JAR compile barrier " + (compileBarrierActive ? "also active" : "inactive")
                        + "; java=" + javaVersion);
            } else if (compileBarrierActive) {
                BundleLog.info(
                    "[Forestry-C2] In-JAR compile barrier active (5/5 Genome helpers > "
                        + HOTSPOT_HUGE_METHOD_LIMIT
                        + " bytecode bytes, DontCompileHugeMethods=true): helpers are never JIT-compiled or inlined; "
                        + "launcher CompileCommand exclusions not required (" + matchedCount + "/"
                        + REQUIRED_EXCLUSIONS.length + " present); java=" + javaVersion);
            } else {
                BundleLog.warn(
                    "[Forestry-C2] UNPROTECTED: CompileCommand exclusions "
                        + matchedCount + "/" + REQUIRED_EXCLUSIONS.length
                        + " (missing " + missingRequiredExclusions(arguments)
                        + ") and in-JAR compile barrier inactive (DontCompileHugeMethods=" + hugeFlag
                        + ", helpersBelowLimit=" + shortMethods
                        + ", rewriteBundled=" + universalRewriteBundled
                        + "). Java 8u51 may crash natively in a C2 CompilerThread; java=" + javaVersion);
            }

            System.setProperty("akashic.forestryC2Guard.active", Boolean.toString(active));
            System.setProperty("akashic.forestryC2Guard.compilerOracleCount", Integer.toString(matchedCount));
            System.setProperty("akashic.forestryC2Guard.compilerOracleRequired", Integer.toString(REQUIRED_EXCLUSIONS.length));
            System.setProperty("akashic.forestryC2Guard.universalRewrite", Boolean.toString(universalRewriteBundled));
            System.setProperty("akashic.forestryC2Guard.compileBarrier", Boolean.toString(compileBarrierActive));
        }
    }

    static int countRequiredExclusions(List<String> arguments) {
        int count = 0;
        for (String required : REQUIRED_EXCLUSIONS) {
            if (containsExclusion(arguments, required)) count++;
        }
        return count;
    }

    static List<String> missingRequiredExclusions(List<String> arguments) {
        final List<String> missing = new ArrayList<String>();
        for (String required : REQUIRED_EXCLUSIONS) {
            if (!containsExclusion(arguments, required)) missing.add(required);
        }
        return missing;
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

    private static boolean hasUniversalRewrite() {
        try {
            final ClassLoader loader = ForestryC2RuntimeGuard.class.getClassLoader();
            return loader.getResource(COMPAT_RESOURCE) != null
                && loader.getResource(
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

    /** Barrier helpers whose Code length is not above the limit; null if the class cannot be read. */
    private static List<String> methodsBelowHugeLimit() {
        try {
            final InputStream in =
                ForestryC2RuntimeGuard.class.getClassLoader().getResourceAsStream(COMPAT_RESOURCE);
            if (in == null) return null;
            final byte[] bytes;
            try {
                bytes = readAll(in);
            } finally {
                in.close();
            }
            return methodsBelowHugeLimit(codeLengths(bytes));
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static List<String> methodsBelowHugeLimit(Map<String, Integer> lengths) {
        final List<String> below = new ArrayList<String>();
        for (String name : BARRIER_METHODS) {
            final Integer length = lengths.get(name);
            if (length == null || length.intValue() <= HOTSPOT_HUGE_METHOD_LIMIT) {
                below.add(name + "=" + length);
            }
        }
        return below;
    }

    private static byte[] readAll(InputStream in) throws IOException {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final byte[] buffer = new byte[8192];
        for (int n; (n = in.read(buffer)) != -1;) out.write(buffer, 0, n);
        return out.toByteArray();
    }

    /**
     * Minimal class-file reader: method name -> Code attribute code_length.
     * Overloads are not expected in ForestryGenomeCompat; a duplicate name keeps the smallest length.
     */
    public static Map<String, Integer> codeLengths(byte[] classBytes) throws IOException {
        final DataInputStream in = new DataInputStream(new java.io.ByteArrayInputStream(classBytes));
        if (in.readInt() != 0xCAFEBABE) throw new IOException("not a class file");
        in.readUnsignedShort();
        in.readUnsignedShort();
        final int poolCount = in.readUnsignedShort();
        final String[] utf8 = new String[poolCount];
        for (int i = 1; i < poolCount; i++) {
            final int tag = in.readUnsignedByte();
            switch (tag) {
                case 1: utf8[i] = in.readUTF(); break;
                case 3: case 4: in.skipBytes(4); break;
                case 5: case 6: in.skipBytes(8); i++; break;
                case 7: case 8: case 16: case 19: case 20: in.skipBytes(2); break;
                case 9: case 10: case 11: case 12: case 17: case 18: in.skipBytes(4); break;
                case 15: in.skipBytes(3); break;
                default: throw new IOException("unknown constant tag " + tag);
            }
        }
        in.skipBytes(6);
        in.skipBytes(2 * in.readUnsignedShort());
        final int fields = in.readUnsignedShort();
        for (int i = 0; i < fields; i++) {
            in.skipBytes(6);
            skipAttributes(in);
        }
        final Map<String, Integer> lengths = new HashMap<String, Integer>();
        final int methods = in.readUnsignedShort();
        for (int i = 0; i < methods; i++) {
            in.skipBytes(2);
            final String name = utf8[in.readUnsignedShort()];
            in.skipBytes(2);
            final int attributes = in.readUnsignedShort();
            for (int a = 0; a < attributes; a++) {
                final String attributeName = utf8[in.readUnsignedShort()];
                final int length = in.readInt();
                if ("Code".equals(attributeName)) {
                    in.skipBytes(4);
                    final int codeLength = in.readInt();
                    in.skipBytes(length - 8);
                    final Integer previous = lengths.get(name);
                    if (previous == null || codeLength < previous.intValue()) {
                        lengths.put(name, Integer.valueOf(codeLength));
                    }
                } else {
                    in.skipBytes(length);
                }
            }
        }
        return lengths;
    }

    private static void skipAttributes(DataInputStream in) throws IOException {
        final int attributes = in.readUnsignedShort();
        for (int a = 0; a < attributes; a++) {
            in.skipBytes(2);
            in.skipBytes(in.readInt());
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

    public static boolean isCompileBarrierActive() {
        return compileBarrierActive;
    }
}
