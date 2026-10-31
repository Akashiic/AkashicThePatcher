package com.akashic.compatlab.tools;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/**
 * beta.48: Backhand state machine, return from a vanilla server behind the proxy.
 *
 * BackhandGuardHooks (unchanged from beta.35 to beta.47) on a Velocity switch vanilla lobby -> Forge backend:
 *   1. lobby JoinGame: beginTransitionLocked -> TRANSITION, awaitingJoinGame=false (no FML reset seen)
 *   2. backend FML mod list: captureRemoteModMap sees TRANSITION && !awaitingJoinGame, promotes to MODDED and
 *      sets consumedProofSerial = proofSerial (the proof is spent before the JoinGame it belongs to arrives)
 *   3. backend JoinGame: onJoinGamePacket finds no fresh proof (proofSerial == consumedProofSerial) and the type is
 *      not VANILLA/BUKKIT -> beginTransitionLocked again: TRANSITION, capability cleared, offhand off for the session
 * (latest.log 2026-10-31 00:33:13: "remote FML mod list captured: type=MODDED ... proof=6/6" then
 *  "join game/backend switch: type=TRANSITION ... offhandAllowed=false").
 *
 * Patch (fail-closed on the exact beta.35..beta.47 class bytes):
 *   + field   private static long akashic$proofJoinSerial      joinSerial when the last mod list was captured
 *   captureRemoteModMap: after proofSerial++ -> akashic$proofJoinSerial = joinSerial
 *   onJoinGamePacket:    the freshness test (remoteModListSeen && proofSerial > consumedProofSerial) becomes
 *                        akashic$freshProofLocked(): the same test, OR (connectionType == MODDED and the mod list was
 *                        captured after the previous JoinGame, i.e. akashic$proofJoinSerial == joinSerial - 1)
 *   Kill-switch -Dakashic.backhandProxyReturn.disable=true: akashic$freshProofLocked() = the beta.47 test.
 * Every other method is left byte-for-byte as it was (checked by the gate).
 */
public final class PatchBeta48Backhand {
    static final String ENTRY = "com/akashic/forbiddenbackhand/core/BackhandGuardHooks.class";
    static final String OWNER = "com/akashic/forbiddenbackhand/core/BackhandGuardHooks";
    static final String BASE_SHA = "af5980de5eb8a66bff03e0b05c39ed397c04c859ecc289ee8b2806d13200632b";
    static final String FIELD = "akashic$proofJoinSerial";
    static final String HELPER = "akashic$freshProofLocked";
    static final String KILL = "akashic.backhandProxyReturn.disable";

    static byte[] read(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        for (int n; (n = in.read(buffer)) != -1; ) out.write(buffer, 0, n);
        return out.toByteArray();
    }

    static String sha(byte[] data) throws Exception {
        StringBuilder sb = new StringBuilder();
        for (byte b : MessageDigest.getInstance("SHA-256").digest(data)) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    static MethodNode method(ClassNode node, String name, String desc) {
        MethodNode found = null;
        for (MethodNode m : node.methods)
            if (m.name.equals(name) && m.desc.equals(desc)) {
                if (found != null) throw new AssertionError("duplicate " + name);
                found = m;
            }
        if (found == null) throw new AssertionError("missing " + name + desc);
        return found;
    }

    static boolean isStatic(AbstractInsnNode insn, int opcode, String name, String desc) {
        if (insn.getOpcode() != opcode) return false;
        FieldInsnNode f = (FieldInsnNode) insn;
        return OWNER.equals(f.owner) && name.equals(f.name) && desc.equals(f.desc);
    }

    static void patchCapture(MethodNode m) {
        List<AbstractInsnNode> hits = new ArrayList<>();
        for (AbstractInsnNode insn : m.instructions.toArray())
            if (isStatic(insn, Opcodes.PUTSTATIC, "proofSerial", "J")) hits.add(insn);
        if (hits.size() != 1) throw new AssertionError("captureRemoteModMap proofSerial stores=" + hits.size());
        AbstractInsnNode increment = hits.get(0);  // proofSerial = proofSerial + 1
        AbstractInsnNode prev = increment.getPrevious();
        if (prev.getOpcode() != Opcodes.LADD || prev.getPrevious().getOpcode() != Opcodes.LCONST_1)
            throw new AssertionError("captureRemoteModMap proofSerial increment shape differs");
        InsnList add = new InsnList();
        add.add(new FieldInsnNode(Opcodes.GETSTATIC, OWNER, "joinSerial", "J"));
        add.add(new FieldInsnNode(Opcodes.PUTSTATIC, OWNER, FIELD, "J"));
        m.instructions.insert(increment, add);
    }

    static void patchJoin(MethodNode m) {
        AbstractInsnNode[] insns = m.instructions.toArray();
        int start = -1, end = -1;
        for (int i = 0; i < insns.length; i++) {
            if (start < 0 && isStatic(insns[i], Opcodes.PUTSTATIC, "joinSerial", "J")) start = i;
            if (start >= 0 && insns[i].getOpcode() == Opcodes.ISTORE && ((VarInsnNode) insns[i]).var == 3) { end = i; break; }
        }
        if (start < 0 || end < 0) throw new AssertionError("onJoinGamePacket freshness test not found");
        // exact shape of the removed test: remoteModListSeen && proofSerial > consumedProofSerial ? 1 : 0
        StringBuilder shape = new StringBuilder();
        Set<LabelNode> inside = new HashSet<>();
        for (int i = start + 1; i < end; i++) {
            AbstractInsnNode insn = insns[i];
            if (insn instanceof LabelNode) inside.add((LabelNode) insn);
            if (insn.getOpcode() >= 0) shape.append(insn.getOpcode()).append(' ');
        }
        String expected = "178 153 178 178 148 158 4 167 3 ";  // getstatic ifeq getstatic getstatic lcmp ifle iconst_1 goto iconst_0
        if (!expected.equals(shape.toString())) throw new AssertionError("onJoinGamePacket test shape: " + shape);
        int first = start + 1;
        while (insns[first].getOpcode() < 0) first++;
        if (!isStatic(insns[first], Opcodes.GETSTATIC, "remoteModListSeen", "Z"))
            throw new AssertionError("onJoinGamePacket test does not start with remoteModListSeen");
        for (AbstractInsnNode insn : insns) {
            int index = m.instructions.indexOf(insn);
            if (index > start && index < end) continue;
            if (insn instanceof JumpInsnNode && inside.contains(((JumpInsnNode) insn).label))
                throw new AssertionError("jump into the replaced test from outside");
        }
        if (m.tryCatchBlocks != null)
            for (TryCatchBlockNode t : m.tryCatchBlocks)
                if (inside.contains(t.start) || inside.contains(t.end) || inside.contains(t.handler))
                    throw new AssertionError("try/catch boundary inside the replaced test");
        // code and frames go; labels and line numbers stay (nothing jumps to them any more)
        for (int i = start + 1; i < end; i++)
            if (insns[i].getOpcode() >= 0 || insns[i] instanceof FrameNode) m.instructions.remove(insns[i]);
        m.instructions.insert(insns[start], new MethodInsnNode(Opcodes.INVOKESTATIC, OWNER, HELPER, "()Z", false));
    }

    static MethodNode helper() {
        MethodNode m = new MethodNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC, HELPER, "()Z", null, null);
        InsnList c = m.instructions;
        LabelNode modList = new LabelNode(), stale = new LabelNode(), no = new LabelNode();
        Object[] none = new Object[0];
        c.add(new FieldInsnNode(Opcodes.GETSTATIC, OWNER, "remoteModListSeen", "Z"));
        c.add(new JumpInsnNode(Opcodes.IFNE, modList));
        c.add(new InsnNode(Opcodes.ICONST_0));
        c.add(new InsnNode(Opcodes.IRETURN));
        c.add(modList);
        c.add(new FrameNode(Opcodes.F_NEW, 0, none, 0, none));
        c.add(new FieldInsnNode(Opcodes.GETSTATIC, OWNER, "proofSerial", "J"));
        c.add(new FieldInsnNode(Opcodes.GETSTATIC, OWNER, "consumedProofSerial", "J"));
        c.add(new InsnNode(Opcodes.LCMP));
        c.add(new JumpInsnNode(Opcodes.IFLE, stale));
        c.add(new InsnNode(Opcodes.ICONST_1));
        c.add(new InsnNode(Opcodes.IRETURN));
        c.add(stale);
        c.add(new FrameNode(Opcodes.F_NEW, 0, none, 0, none));
        c.add(new LdcInsnNode(KILL));
        c.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Boolean", "getBoolean", "(Ljava/lang/String;)Z", false));
        c.add(new JumpInsnNode(Opcodes.IFNE, no));
        c.add(new LdcInsnNode("MODDED"));
        c.add(new FieldInsnNode(Opcodes.GETSTATIC, OWNER, "connectionType", "Ljava/lang/String;"));
        c.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false));
        c.add(new JumpInsnNode(Opcodes.IFEQ, no));
        c.add(new FieldInsnNode(Opcodes.GETSTATIC, OWNER, FIELD, "J"));
        c.add(new FieldInsnNode(Opcodes.GETSTATIC, OWNER, "joinSerial", "J"));
        c.add(new InsnNode(Opcodes.LCONST_1));
        c.add(new InsnNode(Opcodes.LSUB));
        c.add(new InsnNode(Opcodes.LCMP));
        c.add(new JumpInsnNode(Opcodes.IFNE, no));
        c.add(new InsnNode(Opcodes.ICONST_1));
        c.add(new InsnNode(Opcodes.IRETURN));
        c.add(no);
        c.add(new FrameNode(Opcodes.F_NEW, 0, none, 0, none));
        c.add(new InsnNode(Opcodes.ICONST_0));
        c.add(new InsnNode(Opcodes.IRETURN));
        return m;
    }

    static byte[] patch(byte[] original) throws Exception {
        if (!BASE_SHA.equals(sha(original))) throw new AssertionError("BackhandGuardHooks is not the beta.35..beta.47 class: " + sha(original));
        ClassNode node = new ClassNode(Opcodes.ASM9);
        new ClassReader(original).accept(node, ClassReader.EXPAND_FRAMES);
        for (FieldNode f : node.fields) if (FIELD.equals(f.name)) throw new AssertionError("already patched");
        node.fields.add(new FieldNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC, FIELD, "J", null, null));
        patchCapture(method(node, "captureRemoteModMap", "(Ljava/util/Map;)V"));
        patchJoin(method(node, "onJoinGamePacket", "(Ljava/lang/Object;Ljava/lang/Object;)V"));
        node.methods.add(helper());
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("base.jar output.class");
        byte[] original;
        try (JarFile jar = new JarFile(args[0]); InputStream in = jar.getInputStream(jar.getJarEntry(ENTRY))) {
            original = read(in);
        }
        byte[] patched = patch(original);
        Path output = Paths.get(args[1]);
        Files.createDirectories(output.getParent());
        if (Files.exists(output)) throw new FileAlreadyExistsException(output.toString());
        Files.write(output, patched, StandardOpenOption.CREATE_NEW);
        System.out.println("BETA48_BACKHAND_PATCH PASS bytes=" + original.length + "->" + patched.length + " sha256=" + sha(patched));
    }
}
