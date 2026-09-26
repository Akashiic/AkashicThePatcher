/*
 * Decompiled with CFR 0.152.
 *
 * Could not load the following classes:
 *  com.akashic.forbiddenbackhand.config.PatchConfig
 *  com.akashic.forbiddenbackhand.core.TransformerState
 *  com.akashic.forbiddenbackhand.util.PatchLog
 *  net.minecraft.launchwrapper.IClassTransformer
 *  org.objectweb.asm.ClassReader
 *  org.objectweb.asm.ClassVisitor
 *  org.objectweb.asm.ClassWriter
 *  org.objectweb.asm.tree.AbstractInsnNode
 *  org.objectweb.asm.tree.ClassNode
 *  org.objectweb.asm.tree.InsnList
 *  org.objectweb.asm.tree.MethodInsnNode
 *  org.objectweb.asm.tree.MethodNode
 *  org.objectweb.asm.tree.TypeInsnNode
 *  org.objectweb.asm.tree.VarInsnNode
 *
 * RECOVERY NOTE: This is reconstructed Java from the installed beta.35 class file,
 * not the original human-authored source. See ../README.md for provenance and limits.
 */
package com.akashic.forbiddenbackhand.core;

import com.akashic.forbiddenbackhand.config.PatchConfig;
import com.akashic.forbiddenbackhand.core.TransformerState;
import com.akashic.forbiddenbackhand.util.PatchLog;
import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

public final class BackhandBackendTransformer
implements IClassTransformer {
    private static final String BACKHAND_UTILS = "xonin.backhand.api.core.BackhandUtils";
    private static final String PLAYER_CONTROLLER_CLASS = "net.minecraft.client.multiplayer.PlayerControllerMP";
    private static final String NET_HANDLER_CLASS = "net.minecraft.client.network.NetHandlerPlayClient";
    private static final String FML_CLIENT_STATE_PREFIX = "cpw.mods.fml.common.network.handshake.FMLHandshakeClientState$";
    private static final String BACKHAND_METHOD_DESC = "(Lnet/minecraft/entity/player/EntityPlayer;ZLjava/util/function/BooleanSupplier;)Z";
    private static final String HOOKS = "com/akashic/forbiddenbackhand/core/BackhandGuardHooks";
    private static final String PLAYER_CONTROLLER = "net/minecraft/client/multiplayer/PlayerControllerMP";
    private static final String NET_HANDLER = "bjb";
    private static final String BOOLEAN_SUPPLIER = "java/util/function/BooleanSupplier";
    private static final String FML_NETWORK_HANDLER = "cpw/mods/fml/common/network/internal/FMLNetworkHandler";
    private static final String HANDSHAKE_RESET = "cpw/mods/fml/common/network/handshake/FMLHandshakeMessage$HandshakeReset";
    private static final String JOIN_GAME_PACKET = "net/minecraft/network/play/server/S01PacketJoinGame";
    private static final String PACKET_DESC = "(Lft;)V";

    public byte[] transform(String string, String string2, byte[] byArray) {
        if (byArray == null) {
            return null;
        }
        String string3 = string2 == null ? string : string2;
        try {
            if (!PatchConfig.get().enabled) {
                return byArray;
            }
            if (BACKHAND_UTILS.equals(string3)) {
                return this.patchBackhandUtils(byArray);
            }
            if (PLAYER_CONTROLLER_CLASS.equals(string3)) {
                return this.patchPlayerController(byArray);
            }
            if (NET_HANDLER_CLASS.equals(string3)) {
                return this.patchNetHandlerPlayClient(byArray);
            }
            if (string3 != null && string3.startsWith(FML_CLIENT_STATE_PREFIX)) {
                return this.patchFmlHandshakeState(byArray, string3);
            }
        }
        catch (ThreadDeath threadDeath) {
            throw threadDeath;
        }
        catch (VirtualMachineError virtualMachineError) {
            throw virtualMachineError;
        }
        catch (Throwable throwable) {
            PatchLog.error((String)("Transformation failed for " + string3 + "; original bytes will be used."), (Throwable)throwable);
        }
        return byArray;
    }

    private byte[] patchBackhandUtils(byte[] byArray) {
        ClassReader classReader = new ClassReader(byArray);
        ClassNode classNode = new ClassNode(327680);
        classReader.accept((ClassVisitor)classNode, 0);
        MethodNode methodNode = null;
        for (MethodNode methodNode2 : classNode.methods) {
            if (!"useOffhandItem".equals(methodNode2.name) || !BACKHAND_METHOD_DESC.equals(methodNode2.desc)) continue;
            methodNode = methodNode2;
            break;
        }
        if (methodNode == null) {
            PatchLog.warn((String)"BackhandUtils.useOffhandItem(EntityPlayer, boolean, BooleanSupplier) was not found; the backend compatibility guard was not installed.");
            return byArray;
        }
        if (BackhandBackendTransformer.containsHook(methodNode, "getAsBoolean")) {
            TransformerState.markBackhand((int)BackhandBackendTransformer.countHook(methodNode, "syncCurrentPlayItem"), (int)BackhandBackendTransformer.countHook(methodNode, "getAsBoolean"));
            return byArray;
        }
        int n = 0;
        int n2 = 0;
        for (AbstractInsnNode abstractInsnNode : methodNode.instructions.toArray()) {
            if (!(abstractInsnNode instanceof MethodInsnNode)) continue;
            MethodInsnNode methodInsnNode = (MethodInsnNode)abstractInsnNode;
            if (PLAYER_CONTROLLER.equals(methodInsnNode.owner) && "()V".equals(methodInsnNode.desc) && ("syncCurrentPlayItem".equals(methodInsnNode.name) || "func_78750_j".equals(methodInsnNode.name) || "k".equals(methodInsnNode.name))) {
                methodInsnNode.setOpcode(184);
                methodInsnNode.owner = HOOKS;
                methodInsnNode.name = "syncCurrentPlayItem";
                methodInsnNode.desc = "(Ljava/lang/Object;)V";
                methodInsnNode.itf = false;
                ++n;
                continue;
            }
            if (!BOOLEAN_SUPPLIER.equals(methodInsnNode.owner) || !"getAsBoolean".equals(methodInsnNode.name) || !"()Z".equals(methodInsnNode.desc)) continue;
            methodInsnNode.setOpcode(184);
            methodInsnNode.owner = HOOKS;
            methodInsnNode.name = "getAsBoolean";
            methodInsnNode.desc = "(Ljava/lang/Object;)Z";
            methodInsnNode.itf = false;
            ++n2;
        }
        if (n2 != 1 || n != 3) {
            PatchLog.warn((String)("Backhand 1.8.13 bytecode shape differed from the validated target: sync redirects=" + n + ", action redirects=" + n2 + ". Original bytes will be kept to avoid a partial patch."));
            return byArray;
        }
        byte[] byArray2 = BackhandBackendTransformer.write(classReader, classNode);
        TransformerState.markBackhand((int)n, (int)n2);
        PatchLog.info((String)"Patched BackhandUtils: guarded 3 bytecode sync sites and 1 offhand action boundary.");
        return byArray2;
    }

    private byte[] patchPlayerController(byte[] byArray) {
        ClassReader classReader = new ClassReader(byArray);
        ClassNode classNode = new ClassNode(327680);
        classReader.accept((ClassVisitor)classNode, 0);
        int n = 0;
        boolean bl = false;
        for (MethodNode methodNode : classNode.methods) {
            if (!"()V".equals(methodNode.desc) || !"syncCurrentPlayItem".equals(methodNode.name) && !"func_78750_j".equals(methodNode.name) && !"k".equals(methodNode.name)) continue;
            if (BackhandBackendTransformer.containsHook(methodNode, "sendPacket")) {
                n += BackhandBackendTransformer.countHook(methodNode, "sendPacket");
                continue;
            }
            for (AbstractInsnNode abstractInsnNode : methodNode.instructions.toArray()) {
                if (!(abstractInsnNode instanceof MethodInsnNode)) continue;
                MethodInsnNode methodInsnNode = (MethodInsnNode)abstractInsnNode;
                if (!NET_HANDLER.equals(methodInsnNode.owner) || !PACKET_DESC.equals(methodInsnNode.desc) || !"addToSendQueue".equals(methodInsnNode.name) && !"func_147297_a".equals(methodInsnNode.name) && !"a".equals(methodInsnNode.name)) continue;
                methodInsnNode.setOpcode(184);
                methodInsnNode.owner = HOOKS;
                methodInsnNode.name = "sendPacket";
                methodInsnNode.desc = "(Ljava/lang/Object;Ljava/lang/Object;)V";
                methodInsnNode.itf = false;
                ++n;
                bl = true;
            }
        }
        if (n != 1) {
            PatchLog.warn((String)("PlayerControllerMP held-item packet boundary differed from the validated target: redirects=" + n + ". Original bytes will be kept."));
            return byArray;
        }
        TransformerState.markHeldItemPacketGuard((int)n);
        if (!bl) {
            return byArray;
        }
        PatchLog.info((String)"Patched PlayerControllerMP: installed final C09 held-item packet guard.");
        return BackhandBackendTransformer.write(classReader, classNode);
    }

    private byte[] patchNetHandlerPlayClient(byte[] byArray) {
        ClassReader classReader = new ClassReader(byArray);
        ClassNode classNode = new ClassNode(327680);
        classReader.accept((ClassVisitor)classNode, 0);
        int n = 0;
        boolean bl = false;
        for (MethodNode methodNode : classNode.methods) {
            if (!"handleJoinGame".equals(methodNode.name) && !"func_147282_a".equals(methodNode.name) && !"a".equals(methodNode.name) || !methodNode.desc.startsWith("(Lhd;") || !methodNode.desc.endsWith(")V")) continue;
            if (BackhandBackendTransformer.containsHook(methodNode, "onJoinGamePacket")) {
                n += BackhandBackendTransformer.countHook(methodNode, "onJoinGamePacket");
                continue;
            }
            InsnList insnList = new InsnList();
            insnList.add((AbstractInsnNode)new VarInsnNode(25, 0));
            insnList.add((AbstractInsnNode)new VarInsnNode(25, 1));
            insnList.add((AbstractInsnNode)new MethodInsnNode(184, HOOKS, "onJoinGamePacket", "(Ljava/lang/Object;Ljava/lang/Object;)V", false));
            methodNode.instructions.insert(insnList);
            ++n;
            bl = true;
        }
        if (n != 1) {
            PatchLog.warn((String)("NetHandlerPlayClient JoinGame boundary differed from the validated target: hooks=" + n + ". Original bytes will be kept."));
            return byArray;
        }
        TransformerState.markJoinGameGuard((int)n);
        if (!bl) {
            return byArray;
        }
        PatchLog.info((String)"Patched NetHandlerPlayClient: backend JoinGame now invalidates stale capability.");
        return BackhandBackendTransformer.write(classReader, classNode);
    }

    private byte[] patchFmlHandshakeState(byte[] byArray, String string) {
        ClassReader classReader = new ClassReader(byArray);
        ClassNode classNode = new ClassNode(327680);
        classReader.accept((ClassVisitor)classNode, 0);
        boolean bl = false;
        for (MethodNode methodNode : classNode.methods) {
            InsnList insnList;
            boolean bl2 = false;
            boolean bl3 = false;
            for (AbstractInsnNode abstractInsnNode : methodNode.instructions.toArray()) {
                MethodInsnNode methodInsnNode;
                if (abstractInsnNode instanceof MethodInsnNode) {
                    methodInsnNode = (MethodInsnNode)abstractInsnNode;
                    if (!FML_NETWORK_HANDLER.equals(methodInsnNode.owner) || !"checkModList".equals(methodInsnNode.name)) continue;
                    bl2 = true;
                    continue;
                }
                if (!(abstractInsnNode instanceof TypeInsnNode)) continue;
                methodInsnNode = (TypeInsnNode)abstractInsnNode;
                if (abstractInsnNode.getOpcode() != 193 || !HANDSHAKE_RESET.equals(methodInsnNode.desc)) continue;
                bl3 = true;
            }
            if (bl2 && !BackhandBackendTransformer.containsHook(methodNode, "captureRemoteModList")) {
                insnList = new InsnList();
                insnList.add((AbstractInsnNode)new VarInsnNode(25, 2));
                insnList.add((AbstractInsnNode)new MethodInsnNode(184, HOOKS, "captureRemoteModList", "(Ljava/lang/Object;)V", false));
                methodNode.instructions.insert(insnList);
                TransformerState.markModListCapture();
                bl = true;
            }
            if (!bl3 || BackhandBackendTransformer.containsHook(methodNode, "captureHandshakeStateMessage")) continue;
            insnList = new InsnList();
            insnList.add((AbstractInsnNode)new VarInsnNode(25, 2));
            insnList.add((AbstractInsnNode)new MethodInsnNode(184, HOOKS, "captureHandshakeStateMessage", "(Ljava/lang/Object;)V", false));
            methodNode.instructions.insert(insnList);
            TransformerState.markHandshakeReset();
            bl = true;
        }
        if (!bl) {
            return byArray;
        }
        Object object = BackhandBackendTransformer.write(classReader, classNode);
        PatchLog.info((String)("Installed FML handoff capability capture in " + string + '.'));
        return object;
    }

    private static byte[] write(ClassReader classReader, ClassNode classNode) {
        ClassWriter classWriter = new ClassWriter(classReader, 1);
        classNode.accept((ClassVisitor)classWriter);
        return classWriter.toByteArray();
    }

    private static boolean containsHook(MethodNode methodNode, String string) {
        return BackhandBackendTransformer.countHook(methodNode, string) > 0;
    }

    private static int countHook(MethodNode methodNode, String string) {
        int n = 0;
        for (AbstractInsnNode abstractInsnNode : methodNode.instructions.toArray()) {
            if (!(abstractInsnNode instanceof MethodInsnNode)) continue;
            MethodInsnNode methodInsnNode = (MethodInsnNode)abstractInsnNode;
            if (!HOOKS.equals(methodInsnNode.owner) || !string.equals(methodInsnNode.name)) continue;
            ++n;
        }
        return n;
    }
}
