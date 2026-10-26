package com.akashic.thepatcher.module.angelica;

import net.minecraft.launchwrapper.IClassTransformer;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodNode;

import com.akashic.thepatcher.core.RuntimeSide;
import com.akashic.thepatcher.util.BundleLog;

/**
 * Angelica shader packs in a .zip on Java 8 (beta.42).
 *
 * Angelica's ShaderPack (2.2.37 and master as of 2026-10-26) finds the per-dimension
 * folders of packs without dimension.properties with
 *   Files.list(root).filter(Files::isDirectory).map(Path::getFileName).map(Path::toString)
 *        .filter(name -> name.matches("world-?\\d+"))
 * Java 8's zip file system returns directory names with a trailing '/' ("world0/"), so on
 * Java 8 no folder matches. Packs that keep their programs in world0/ world-1/ world1/ with no
 * dimension.properties (BSL 8.2.09 in the Forbidden pack, run on Java 8) load with an
 * empty program set: none of their gbuffers/composite programs run and Iris' Distant
 * Horizons support reports "No DH shader found in this pack" / "This pack doesn't have DH
 * support", so DH's distant terrain never goes through the shader pack. Extracted packs and
 * Java 9+ are not affected.
 *
 * The Path::toString method reference of that stream becomes
 * AngelicaShaderPackFolderHooks.folderName(Path), which strips the trailing '/'. Fails
 * closed: exactly one such method reference in a constructor of a class that also has the
 * "world-?\\d+" pattern, else the class is left alone.
 * Kill-switch: -Dakashic.angelicaShaderPackFolders.disable=true
 */
public final class AngelicaShaderPackFolderTransformer
    implements IClassTransformer {

    public static final String DISABLE_PROPERTY =
        "akashic.angelicaShaderPackFolders.disable";

    static final String SHADER_PACK = "net.coderbot.iris.shaderpack.ShaderPack";
    static final String PATTERN = "world-?\\d+";

    private static final String METAFACTORY_OWNER = "java/lang/invoke/LambdaMetafactory";
    private static final String PATH = "java/nio/file/Path";
    private static final String HOOKS = "com/akashic/thepatcher/module/angelica/AngelicaShaderPackFolderHooks";
    private static final String HOOK = "folderName";
    private static final String HOOK_DESC = "(Ljava/nio/file/Path;)Ljava/lang/String;";

    private static volatile boolean disabledLogged;

    @Override
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        final String target = transformedName == null ? name : transformedName;
        if (!SHADER_PACK.equals(target)) {
            return bytes;
        }
        if (Boolean.getBoolean(DISABLE_PROPERTY)) {
            if (!disabledLogged) {
                disabledLogged = true;
                BundleLog.info("Angelica shader pack folder fix DISABLED by -D" + DISABLE_PROPERTY + "=true.");
            }
            return bytes;
        }
        if (!RuntimeSide.isClient()) {
            return bytes;
        }
        try {
            final ClassNode node = new ClassNode();
            new ClassReader(bytes).accept(node, 0);
            final String result = patch(node);
            if (result != null) {
                BundleLog.warn("Angelica shader pack folder fix skipped for " + target + ": " + result
                    + "; original bytecode retained.");
                return bytes;
            }
            final ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(writer);
            BundleLog.info("Angelica shader pack folder fix applied: " + target + " now finds world0/ world-1/ world1/"
                + " folders inside .zip shader packs on Java " + System.getProperty("java.specification.version") + ".");
            return writer.toByteArray();
        } catch (ThreadDeath | VirtualMachineError fatal) {
            throw fatal;
        } catch (Throwable error) {
            BundleLog.error("Angelica shader pack folder fix rejected " + target + "; original bytecode retained.", error);
            return bytes;
        }
    }

    /** Returns null when the class was patched, otherwise why it was left alone. */
    static String patch(ClassNode node) {
        boolean pattern = false;
        InvokeDynamicInsnNode found = null;
        int count = 0;
        for (Object methodObject : node.methods) {
            final MethodNode method = (MethodNode) methodObject;
            for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (insn instanceof LdcInsnNode && PATTERN.equals(((LdcInsnNode) insn).cst)) {
                    pattern = true;
                }
                if (!(insn instanceof InvokeDynamicInsnNode)) {
                    continue;
                }
                final InvokeDynamicInsnNode indy = (InvokeDynamicInsnNode) insn;
                if (indy.bsm == null || !METAFACTORY_OWNER.equals(indy.bsm.getOwner()) || indy.bsmArgs.length < 3
                    || !(indy.bsmArgs[1] instanceof Handle)) {
                    continue;
                }
                final Handle impl = (Handle) indy.bsmArgs[1];
                if (impl.getOwner().equals(HOOKS)) {
                    return "already patched";
                }
                if (impl.getTag() == Opcodes.H_INVOKEINTERFACE && PATH.equals(impl.getOwner())
                    && "toString".equals(impl.getName()) && "()Ljava/lang/String;".equals(impl.getDesc())) {
                    count++;
                    if ("<init>".equals(method.name)) {
                        found = indy;
                    }
                }
            }
        }
        if (!pattern) {
            return "no \"" + PATTERN + "\" folder scan";
        }
        if (count != 1 || found == null) {
            return count + " Path::toString references (expected one, in a constructor)";
        }
        found.bsmArgs[1] = new Handle(Opcodes.H_INVOKESTATIC, HOOKS, HOOK, HOOK_DESC);
        return null;
    }
}
