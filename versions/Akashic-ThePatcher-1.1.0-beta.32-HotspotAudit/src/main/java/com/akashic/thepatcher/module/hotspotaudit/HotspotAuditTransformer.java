package com.akashic.thepatcher.module.hotspotaudit;

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

public final class HotspotAuditTransformer
    implements IClassTransformer {

    private static final String HOOKS =
        "com/akashic/thepatcher/module/startupaudit/FmlPhaseAuditHooks";

    private static final Target[] TARGETS =
        new Target[] {
            target(
                "com.mia.props.Props",
                "loadModels",
                "()V",
                "Decocraft.Props.loadModels"),

            target(
                "software.bernie.geckolib3.GeckoLib",
                "initialize",
                "()V",
                "GeckoLib.initialize"),

            target(
                "Reika.ChromatiCraft.Auxiliary.APIImpl.CCAPICore",
                "load",
                "()V",
                "ChromatiCraft.CCAPICore.load"),

            target(
                "Reika.ChromatiCraft.ChromaRecipes",
                "loadDictionary",
                "()V",
                "ChromatiCraft.ChromaRecipes.loadDictionary"),

            target(
                "Reika.ChromatiCraft.World.Dimension.ChromaDimensionManager",
                "initialize",
                "()V",
                "ChromatiCraft.ChromaDimensionManager.initialize"),

            target(
                "Reika.ChromatiCraft.Registry.ChromaStructures",
                "buildStructures",
                "()V",
                "ChromatiCraft.ChromaStructures.buildStructures"),

            target(
                "Reika.ChromatiCraft.ChromaRecipes",
                "addRecipes",
                "()V",
                "ChromatiCraft.ChromaRecipes.addRecipes"),

            target(
                "Reika.ChromatiCraft.Auxiliary.ChromaDescriptions",
                "loadData",
                "()V",
                "ChromatiCraft.ChromaDescriptions.loadData"),

            target(
                "Reika.ChromatiCraft.ChromatiCraft",
                "addTileEntities",
                "()V",
                "ChromatiCraft.addTileEntities"),

            target(
                "Reika.ChromatiCraft.ChromatiCraft",
                "addEntities",
                "()V",
                "ChromatiCraft.addEntities"),

            target(
                "noppes.npcs.config.LoadConfiguration",
                "init",
                "(Ljava/lang/String;)V",
                "CustomNPC.LoadConfiguration.init"),

            target(
                "noppes.npcs.enchants.EnchantInterface",
                "load",
                "()V",
                "CustomNPC.EnchantInterface.load"),

            target(
                "noppes.npcs.CustomItems",
                "load",
                "()V",
                "CustomNPC.CustomItems.load"),

            target(
                "noppes.npcs.CommonProxy",
                "load",
                "()V",
                "CustomNPC.CommonProxy.load"),

            target(
                "noppes.npcs.client.ClientProxy",
                "load",
                "()V",
                "CustomNPC.ClientProxy.load"),

            target(
                "mods.eln.config.ConfigHandler",
                "loadConfig",
                "(Lmods/eln/Eln;)V",
                "Eln.ConfigHandler.loadConfig"),

            target(
                "mods.eln.mqtt.MqttManager",
                "init",
                "()V",
                "Eln.MqttManager.init"),

            target(
                "com.kuba6000.mobsinfo.ClientProxy",
                "loadComplete",
                "(Lcpw/mods/fml/common/event/FMLLoadCompleteEvent;)V",
                "MobsInfo.ClientProxy.loadComplete"),

            target(
                "com.kuba6000.mobsinfo.loader.MobRecipeLoader",
                "generateMobRecipeMap",
                "()V",
                "MobsInfo.generateMobRecipeMap"),

            target(
                "com.kuba6000.mobsinfo.loader.VillagerTradesLoader",
                "generateVillagerTrades",
                "()V",
                "MobsInfo.generateVillagerTrades")
        };

    @Override
    public byte[] transform(
        String name,
        String transformedName,
        byte[] basicClass) {

        if (basicClass == null) return null;

        String effective =
            transformedName != null
                ? transformedName
                : name;

        List<Target> targets =
            targetsFor(effective);

        if (targets.isEmpty()
            && name != null
            && !name.equals(effective)) {

            targets =
                targetsFor(name);
        }

        if (targets.isEmpty()) {
            return basicClass;
        }

        try {
            byte[] result =
                patchClass(
                    basicClass,
                    targets);

            System.out.println(
                "[Akashic-ThePatcher/HotspotAudit] "
                    + "instrumented "
                    + effective
                    + " methods="
                    + targets.size());

            return result;
        } catch (Throwable failure) {
            System.err.println(
                "[Akashic-ThePatcher/HotspotAudit] "
                    + "PATCH REFUSED class="
                    + effective
                    + " reason="
                    + failure.getClass().getName()
                    + ": "
                    + failure.getMessage());

            System.setProperty(
                "akashic.hotspotAudit.failed."
                    + effective,
                "true");

            return basicClass;
        }
    }

    static byte[] patchClass(
        byte[] original,
        List<Target> targets) {

        ClassReader reader =
            new ClassReader(original);

        ClassNode node =
            new ClassNode();

        reader.accept(node, 0);

        for (Target target : targets) {
            MethodNode method =
                findUnique(
                    node,
                    target.method,
                    target.desc);

            if (containsHook(
                    method,
                    target.label)) {

                continue;
            }

            if ((method.access
                    & (Opcodes.ACC_ABSTRACT
                        | Opcodes.ACC_NATIVE))
                != 0) {

                throw new IllegalStateException(
                    "target is abstract/native "
                        + target.method
                        + target.desc);
            }

            int returns =
                countReturns(method);

            if (returns < 1) {
                throw new IllegalStateException(
                    "target has no normal return "
                        + target.method
                        + target.desc);
            }

            inject(
                method,
                target.label);
        }

        ClassWriter writer =
            new ClassWriter(
                reader,
                ClassWriter.COMPUTE_MAXS);

        node.accept(writer);

        byte[] result =
            writer.toByteArray();

        verifyPatched(
            result,
            targets);

        return result;
    }

    static List<Target> targetsForTest(
        String className) {

        return targetsFor(className);
    }

    private static List<Target> targetsFor(
        String className) {

        List<Target> found =
            new ArrayList<Target>();

        if (className == null) {
            return found;
        }

        for (Target target : TARGETS) {
            if (target.className.equals(
                    className)) {

                found.add(target);
            }
        }

        return found;
    }

    private static void inject(
        MethodNode method,
        String label) {

        InsnList head =
            new InsnList();

        head.add(
            new LdcInsnNode(label));

        head.add(
            new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                HOOKS,
                "spanEnter",
                "(Ljava/lang/String;)V",
                false));

        method.instructions.insert(head);

        List<AbstractInsnNode> returns =
            new ArrayList<AbstractInsnNode>();

        for (AbstractInsnNode insn =
                 method.instructions.getFirst();
             insn != null;
             insn = insn.getNext()) {

            int opcode =
                insn.getOpcode();

            if (opcode == Opcodes.RETURN
                || opcode == Opcodes.IRETURN
                || opcode == Opcodes.LRETURN
                || opcode == Opcodes.FRETURN
                || opcode == Opcodes.DRETURN
                || opcode == Opcodes.ARETURN) {

                returns.add(insn);
            }
        }

        for (AbstractInsnNode ret : returns) {
            InsnList exit =
                new InsnList();

            exit.add(
                new LdcInsnNode(label));

            exit.add(
                new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    HOOKS,
                    "spanExit",
                    "(Ljava/lang/String;)V",
                    false));

            method.instructions.insertBefore(
                ret,
                exit);
        }
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

    private static boolean containsHook(
        MethodNode method,
        String label) {

        boolean labelSeen = false;
        boolean enterSeen = false;

        for (AbstractInsnNode insn =
                 method.instructions.getFirst();
             insn != null;
             insn = insn.getNext()) {

            if (insn instanceof LdcInsnNode
                && label.equals(
                    ((LdcInsnNode) insn).cst)) {

                labelSeen = true;
            }

            if (insn instanceof MethodInsnNode) {
                MethodInsnNode call =
                    (MethodInsnNode) insn;

                if (HOOKS.equals(call.owner)
                    && "spanEnter".equals(call.name)
                    && "(Ljava/lang/String;)V".equals(
                        call.desc)) {

                    enterSeen = true;
                }
            }
        }

        return labelSeen && enterSeen;
    }

    private static void verifyPatched(
        byte[] bytes,
        List<Target> targets) {

        ClassNode node =
            new ClassNode();

        new ClassReader(bytes)
            .accept(node, 0);

        for (Target target : targets) {
            MethodNode method =
                findUnique(
                    node,
                    target.method,
                    target.desc);

            int enters =
                countHook(
                    method,
                    "spanEnter");

            int exits =
                countHook(
                    method,
                    "spanExit");

            int returns =
                countReturns(method);

            if (enters != 1
                || exits != returns) {

                throw new IllegalStateException(
                    "hook verification failed "
                        + target.label
                        + " enters="
                        + enters
                        + " exits="
                        + exits
                        + " returns="
                        + returns);
            }
        }
    }

    private static int countHook(
        MethodNode method,
        String hook) {

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

            if (HOOKS.equals(call.owner)
                && hook.equals(call.name)
                && "(Ljava/lang/String;)V".equals(
                    call.desc)) {

                count++;
            }
        }

        return count;
    }

    private static int countReturns(
        MethodNode method) {

        int count = 0;

        for (AbstractInsnNode insn =
                 method.instructions.getFirst();
             insn != null;
             insn = insn.getNext()) {

            int opcode =
                insn.getOpcode();

            if (opcode == Opcodes.RETURN
                || opcode == Opcodes.IRETURN
                || opcode == Opcodes.LRETURN
                || opcode == Opcodes.FRETURN
                || opcode == Opcodes.DRETURN
                || opcode == Opcodes.ARETURN) {

                count++;
            }
        }

        return count;
    }

    private static Target target(
        String className,
        String method,
        String desc,
        String label) {

        return new Target(
            className,
            method,
            desc,
            label);
    }

    static final class Target {
        final String className;
        final String method;
        final String desc;
        final String label;

        Target(
            String className,
            String method,
            String desc,
            String label) {

            this.className = className;
            this.method = method;
            this.desc = desc;
            this.label = label;
        }
    }
}
