package com.akashic.compatlab.client;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import com.akashic.thepatcher.module.mps.MpsAngelicaCompatTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

public final class MpsAngelicaCompatTransformerSelfTest {
    private static final String TARGET =
        "net.machinemuse.numina.render.RenderState";
    private static final String RESOURCE =
        "net/machinemuse/numina/render/RenderState.class";
    private static final String GL11 = "org/lwjgl/opengl/GL11";
    private static final String ANGELICA =
        "com/gtnewhorizons/angelica/glsm/GLStateManager";
    private static final String HELPER =
        "com/akashic/thepatcher/module/mps/MpsAngelica218StateCompat";
    private static final Set<String> TARGET_METHODS = new HashSet<String>(
        Arrays.asList("on2D", "off2D", "blendingOn", "blendingOff", "glowOn", "glowOff"));
    private MpsAngelicaCompatTransformerSelfTest() {}

    public static void main(String[] args) throws Exception {
        final boolean realFingerprints =
            args.length > 0 && "real".equals(args[0]);
        if (realFingerprints) {
            System.clearProperty("akashic.mpsAngelica218.forceForTests");
        } else {
            System.setProperty("akashic.mpsAngelica218.forceForTests", "true");
        }
        System.clearProperty("akashic.mpsAngelica218.disable");

        final byte[] raw = resource(RESOURCE);
        final MpsAngelicaCompatTransformer first =
            new MpsAngelicaCompatTransformer();
        final byte[] patchedRaw = first.transform(TARGET, TARGET, raw);

        require(!Arrays.equals(raw, patchedRaw), "raw transform made no change");
        require(MpsAngelicaCompatTransformer.isPatched(), "raw transform did not mark patched");
        require(MpsAngelicaCompatTransformer.patchedCallCount() == 6, "raw transform count != 6");
        require("PATCHED".equals(MpsAngelicaCompatTransformer.status()),
            "raw status=" + MpsAngelicaCompatTransformer.status());
        inspectPatched(patchedRaw, GL11);

        final byte[] preRedirected = redirectPushPop(raw);
        final MpsAngelicaCompatTransformer second =
            new MpsAngelicaCompatTransformer();
        final byte[] patchedRedirected =
            second.transform(TARGET, TARGET, preRedirected);

        require(!Arrays.equals(preRedirected, patchedRedirected),
            "post-GLSM-order transform made no change");
        require(MpsAngelicaCompatTransformer.isPatched(), "post-GLSM transform did not mark patched");
        require(MpsAngelicaCompatTransformer.patchedCallCount() == 6, "post-GLSM transform count != 6");
        inspectPatched(patchedRedirected, ANGELICA);
        System.setProperty("akashic.mpsAngelica218.disable", "true");
        final MpsAngelicaCompatTransformer disabled =
            new MpsAngelicaCompatTransformer();
        final byte[] disabledResult = disabled.transform(TARGET, TARGET, raw);
        require(Arrays.equals(raw, disabledResult), "kill switch changed bytes");
        require("DISABLED_BY_PROPERTY".equals(MpsAngelicaCompatTransformer.status()),
            "kill switch status=" + MpsAngelicaCompatTransformer.status());

        System.clearProperty("akashic.mpsAngelica218.disable");
        System.clearProperty("akashic.mpsAngelica218.forceForTests");

        System.out.println(
            "MPS_ANGELICA_COMPAT_TRANSFORMER PASS "
            + "rawCalls=6 redirectedCalls=6 killSwitch=exact narrowScopes=6");
    }

    private static void inspectPatched(byte[] bytes, String residualOwner) {
        final ClassNode node = read(bytes);
        int helperCalls = 0;
        int residualTargetPushPop = 0;
        int residualNonTargetPushPop = 0;

        for (MethodNode method : node.methods) {
            for (AbstractInsnNode insn = method.instructions.getFirst();
                 insn != null; insn = insn.getNext()) {
                if (!(insn instanceof MethodInsnNode)) continue;
                final MethodInsnNode call = (MethodInsnNode) insn;
                if (call.getOpcode() != Opcodes.INVOKESTATIC) continue;
                final boolean pushPop = "glPushAttrib".equals(call.name)
                    || "glPopAttrib".equals(call.name);
                if (TARGET_METHODS.contains(method.name)
                    && HELPER.equals(call.owner)
                    && ("pushAttrib".equals(call.name) || "popAttrib".equals(call.name))) {
                    helperCalls++;
                } else if (TARGET_METHODS.contains(method.name) && pushPop) {
                    residualTargetPushPop++;
                } else if (!TARGET_METHODS.contains(method.name)
                    && pushPop
                    && residualOwner.equals(call.owner)) {
                    residualNonTargetPushPop++;
                }
            }
        }

        require(helperCalls == 6, "helper calls=" + helperCalls);
        require(residualTargetPushPop == 0,
            "target methods retain push/pop=" + residualTargetPushPop);
        require(residualNonTargetPushPop >= 2,
            "narrowness proof missing; non-target push/pop="
                + residualNonTargetPushPop);
    }

    private static byte[] redirectPushPop(byte[] source) {
        final ClassNode node = read(source);
        for (MethodNode method : node.methods) {
            for (AbstractInsnNode insn = method.instructions.getFirst();
                 insn != null; insn = insn.getNext()) {
                if (!(insn instanceof MethodInsnNode)) continue;
                final MethodInsnNode call = (MethodInsnNode) insn;
                if (call.getOpcode() == Opcodes.INVOKESTATIC
                    && GL11.equals(call.owner)
                    && ("glPushAttrib".equals(call.name)
                        || "glPopAttrib".equals(call.name))) {
                    call.owner = ANGELICA;
                    call.itf = false;
                }
            }
        }
        final ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static ClassNode read(byte[] bytes) {
        final ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        return node;
    }

    private static byte[] resource(String name) throws Exception {
        final InputStream stream =
            MpsAngelicaCompatTransformerSelfTest.class
                .getClassLoader().getResourceAsStream(name);
        require(stream != null, "resource missing: " + name);
        try {
            final ByteArrayOutputStream out = new ByteArrayOutputStream(8192);
            final byte[] buffer = new byte[8192];
            int count;
            while ((count = stream.read(buffer)) != -1) {
                out.write(buffer, 0, count);
            }
            return out.toByteArray();
        } finally {
            stream.close();
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
