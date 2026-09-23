package com.akashic.thepatcher.module.mps;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import net.minecraft.launchwrapper.IClassTransformer;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Exact-version bridge for Numina RenderState on Angelica 2.2.18.
 * Fail-closed: any unexpected Angelica or Numina fingerprint leaves bytes untouched.
 */
public final class MpsAngelicaCompatTransformer implements IClassTransformer {
    private static final String TARGET = "net.machinemuse.numina.render.RenderState";
    private static final String HELPER =
        "com/akashic/thepatcher/module/mps/MpsAngelica218StateCompat";
    private static final String ANGELICA_OWNER =
        "com/gtnewhorizons/angelica/glsm/GLStateManager";
    private static final String LWJGL_OWNER = "org/lwjgl/opengl/GL11";
    private static final String ANGELICA_RESOURCE =
        "com/gtnewhorizons/angelica/glsm/GLStateManager.class";
    private static final String NUMINA_RENDERSTATE_RESOURCE =
        "net/machinemuse/numina/render/RenderState.class";

    private static final String EXPECTED_ANGELICA_BASE_SHA256 =
        "41595e360943797144409a388b8ca3ab48b18d39fd3336c13cfbd930004f7dc6";
    private static final String EXPECTED_ANGELICA_JAVA17_SHA256 =
        "9dae6a1ea42f7d42d6ddf90b24e9007d890e1ad15787a01777a423d8317e5f68";
    private static final String EXPECTED_ANGELICA_JAVA21_SHA256 =
        "f6292d494a17b73c5c189379aaf9304f5e1a61046a6b6b1372597b5fa0726aad";
    private static final String EXPECTED_NUMINA_RENDERSTATE_SHA256 =
        "bc331c67fa8fae765fb8bfce84f9872446498d32707adc572efd63ec55546746";

    private static final Set<String> PUSH_METHODS = new HashSet<String>(Arrays.asList(
        "on2D", "blendingOn", "glowOn"));
    private static final Set<String> POP_METHODS = new HashSet<String>(Arrays.asList(
        "off2D", "blendingOff", "glowOff"));

    private static volatile boolean patched;
    private static volatile int patchedCalls;
    private static volatile String status = "NOT_VISITED";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null || !TARGET.equals(transformedName)) {
            return basicClass;
        }
        if (Boolean.getBoolean("akashic.mpsAngelica218.disable")) {
            status = "DISABLED_BY_PROPERTY";
            return basicClass;
        }
        if (!angelica218Exact()) {
            status = "ANGELICA_FINGERPRINT_MISMATCH";
            log("inactive: Angelica 2.2.18 fingerprint not present");
            return basicClass;
        }
        if (!numina041105Exact()) {
            status = "NUMINA_FINGERPRINT_MISMATCH";
            log("inactive: raw Numina 0.4.1.105 RenderState fingerprint not present");
            return basicClass;
        }

        try {
            final ClassNode node = new ClassNode();
            new ClassReader(basicClass).accept(node, 0);
            int changed = 0;

            for (MethodNode method : node.methods) {
                if (!"()V".equals(method.desc)) {
                    continue;
                }
                final boolean push = PUSH_METHODS.contains(method.name);
                final boolean pop = POP_METHODS.contains(method.name);
                if (!push && !pop) {
                    continue;
                }

                int localChanges = 0;
                for (AbstractInsnNode insn = method.instructions.getFirst();
                     insn != null; insn = insn.getNext()) {
                    if (!(insn instanceof MethodInsnNode)) {
                        continue;
                    }
                    final MethodInsnNode call = (MethodInsnNode) insn;
                    if (call.getOpcode() != Opcodes.INVOKESTATIC) {
                        continue;
                    }

                    if (push
                        && "glPushAttrib".equals(call.name)
                        && "(I)V".equals(call.desc)
                        && isSupportedOwner(call.owner)) {
                        call.owner = HELPER;
                        call.name = "pushAttrib";
                        call.itf = false;
                        localChanges++;
                    } else if (pop
                        && "glPopAttrib".equals(call.name)
                        && "()V".equals(call.desc)
                        && isSupportedOwner(call.owner)) {
                        call.owner = HELPER;
                        call.name = "popAttrib";
                        call.itf = false;
                        localChanges++;
                    }
                }

                if (localChanges != 1) {
                    status = "SHAPE_MISMATCH:" + method.name + ":" + localChanges;
                    log("fail-closed: unexpected " + method.name
                        + " push/pop call count=" + localChanges);
                    return basicClass;
                }
                changed += localChanges;
            }

            if (changed != 6) {
                status = "SHAPE_MISMATCH:TOTAL:" + changed;
                log("fail-closed: expected 6 Numina state calls, found " + changed);
                return basicClass;
            }

            final ClassWriter writer = new ClassWriter(0);
            node.accept(writer);
            final byte[] result = writer.toByteArray();
            patched = true;
            patchedCalls = changed;
            status = "PATCHED";
            log("patched Numina RenderState for Angelica 2.2.18; calls=" + changed);
            return result;
        } catch (Throwable failure) {
            patched = false;
            patchedCalls = 0;
            status = "ERROR:" + failure.getClass().getName();
            log("fail-closed after transformer error: " + failure);
            return basicClass;
        }
    }

    private static boolean isSupportedOwner(String owner) {
        return LWJGL_OWNER.equals(owner) || ANGELICA_OWNER.equals(owner);
    }

    private static boolean numina041105Exact() {
        return exactResource(
            NUMINA_RENDERSTATE_RESOURCE,
            "Numina",
            EXPECTED_NUMINA_RENDERSTATE_SHA256);
    }

    private static boolean angelica218Exact() {
        if (Boolean.getBoolean("akashic.mpsAngelica218.forceForTests")) {
            return true;
        }
        return exactResource(
            ANGELICA_RESOURCE,
            "Angelica",
            EXPECTED_ANGELICA_BASE_SHA256,
            EXPECTED_ANGELICA_JAVA17_SHA256,
            EXPECTED_ANGELICA_JAVA21_SHA256);
    }

    private static boolean exactResource(
        String resource,
        String label,
        String... expectedSha256) {
        InputStream stream = null;
        try {
            final ClassLoader loader =
                MpsAngelicaCompatTransformer.class.getClassLoader();
            stream = loader.getResourceAsStream(resource);
            if (stream == null) {
                return false;
            }
            final String actual = sha256(readAll(stream));
            for (String expected : expectedSha256) {
                if (expected.equals(actual)) {
                    return true;
                }
            }
            return false;
        } catch (Throwable failure) {
            log(label + " fingerprint probe failed: " + failure);
            return false;
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (Exception ignored) {
                    // no-op
                }
            }
        }
    }

    private static byte[] readAll(InputStream stream) throws Exception {
        final ByteArrayOutputStream out = new ByteArrayOutputStream(65536);
        final byte[] buffer = new byte[8192];
        int read;
        while ((read = stream.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private static String sha256(byte[] bytes) {
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            final byte[] hash = digest.digest(bytes);
            final StringBuilder out = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                out.append(String.format("%02x", value & 0xff));
            }
            return out.toString();
        } catch (Exception failure) {
            throw new IllegalStateException("SHA-256 unavailable", failure);
        }
    }

    private static void log(String message) {
        System.out.println("[AkashicThePatcher/MPS-Angelica] " + message);
    }

    public static boolean isPatched() {
        return patched;
    }

    public static int patchedCallCount() {
        return patchedCalls;
    }

    public static String status() {
        return status;
    }
}
