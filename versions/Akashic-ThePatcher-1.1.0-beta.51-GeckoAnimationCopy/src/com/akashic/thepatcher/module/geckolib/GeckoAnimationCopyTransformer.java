package com.akashic.thepatcher.module.geckolib;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.launchwrapper.IClassTransformer;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicVerifier;

import com.akashic.thepatcher.util.BundleLog;

/**
 * Frame spikes of GeckoLib models with long animations (beta.51). Both sides (the class also loads on the server).
 *
 * GeckoLib Unofficial 1.7.10 1.0.4: AnimationController.setAnimation takes a deep copy of every Animation it starts,
 * to give the copy its own loop type: software.bernie.geckolib3.core.builder.Animation.copy(Animation) writes the
 * whole animation with ObjectOutputStream, turns the bytes into a Base64 string, decodes it and reads it back with
 * ObjectInputStream. The port also restarts a looping animation through setAnimation at the end of every loop, so a
 * model pays that copy at every turn of every looping animation, on the client thread. Measured in a real TEOF client
 * with the AkashicFishing 3D rods (Kunftig): 2,952-keyframe aura loop 81 ms and 18 MB per copy, every 4 seconds; 36 ms
 * for the 239-keyframe fight loop, every 2 seconds. A field copy takes under a microsecond.
 *
 * Edit: copy's body becomes a field copy: null stays null, else a new Animation (its no-argument constructor) with every
 * instance field of the original assigned. The copy has its own fields (the loop type that setAnimation sets on it), and
 * shares the keyframe lists, which GeckoLib only reads at run time (AnimationController.process already swaps the copy
 * for the shared cached Animation from the model on the next frame). Kill-switch -Dakashic.geckoAnimationCopy.disable=true.
 *
 * Fails closed: the class must have a no-argument constructor and copy must have the exact descriptor, be static, and
 * call ObjectOutputStream.writeObject and ObjectInputStream.readObject (the serialization copy); the new body must
 * pass the ASM verifier. Otherwise the class is kept as it is.
 */
public final class GeckoAnimationCopyTransformer implements IClassTransformer {

    static final String TARGET = "software.bernie.geckolib3.core.builder.Animation";
    static final String OWNER = "software/bernie/geckolib3/core/builder/Animation";
    static final String COPY = "copy";
    static final String COPY_DESC = "(L" + OWNER + ";)L" + OWNER + ";";
    static final String DISABLE = "akashic.geckoAnimationCopy.disable";

    private static volatile boolean offLogged;

    /**
     * Called by AkashicThePatcherLoadingPlugin.getASMTransformerClass right after beta.50's recipe index registers, so
     * this transformer is the last one in the list (both sides).
     */
    public static void register(List<String> transformers) {
        transformers.add(GeckoAnimationCopyTransformer.class.getName());
    }

    @Override
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        final String target = transformedName == null ? name : transformedName;
        if (!TARGET.equals(target)) {
            return bytes;
        }
        if (Boolean.getBoolean(DISABLE)) {
            if (!offLogged) {
                offLogged = true;
                BundleLog.info("GeckoLib animation copy fix disabled by -D" + DISABLE + "=true.");
            }
            return bytes;
        }
        try {
            final ClassNode node = new ClassNode();
            new ClassReader(bytes).accept(node, 0);
            final String result = patch(node);
            if (!result.startsWith("ok")) {
                BundleLog.warn("GeckoLib animation copy fix skipped for " + target + ": " + result
                    + "; original bytecode retained.");
                return bytes;
            }
            final ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(writer);
            BundleLog.info("GeckoLib animation copy fix applied: " + target + " (" + result.substring(3) + ").");
            return writer.toByteArray();
        } catch (ThreadDeath | VirtualMachineError fatal) {
            throw fatal;
        } catch (Throwable error) {
            BundleLog.error("GeckoLib animation copy fix rejected " + target + "; original bytecode retained.", error);
            return bytes;
        }
    }

    /** "ok <detail>" when patched, else why not. */
    static String patch(ClassNode node) throws Exception {
        if (node.version < Opcodes.V1_6) {
            return "unexpected class version " + node.version;
        }
        MethodNode copy = null;
        boolean constructor = false;
        for (Object o : node.methods) {
            final MethodNode m = (MethodNode) o;
            if (COPY.equals(m.name) && COPY_DESC.equals(m.desc)) {
                if (copy != null) {
                    return "copy found twice";
                }
                copy = m;
            }
            if ("<init>".equals(m.name) && "()V".equals(m.desc) && (m.access & Opcodes.ACC_PUBLIC) != 0) {
                constructor = true;
            }
        }
        if (copy == null) {
            return "no copy" + COPY_DESC;
        }
        if ((copy.access & Opcodes.ACC_STATIC) == 0) {
            return "copy is not static";
        }
        if (!constructor) {
            return "no public no-argument constructor";
        }
        boolean writes = false;
        boolean reads = false;
        for (AbstractInsnNode insn = copy.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof MethodInsnNode) {
                final MethodInsnNode call = (MethodInsnNode) insn;
                writes |= "java/io/ObjectOutputStream".equals(call.owner) && "writeObject".equals(call.name);
                reads |= "java/io/ObjectInputStream".equals(call.owner) && "readObject".equals(call.name);
            }
        }
        if (!writes || !reads) {
            return "copy is not the serialization copy (writeObject=" + writes + ", readObject=" + reads + ")";
        }
        final List<FieldNode> fields = new ArrayList<FieldNode>();
        for (Object o : node.fields) {
            final FieldNode f = (FieldNode) o;
            if ((f.access & Opcodes.ACC_STATIC) == 0) {
                fields.add(f);
            }
        }
        if (fields.isEmpty()) {
            return "no instance fields";
        }

        // if (original == null) return null; Animation c = new Animation(); c.f = original.f ...; return c;
        final InsnList body = new InsnList();
        final LabelNode notNull = new LabelNode(new Label());
        body.add(new VarInsnNode(Opcodes.ALOAD, 0));
        body.add(new JumpInsnNode(Opcodes.IFNONNULL, notNull));
        body.add(new InsnNode(Opcodes.ACONST_NULL));
        body.add(new InsnNode(Opcodes.ARETURN));
        body.add(notNull);
        body.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
        body.add(new TypeInsnNode(Opcodes.NEW, OWNER));
        body.add(new InsnNode(Opcodes.DUP));
        body.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, OWNER, "<init>", "()V", false));
        body.add(new VarInsnNode(Opcodes.ASTORE, 1));
        for (FieldNode f : fields) {
            body.add(new VarInsnNode(Opcodes.ALOAD, 1));
            body.add(new VarInsnNode(Opcodes.ALOAD, 0));
            body.add(new FieldInsnNode(Opcodes.GETFIELD, OWNER, f.name, f.desc));
            body.add(new FieldInsnNode(Opcodes.PUTFIELD, OWNER, f.name, f.desc));
        }
        body.add(new VarInsnNode(Opcodes.ALOAD, 1));
        body.add(new InsnNode(Opcodes.ARETURN));

        copy.instructions = body;
        copy.tryCatchBlocks.clear();
        copy.localVariables = null;
        copy.visibleLocalVariableAnnotations = null;
        copy.invisibleLocalVariableAnnotations = null;
        copy.exceptions.clear();
        copy.maxLocals = 2;
        copy.maxStack = 3;
        new Analyzer(new BasicVerifier()).analyze(node.name, copy);
        return "ok field copy of " + fields.size() + " field(s)";
    }
}
