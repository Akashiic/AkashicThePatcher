package com.akashic.thepatcher.module.cinematic;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;

import net.minecraft.launchwrapper.IClassTransformer;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Exact-version compatibility bridge for Akashic Cinematic Menu alpha.4
 * and Angelica 2.2.18.
 */
public final class CinematicAngelicaCompatTransformer implements IClassTransformer {
    private static final String RAW_SCOPE =
        "br.com.redeakashic.cinematic.render.RawScope";
    private static final String VSYNC_BACKEND =
        "com.gtnewhorizons.angelica.glsm.backend.Lwjgl2GLRenderBackend";

    private static final String RAW_SCOPE_RESOURCE =
        "br/com/redeakashic/cinematic/render/RawScope.class";
    private static final String GLSM_RESOURCE =
        "com/gtnewhorizons/angelica/glsm/GLStateManager.class";    private static final String VSYNC_RESOURCE =
        "com/gtnewhorizons/angelica/glsm/backend/Lwjgl2GLRenderBackend.class";

    private static final String RAW_SCOPE_SHA =
        "43922d54b50dfc6c2ce9ac8bb052e07172a46369123143dc8c1371790a988d53";
    private static final String GLSM_BASE_SHA =
        "41595e360943797144409a388b8ca3ab48b18d39fd3336c13cfbd930004f7dc6";
    private static final String GLSM_J17_SHA =
        "9dae6a1ea42f7d42d6ddf90b24e9007d890e1ad15787a01777a423d8317e5f68";
    private static final String GLSM_J21_SHA =
        "f6292d494a17b73c5c189379aaf9304f5e1a61046a6b6b1372597b5fa0726aad";
    private static final String VSYNC_BASE_SHA =
        "6819de7d6ded5753c0d7645304a37ecd4091353b8896a5fdb2806fa9f79993b1";
    private static final String VSYNC_J17_SHA =
        "01d8ae492ae60102a34894744eb98d22c09eacf709e1faeb5715ef7c474ecc7c";

    private static final String STATE_FENCE =
        "com/akashic/thepatcher/module/cinematic/CinematicAngelicaStateFence";
    private static final String VSYNC_GUARD =
        "com/akashic/thepatcher/module/cinematic/AngelicaVSyncGuard";

    private static volatile int rawScopePatches;
    private static volatile int vsyncPatches;

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) {
            return null;
        }        if (RAW_SCOPE.equals(transformedName)) {
            if (Boolean.getBoolean("akashic.cinematicAngelicaStateFence.disable")) {
                return basicClass;
            }
            if (!exactResource(RAW_SCOPE_RESOURCE, RAW_SCOPE_SHA)
                || !exactResource(GLSM_RESOURCE, GLSM_BASE_SHA, GLSM_J17_SHA, GLSM_J21_SHA)) {
                log("RawScope fence inactive: fingerprint mismatch");
                return basicClass;
            }
            return patchRawScope(basicClass);
        }

        if (VSYNC_BACKEND.equals(transformedName)) {
            if (Boolean.getBoolean("akashic.angelicaVSyncGuard.disable")) {
                return basicClass;
            }
            if (!exactResource(VSYNC_RESOURCE, VSYNC_BASE_SHA, VSYNC_J17_SHA)) {
                log("VSync guard inactive: fingerprint mismatch");
                return basicClass;
            }
            return patchVSyncBackend(basicClass);
        }
        return basicClass;
    }

    private static byte[] patchRawScope(byte[] input) {
        try {
            final ClassNode node = new ClassNode();
            new ClassReader(input).accept(node, 0);
            int returns = 0;

            for (MethodNode method : node.methods) {
                if (!"close".equals(method.name) || !"()V".equals(method.desc)) {
                    continue;
                }
                for (AbstractInsnNode insn = method.instructions.getFirst();
                     insn != null; insn = insn.getNext()) {                    if (insn.getOpcode() == Opcodes.RETURN) {
                        final InsnList patch = new InsnList();
                        patch.add(new MethodInsnNode(
                            Opcodes.INVOKESTATIC,
                            STATE_FENCE,
                            "repair",
                            "()V",
                            false));
                        method.instructions.insertBefore(insn, patch);
                        returns++;
                    }
                }
            }

            if (returns != 1) {
                log("RawScope fence fail-closed: expected one close RETURN, found " + returns);
                return input;
            }

            final ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(writer);
            final byte[] output = writer.toByteArray();
            rawScopePatches = returns;
            log("RawScope state fence patched; returns=" + returns);
            return output;
        } catch (Throwable failure) {
            log("RawScope fence fail-closed: " + failure);
            return input;
        }
    }

    private static byte[] patchVSyncBackend(byte[] input) {
        try {
            final ClassNode node = new ClassNode();
            new ClassReader(input).accept(node, 0);
            int calls = 0;            for (MethodNode method : node.methods) {
                if (!"applyVSyncMode".equals(method.name)
                    || !method.desc.endsWith(
                        ")Lcom/gtnewhorizons/angelica/glsm/backend/VSyncMode;")) {
                    continue;
                }

                for (AbstractInsnNode insn = method.instructions.getFirst();
                     insn != null; insn = insn.getNext()) {
                    if (!(insn instanceof MethodInsnNode)) {
                        continue;
                    }
                    final MethodInsnNode call = (MethodInsnNode) insn;
                    if (call.getOpcode() == Opcodes.INVOKESTATIC
                        && "org/lwjgl/opengl/Display".equals(call.owner)
                        && "setVSyncEnabled".equals(call.name)
                        && "(Z)V".equals(call.desc)) {
                        call.owner = VSYNC_GUARD;
                        call.name = "setVSyncEnabled";
                        call.itf = false;
                        calls++;
                    }
                }
            }

            if (calls != 1) {
                log("VSync guard fail-closed: expected one Display call, found " + calls);
                return input;
            }

            final ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(writer);
            final byte[] output = writer.toByteArray();
            vsyncPatches = calls;
            log("Angelica VSync fail-safe patched; calls=" + calls);
            return output;        } catch (Throwable failure) {
            log("VSync guard fail-closed: " + failure);
            return input;
        }
    }

    private static boolean exactResource(String resource, String... accepted) {
        InputStream stream = null;
        try {
            final ClassLoader loader =
                CinematicAngelicaCompatTransformer.class.getClassLoader();
            stream = loader.getResourceAsStream(resource);
            if (stream == null) {
                return false;
            }
            final String actual = sha256(readAll(stream));
            for (String expected : accepted) {
                if (expected.equals(actual)) {
                    return true;
                }
            }
            return false;
        } catch (Throwable failure) {
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
        int read;        while ((read = stream.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private static String sha256(byte[] bytes) throws Exception {
        final byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes);
        final StringBuilder out = new StringBuilder(hash.length * 2);
        for (byte value : hash) {
            out.append(String.format("%02x", value & 0xff));
        }
        return out.toString();
    }

    private static void log(String message) {
        System.out.println("[AkashicThePatcher/Cinematic-Angelica] " + message);
    }

    public static int rawScopePatchCount() {
        return rawScopePatches;
    }

    public static int vsyncPatchCount() {
        return vsyncPatches;
    }
}
