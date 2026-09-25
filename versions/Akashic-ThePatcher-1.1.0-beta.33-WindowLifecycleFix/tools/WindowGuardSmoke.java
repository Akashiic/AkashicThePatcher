package com.akashic.compatlab.tools;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import com.akashic.thepatcher.module.windowguard.WindowLifecycleGuardTransformer;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public final class WindowGuardSmoke {

    private static final String DISPLAY =
        "org/lwjgl/opengl/Display";

    private static final String GUARD =
        "com/akashic/thepatcher/module/windowguard/WindowLifecycleGuard";

    private static final String DESC =
        "(Lorg/lwjgl/opengl/DisplayMode;)V";

    private WindowGuardSmoke() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("expected SRG minecraft jar");
        }
        byte[] input = readClass(args[0]);
        int[] before = count(input);

        WindowLifecycleGuardTransformer transformer =
            new WindowLifecycleGuardTransformer();

        byte[] output = transformer.transform(
            "net.minecraft.client.Minecraft",
            "net.minecraft.client.Minecraft",
            input);

        int[] after = count(output);

        require(before[0] == 2, "before target direct calls=" + before[0]);
        require(before[1] == 0, "before guard calls=" + before[1]);
        require(after[0] == 0, "after target direct calls=" + after[0]);
        require(after[1] == 2, "after guard calls=" + after[1]);
        require(after[2] == 1, "unrelated direct calls changed=" + after[2]);

        System.out.println(
            "WINDOW_GUARD_SMOKE PASS targetDirectBefore="
                + before[0]
                + " guardAfter="
                + after[1]
                + " unrelatedDirectAfter="
                + after[2]);
    }

    private static byte[] readClass(String jarPath)
        throws Exception {
        JarFile jar = new JarFile(jarPath);
        try {
            JarEntry entry = jar.getJarEntry(
                "net/minecraft/client/Minecraft.class");

            if (entry == null) {
                throw new IllegalStateException("Minecraft.class missing");
            }

            InputStream in = jar.getInputStream(entry);
            try {
                ByteArrayOutputStream out =
                    new ByteArrayOutputStream();

                byte[] buffer = new byte[8192];
                int read;

                while ((read = in.read(buffer)) >= 0) {
                    out.write(buffer, 0, read);
                }

                return out.toByteArray();
            } finally {
                in.close();
            }
        } finally {
            jar.close();
        }
    }

    private static int[] count(byte[] bytes) {
        final int[] counts = new int[3];

        new ClassReader(bytes).accept(
            new ClassVisitor(Opcodes.ASM5) {
                @Override
                public MethodVisitor visitMethod(
                    int access,
                    final String name,
                    String desc,
                    String signature,
                    String[] exceptions) {

                    final boolean target =
                        ("func_110441_Q".equals(name)
                            || "func_71352_k".equals(name))
                            && "()V".equals(desc);

                    return new MethodVisitor(Opcodes.ASM5) {
                        @Override
                        public void visitMethodInsn(
                            int opcode,
                            String owner,
                            String methodName,
                            String methodDesc,
                            boolean itf) {

                            if ("setDisplayMode".equals(methodName)
                                && DESC.equals(methodDesc)) {
                                if (target && DISPLAY.equals(owner)) {
                                    counts[0]++;
                                } else if (target && GUARD.equals(owner)) {
                                    counts[1]++;
                                } else if (!target && DISPLAY.equals(owner)) {
                                    counts[2]++;
                                }
                            }

                            super.visitMethodInsn(
                                opcode,
                                owner,
                                methodName,
                                methodDesc,
                                itf);
                        }
                    };
                }
            },
            0);

        return counts;
    }

    private static void require(
        boolean condition,
        String message) {

        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
