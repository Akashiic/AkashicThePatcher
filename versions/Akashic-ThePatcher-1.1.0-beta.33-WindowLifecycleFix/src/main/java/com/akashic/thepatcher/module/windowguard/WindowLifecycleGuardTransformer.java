package com.akashic.thepatcher.module.windowguard;

import net.minecraft.launchwrapper.IClassTransformer;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

public final class WindowLifecycleGuardTransformer
    implements IClassTransformer {

    private static final String TARGET =
        "net.minecraft.client.Minecraft";

    private static final String DISPLAY =
        "org/lwjgl/opengl/Display";

    private static final String GUARD =
        "com/akashic/thepatcher/module/windowguard/WindowLifecycleGuard";

    private static final String MODE_DESC =
        "(Lorg/lwjgl/opengl/DisplayMode;)V";

    @Override
    public byte[] transform(
        String name,
        String transformedName,
        byte[] basicClass) {

        if (basicClass == null) return null;

        String effective =
            transformedName != null ? transformedName : name;

        if (!TARGET.equals(effective)) {
            return basicClass;
        }

        try {
            byte[] patched = patch(basicClass);
            System.out.println(
                "[Akashic-ThePatcher/WindowGuard] "
                    + "Minecraft display-mode transitions patched.");
            return patched;
        } catch (Throwable failure) {
            if (failure instanceof ThreadDeath) {
                throw (ThreadDeath) failure;
            }
            if (failure instanceof VirtualMachineError) {
                throw (VirtualMachineError) failure;
            }

            System.err.println(
                "[Akashic-ThePatcher/WindowGuard] PATCH REFUSED: "
                    + failure.getClass().getName()
                    + ": "
                    + failure.getMessage());
            System.setProperty(
                "akashic.windowGuard.failed",
                "true");
            return basicClass;
        }
    }

    static byte[] patch(byte[] basicClass) {
        ClassReader reader = new ClassReader(basicClass);
        ClassNode node = new ClassNode();
        reader.accept(node, 0);

        MethodNode update = findMethod(
            node,
            new String[] {
                "updateDisplayMode",
                "func_110441_Q",
                "ai"
            });

        MethodNode toggle = findMethod(
            node,
            new String[] {
                "toggleFullscreen",
                "func_71352_k",
                "o"
            });

        int updateChanges = replaceSetDisplayMode(update);
        int toggleChanges = replaceSetDisplayMode(toggle);

        if (updateChanges != 1 || toggleChanges != 1) {
            throw new IllegalStateException(
                "validated call shape changed: update="
                    + updateChanges
                    + " toggle="
                    + toggleChanges);
        }

        ClassWriter writer =
            new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);

        node.accept(writer);
        byte[] result = writer.toByteArray();

        verifyPatched(result);
        return result;
    }

    private static MethodNode findMethod(
        ClassNode node,
        String[] names) {

        MethodNode found = null;

        for (Object object : node.methods) {
            MethodNode method = (MethodNode) object;
            if (!"()V".equals(method.desc)) continue;
            if (!matches(method.name, names)) continue;

            if (found != null) {
                throw new IllegalStateException(
                    "duplicate target method " + method.name);
            }
            found = method;
        }

        if (found == null) {
            throw new IllegalStateException(
                "missing target method");
        }

        return found;
    }

    private static boolean matches(
        String actual,
        String[] names) {

        for (String name : names) {
            if (name.equals(actual)) return true;
        }
        return false;
    }

    private static int replaceSetDisplayMode(
        MethodNode method) {

        int changes = 0;

        for (AbstractInsnNode insn =
                 method.instructions.getFirst();
             insn != null;
             insn = insn.getNext()) {

            if (!(insn instanceof MethodInsnNode)) {
                continue;
            }

            MethodInsnNode call = (MethodInsnNode) insn;

            if (call.getOpcode() != Opcodes.INVOKESTATIC
                || !DISPLAY.equals(call.owner)
                || !"setDisplayMode".equals(call.name)
                || !MODE_DESC.equals(call.desc)) {

                continue;
            }

            call.owner = GUARD;
            call.name = "setDisplayMode";
            call.itf = false;
            changes++;
        }

        return changes;
    }

    private static void verifyPatched(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);

        MethodNode update = findMethod(
            node,
            new String[] {
                "updateDisplayMode",
                "func_110441_Q",
                "ai"
            });

        MethodNode toggle = findMethod(
            node,
            new String[] {
                "toggleFullscreen",
                "func_71352_k",
                "o"
            });

        if (countGuardCalls(update) != 1
            || countGuardCalls(toggle) != 1
            || countDirectDisplayModeCalls(update) != 0
            || countDirectDisplayModeCalls(toggle) != 0) {

            throw new IllegalStateException(
                "post-patch verification failed");
        }
    }

    private static int countGuardCalls(MethodNode method) {
        return countCalls(method, GUARD, "setDisplayMode");
    }

    private static int countDirectDisplayModeCalls(
        MethodNode method) {

        return countCalls(
            method,
            DISPLAY,
            "setDisplayMode");
    }

    private static int countCalls(
        MethodNode method,
        String owner,
        String name) {

        int count = 0;

        for (AbstractInsnNode insn =
                 method.instructions.getFirst();
             insn != null;
             insn = insn.getNext()) {

            if (!(insn instanceof MethodInsnNode)) {
                continue;
            }

            MethodInsnNode call = (MethodInsnNode) insn;

            if (owner.equals(call.owner)
                && name.equals(call.name)
                && MODE_DESC.equals(call.desc)) {

                count++;
            }
        }

        return count;
    }
}
