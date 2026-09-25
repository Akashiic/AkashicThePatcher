package com.akashic.thepatcher.module.startupaudit;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.launchwrapper.IClassTransformer;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

public final class FmlPhaseAuditTransformer
    implements IClassTransformer {

    private static final String TARGET =
        "cpw.mods.fml.common.LoadController";

    private static final String HOOKS =
        "com/akashic/thepatcher/module/startupaudit/FmlPhaseAuditHooks";

    private static final String PHASE_METHOD =
        "propogateStateMessage";

    private static final String PHASE_DESC =
        "(Lcpw/mods/fml/common/event/FMLEvent;)V";

    private static final String MOD_METHOD =
        "sendEventToModContainer";

    private static final String MOD_DESC =
        "(Lcpw/mods/fml/common/event/FMLEvent;"
            + "Lcpw/mods/fml/common/ModContainer;)V";

    private static final String BUILD_METHOD =
        "buildModList";

    private static final String BUILD_DESC =
        "(Lcpw/mods/fml/common/event/FMLLoadEvent;)V";

    @Override
    public byte[] transform(
        String name,
        String transformedName,
        byte[] basicClass) {

        if (basicClass == null) return null;

        if (!TARGET.equals(name)
            && !TARGET.equals(transformedName)) {

            return basicClass;
        }

        try {
            byte[] result = patch(basicClass);

            System.out.println(
                "[Akashic-ThePatcher/FMLPhaseAudit] "
                    + "LoadController instrumentation installed.");

            return result;
        } catch (Throwable failure) {
            System.err.println(
                "[Akashic-ThePatcher/FMLPhaseAudit] "
                    + "PATCH REFUSED: "
                    + failure.getClass().getName()
                    + ": "
                    + failure.getMessage());

            System.setProperty(
                "akashic.fmlPhaseAudit.failed",
                "true");

            return basicClass;
        }
    }

    static byte[] patch(byte[] basicClass) {
        ClassReader reader =
            new ClassReader(basicClass);

        ClassNode node =
            new ClassNode();

        reader.accept(node, 0);

        MethodNode phase =
            findUnique(
                node,
                PHASE_METHOD,
                PHASE_DESC);

        MethodNode mod =
            findUnique(
                node,
                MOD_METHOD,
                MOD_DESC);

        MethodNode build =
            findUnique(
                node,
                BUILD_METHOD,
                BUILD_DESC);

        if (containsHook(phase, "phaseEnter")
            || containsHook(mod, "modEnter")
            || containsHook(build, "spanEnter")) {

            return basicClass;
        }

        verifyOriginalShape(
            phase,
            mod,
            build);

        injectPhase(phase);
        injectMod(mod);
        injectBuildSpan(build);

        ClassWriter writer =
            new ClassWriter(
                reader,
                ClassWriter.COMPUTE_MAXS);

        node.accept(writer);

        byte[] result =
            writer.toByteArray();

        verifyPatched(result);

        return result;
    }

    private static MethodNode findUnique(
        ClassNode node,
        String name,
        String desc) {

        MethodNode found = null;

        for (Object object : node.methods) {
            MethodNode method =
                (MethodNode) object;

            if (!name.equals(method.name)
                || !desc.equals(method.desc)) {

                continue;
            }

            if (found != null) {
                throw new IllegalStateException(
                    "duplicate target "
                        + name
                        + desc);
            }

            found = method;
        }

        if (found == null) {
            throw new IllegalStateException(
                "missing target "
                    + name
                    + desc);
        }

        return found;
    }

    private static void verifyOriginalShape(
        MethodNode phase,
        MethodNode mod,
        MethodNode build) {

        int sendCalls =
            countCall(
                phase,
                "cpw/mods/fml/common/LoadController",
                MOD_METHOD,
                MOD_DESC);

        if (sendCalls != 1) {
            throw new IllegalStateException(
                "propogateStateMessage fingerprint mismatch sendCalls="
                    + sendCalls);
        }

        int eventPosts =
            countCallName(
                mod,
                "com/google/common/eventbus/EventBus",
                "post");

        if (eventPosts != 1) {
            throw new IllegalStateException(
                "sendEventToModContainer fingerprint mismatch postCalls="
                    + eventPosts);
        }

        int registerBusCalls =
            countCallName(
                build,
                "cpw/mods/fml/common/ModContainer",
                "registerBus");

        if (registerBusCalls != 1) {
            throw new IllegalStateException(
                "buildModList fingerprint mismatch registerBusCalls="
                    + registerBusCalls);
        }

        if (countReturns(phase) < 1
            || countReturns(mod) < 1
            || countReturns(build) < 1) {

            throw new IllegalStateException(
                "target return shape changed");
        }
    }

    private static void injectPhase(
        MethodNode method) {

        InsnList head =
            new InsnList();

        head.add(
            new VarInsnNode(
                Opcodes.ALOAD,
                1));

        head.add(
            new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                HOOKS,
                "phaseEnter",
                PHASE_DESC,
                false));

        method.instructions.insert(head);

        injectBeforeReturns(
            method,
            new ExitFactory() {
                @Override
                public InsnList create() {
                    InsnList exit =
                        new InsnList();

                    exit.add(
                        new VarInsnNode(
                            Opcodes.ALOAD,
                            1));

                    exit.add(
                        new MethodInsnNode(
                            Opcodes.INVOKESTATIC,
                            HOOKS,
                            "phaseExit",
                            PHASE_DESC,
                            false));

                    return exit;
                }
            });
    }

    private static void injectMod(
        MethodNode method) {

        InsnList head =
            new InsnList();

        head.add(
            new VarInsnNode(
                Opcodes.ALOAD,
                1));

        head.add(
            new VarInsnNode(
                Opcodes.ALOAD,
                2));

        head.add(
            new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                HOOKS,
                "modEnter",
                MOD_DESC,
                false));

        method.instructions.insert(head);

        injectBeforeReturns(
            method,
            new ExitFactory() {
                @Override
                public InsnList create() {
                    InsnList exit =
                        new InsnList();

                    exit.add(
                        new VarInsnNode(
                            Opcodes.ALOAD,
                            1));

                    exit.add(
                        new VarInsnNode(
                            Opcodes.ALOAD,
                            2));

                    exit.add(
                        new MethodInsnNode(
                            Opcodes.INVOKESTATIC,
                            HOOKS,
                            "modExit",
                            MOD_DESC,
                            false));

                    return exit;
                }
            });
    }

    private static void injectBuildSpan(
        MethodNode method) {

        InsnList head =
            new InsnList();

        head.add(
            new LdcInsnNode(
                "LoadController.buildModList"));

        head.add(
            new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                HOOKS,
                "spanEnter",
                "(Ljava/lang/String;)V",
                false));

        method.instructions.insert(head);

        injectBeforeReturns(
            method,
            new ExitFactory() {
                @Override
                public InsnList create() {
                    InsnList exit =
                        new InsnList();

                    exit.add(
                        new LdcInsnNode(
                            "LoadController.buildModList"));

                    exit.add(
                        new MethodInsnNode(
                            Opcodes.INVOKESTATIC,
                            HOOKS,
                            "spanExit",
                            "(Ljava/lang/String;)V",
                            false));

                    return exit;
                }
            });
    }

    private static void injectBeforeReturns(
        MethodNode method,
        ExitFactory factory) {

        List<AbstractInsnNode> returns =
            new ArrayList<AbstractInsnNode>();

        for (AbstractInsnNode insn =
                 method.instructions.getFirst();
             insn != null;
             insn = insn.getNext()) {

            if (insn.getOpcode() == Opcodes.RETURN) {
                returns.add(insn);
            }
        }

        for (AbstractInsnNode ret : returns) {
            method.instructions.insertBefore(
                ret,
                factory.create());
        }
    }

    private static boolean containsHook(
        MethodNode method,
        String hookName) {

        return countCall(
            method,
            HOOKS,
            hookName,
            null) > 0;
    }

    private static int countCall(
        MethodNode method,
        String owner,
        String name,
        String desc) {

        int count = 0;

        for (AbstractInsnNode insn =
                 method.instructions.getFirst();
             insn != null;
             insn = insn.getNext()) {

            if (!(insn instanceof MethodInsnNode)) {
                continue;
            }

            MethodInsnNode call =
                (MethodInsnNode) insn;

            if (!owner.equals(call.owner)
                || !name.equals(call.name)) {

                continue;
            }

            if (desc != null
                && !desc.equals(call.desc)) {

                continue;
            }

            count++;
        }

        return count;
    }

    private static int countCallName(
        MethodNode method,
        String owner,
        String name) {

        return countCall(
            method,
            owner,
            name,
            null);
    }

    private static int countReturns(
        MethodNode method) {

        int count = 0;

        for (AbstractInsnNode insn =
                 method.instructions.getFirst();
             insn != null;
             insn = insn.getNext()) {

            if (insn.getOpcode() == Opcodes.RETURN) {
                count++;
            }
        }

        return count;
    }

    private static void verifyPatched(
        byte[] bytes) {

        ClassNode node =
            new ClassNode();

        new ClassReader(bytes)
            .accept(node, 0);

        MethodNode phase =
            findUnique(
                node,
                PHASE_METHOD,
                PHASE_DESC);

        MethodNode mod =
            findUnique(
                node,
                MOD_METHOD,
                MOD_DESC);

        MethodNode build =
            findUnique(
                node,
                BUILD_METHOD,
                BUILD_DESC);

        if (countCall(
                phase,
                HOOKS,
                "phaseEnter",
                PHASE_DESC) != 1) {

            throw new IllegalStateException(
                "phaseEnter verification failed");
        }

        if (countCall(
                phase,
                HOOKS,
                "phaseExit",
                PHASE_DESC)
            != countReturns(phase)) {

            throw new IllegalStateException(
                "phaseExit verification failed");
        }

        if (countCall(
                mod,
                HOOKS,
                "modEnter",
                MOD_DESC) != 1) {

            throw new IllegalStateException(
                "modEnter verification failed");
        }

        if (countCall(
                mod,
                HOOKS,
                "modExit",
                MOD_DESC)
            != countReturns(mod)) {

            throw new IllegalStateException(
                "modExit verification failed");
        }

        if (countCall(
                build,
                HOOKS,
                "spanEnter",
                "(Ljava/lang/String;)V") != 1
            || countCall(
                build,
                HOOKS,
                "spanExit",
                "(Ljava/lang/String;)V")
                != countReturns(build)) {

            throw new IllegalStateException(
                "build span verification failed");
        }

        if (countCall(
                phase,
                "cpw/mods/fml/common/LoadController",
                MOD_METHOD,
                MOD_DESC) != 1
            || countCallName(
                mod,
                "com/google/common/eventbus/EventBus",
                "post") != 1
            || countCallName(
                build,
                "cpw/mods/fml/common/ModContainer",
                "registerBus") != 1) {

            throw new IllegalStateException(
                "original calls changed");
        }
    }

    private interface ExitFactory {
        InsnList create();
    }
}
