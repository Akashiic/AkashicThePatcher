package com.akashic.thepatcher.module.hologram;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.launchwrapper.IClassTransformer;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import com.akashic.thepatcher.core.RuntimeSide;
import com.akashic.thepatcher.util.BundleLog;

import cpw.mods.fml.common.asm.transformers.deobf.FMLDeobfuscatingRemapper;

/**
 * Hologram fast path (beta.43): see com.akashic.hologramfastpath.HologramFastPath for what and why.
 *
 * Head hooks (each method must exist exactly once with the vanilla descriptor, else the class
 * is left untouched and logged):
 *   EntityFireball.onUpdate                 -> tickVehicle   (return when it answers true)
 *   EntityHorse/EntityItem/EntitySlime.onUpdate -> tickLine  (return when it answers true)
 *   Entity.isInRangeToRender3d              -> leavesEntityPipeline (return false)
 *   RenderManager.doRenderEntity            -> leavesEntityPipeline (return true, nothing drawn)
 *   RendererLivingEntity.passSpecialRender  -> drawsOwnName (return)
 *   RenderGlobal.renderEntities             -> renderLines(camera, partialTicks), right before the
 *                                              single LDC "blockentities" (end of the entity loop)
 * EntityHorse also gains the field {@code public boolean akashic$hologram} (cached detection).
 *
 * The render hooks only act once RenderGlobal is patched ({@link #rendererInstalled}): if the
 * line renderer cannot be installed, hologram entities keep the vanilla path (no hologram is
 * ever hidden without being drawn). Must run after the bundled ForbiddenHologramTransformer,
 * which wraps the returns of Entity.isInRangeToRender3d (this hook returns before them).
 *
 * Kill-switch: -Dakashic.hologramFastPath.disable=true
 */
public final class HologramFastPathTransformer implements IClassTransformer {

    public static final String DISABLE_PROPERTY = "akashic.hologramFastPath.disable";

    static final String ENTITY = "net.minecraft.entity.Entity";
    static final String FIREBALL = "net.minecraft.entity.projectile.EntityFireball";
    static final String HORSE = "net.minecraft.entity.passive.EntityHorse";
    static final String ITEM = "net.minecraft.entity.item.EntityItem";
    static final String SLIME = "net.minecraft.entity.monster.EntitySlime";
    static final String RENDER_MANAGER = "net.minecraft.client.renderer.entity.RenderManager";
    static final String RENDERER_LIVING = "net.minecraft.client.renderer.entity.RendererLivingEntity";
    static final String RENDER_GLOBAL = "net.minecraft.client.renderer.RenderGlobal";

    private static final String HOOKS = "com/akashic/hologramfastpath/HologramFastPath";
    private static final String E = "Lnet/minecraft/entity/Entity;";
    static final String FIELD = "akashic$hologram";

    /** True once RenderGlobal.renderEntities calls HologramFastPath.renderLines. */
    public static volatile boolean rendererInstalled;
    private static volatile Boolean enabled;

    /**
     * Called by AkashicThePatcherLoadingPlugin.getASMTransformerClass right before it logs the list, so this
     * transformer comes after every bundled one (ForbiddenHologramTransformer included) on the client only.
     */
    public static void register(List<String> transformers) {
        if (RuntimeSide.isClient()) {
            transformers.add(HologramFastPathTransformer.class.getName());
        }
    }

    @Override
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        final String target = transformedName == null ? name : transformedName;
        final int kind = kind(target);
        if (kind < 0 || !enabled()) {
            return bytes;
        }
        try {
            final ClassNode node = new ClassNode();
            new ClassReader(bytes).accept(node, 0);
            final String result = patch(kind, node);
            if (result != null) {
                BundleLog.warn("Hologram fast path skipped " + target + ": " + result + "; original bytecode retained.");
                return bytes;
            }
            final ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(writer);
            final byte[] out = writer.toByteArray();
            if (kind == 6) {
                rendererInstalled = true;
            }
            BundleLog.info("Hologram fast path applied: " + target + " (" + DESCRIPTION[kind] + ").");
            return out;
        } catch (ThreadDeath | VirtualMachineError fatal) {
            throw fatal;
        } catch (Throwable error) {
            BundleLog.error("Hologram fast path rejected " + target + "; original bytecode retained.", error);
            return bytes;
        }
    }

    private static final String[] DESCRIPTION = {
        "line vehicles keep still", "text lines keep still", "item lines keep still, still spin",
        "touch lines keep still", "hologram carriers out of the range check",
        "hologram carriers draw nothing", "hologram text lines drawn in one pass",
        "no second name tag for hologram text lines"};

    static int kind(String target) {
        switch (target) {
            case FIREBALL: return 0;
            case HORSE: return 1;
            case ITEM: return 2;
            case SLIME: return 3;
            case ENTITY: return 4;
            case RENDER_MANAGER: return 5;
            case RENDER_GLOBAL: return 6;
            case RENDERER_LIVING: return 7;
            default: return -1;
        }
    }

    static boolean enabled() {
        Boolean current = enabled;
        if (current == null) {
            if (Boolean.getBoolean(DISABLE_PROPERTY)) {
                BundleLog.info("Hologram fast path off: disabled by -D" + DISABLE_PROPERTY + "=true.");
                current = Boolean.FALSE;
            } else if (!RuntimeSide.isClient()) {
                current = Boolean.FALSE;
            } else {
                BundleLog.info("Hologram fast path armed: 1.7 hologram carriers (horse/item/slime on a wither skull) "
                    + "stop ticking and their text is drawn in one pass.");
                current = Boolean.TRUE;
            }
            enabled = current;
        }
        return current;
    }

    /** Returns null when patched, otherwise why the class was left alone. */
    static String patch(int kind, ClassNode node) {
        switch (kind) {
            case 0:
                return head(node, "()V", "onUpdate", "func_70071_h_",
                    returnIf("tickVehicle", "(Lnet/minecraft/entity/projectile/EntityFireball;)Z", Opcodes.RETURN));
            case 1: {
                for (Object f : node.fields) {
                    if (FIELD.equals(((FieldNode) f).name)) return "already patched";
                }
                final String result = head(node, "()V", "onUpdate", "func_70071_h_",
                    returnIf("tickLine", "(" + E + ")Z", Opcodes.RETURN));
                if (result == null) {
                    addField(node, new FieldNode(Opcodes.ACC_PUBLIC, FIELD, "Z", null, null));
                }
                return result;
            }
            case 2:
            case 3:
                return head(node, "()V", "onUpdate", "func_70071_h_",
                    returnIf("tickLine", "(" + E + ")Z", Opcodes.RETURN));
            case 4:
                return head(node, "(DDD)Z", "isInRangeToRender3d", "func_145770_h",
                    returnIf("leavesEntityPipeline", "(" + E + ")Z", Opcodes.ICONST_0));
            case 5:
                return head(node, "(" + E + "DDDFFZ)Z", "doRenderEntity", "func_147939_a",
                    returnIf("leavesEntityPipeline", "(" + E + ")Z", Opcodes.ICONST_1, 1));
            case 6:
                return renderEntities(node);
            case 7:
                return head(node, "(Lnet/minecraft/entity/EntityLivingBase;DDD)V", "passSpecialRender", "func_77033_b",
                    returnIf("drawsOwnName", "(" + E + ")Z", Opcodes.RETURN, 1));
            default:
                return "unknown target";
        }
    }

    private interface Prologue {
        InsnList build(MethodNode method);
    }

    private static Prologue returnIf(String hook, String hookDesc, int returnOpcode) {
        return returnIf(hook, hookDesc, returnOpcode, 0);
    }

    /** if (HologramFastPath.hook(local)) return [0|1]; */
    private static Prologue returnIf(final String hook, final String hookDesc, final int returnOpcode, final int local) {
        return method -> {
            final InsnList list = new InsnList();
            final LabelNode go = new LabelNode();
            list.add(new VarInsnNode(Opcodes.ALOAD, local));
            list.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, hook, hookDesc, false));
            list.add(new JumpInsnNode(Opcodes.IFEQ, go));
            if (returnOpcode == Opcodes.RETURN) {
                list.add(new InsnNode(Opcodes.RETURN));
            } else {
                list.add(new InsnNode(returnOpcode));
                list.add(new InsnNode(Opcodes.IRETURN));
            }
            list.add(go);
            return list;
        };
    }

    private static String head(ClassNode node, String desc, String mcp, String srg, Prologue prologue) {
        final List<MethodNode> found = methods(node, desc, mcp, srg);
        if (found.size() != 1) {
            return found.size() + " " + mcp + desc + " methods, expected 1";
        }
        final MethodNode method = found.get(0);
        if (calls(method, HOOKS)) {
            return "already patched";
        }
        final InsnList list = prologue.build(method);
        // The prologue ends on a branch target at the method's first instruction: locals are the
        // arguments, the stack is empty, i.e. F_SAME. Classes with stack map frames (major >= 50) need it
        // there, unless the original code already has a frame at that offset.
        if (node.version >= Opcodes.V1_6 && !(firstReal(method) instanceof FrameNode)) {
            list.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
        }
        method.instructions.insert(list);
        return null;
    }

    /** The first node that is not a label or a line number (a frame or an instruction). */
    private static AbstractInsnNode firstReal(MethodNode method) {
        AbstractInsnNode insn = method.instructions.getFirst();
        while (insn instanceof LabelNode || insn instanceof LineNumberNode) {
            insn = insn.getNext();
        }
        return insn;
    }

    private static String renderEntities(ClassNode node) {
        final String desc = "(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/client/renderer/culling/ICamera;F)V";
        final List<MethodNode> found = methods(node, desc, "renderEntities", "func_147589_a");
        if (found.size() != 1) {
            return found.size() + " renderEntities" + desc + " methods, expected 1";
        }
        final MethodNode method = found.get(0);
        if (calls(method, HOOKS)) {
            return "already patched";
        }
        LdcInsnNode anchor = null;
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof LdcInsnNode && "blockentities".equals(((LdcInsnNode) insn).cst)) {
                if (anchor != null) {
                    return "more than one \"blockentities\" section";
                }
                anchor = (LdcInsnNode) insn;
            }
        }
        if (anchor == null) {
            return "no \"blockentities\" section";
        }
        // Between the profiler receiver and the LDC: stack-neutral, and the LDC stays directly in
        // front of endStartSection, where Angelica's INVOKE_STRING injectors look for it.
        final InsnList list = new InsnList();
        list.add(new VarInsnNode(Opcodes.ALOAD, 2));
        list.add(new VarInsnNode(Opcodes.FLOAD, 3));
        list.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, "renderLines",
            "(Lnet/minecraft/client/renderer/culling/ICamera;F)V", false));
        method.instructions.insertBefore(anchor, list);
        return null;
    }

    /**
     * Methods matching an MCP/SRG name and MCP descriptor. ThePatcher's transformers see Minecraft classes
     * before FML's deobfuscation (notch names: Entity is "sa", isInRangeToRender3d is "a(DDD)Z"), so names
     * and descriptors are first mapped through FML's own remapper (a no-op in a deobfuscated workspace).
     */
    private static List<MethodNode> methods(ClassNode node, String desc, String mcp, String srg) {
        final List<MethodNode> found = new ArrayList<>();
        for (Object o : node.methods) {
            final MethodNode m = (MethodNode) o;
            final String name = Names.method(node.name, m.name, m.desc);
            if (desc.equals(Names.desc(m.desc)) && (mcp.equals(name) || srg.equals(name))) {
                found.add(m);
            }
        }
        return found;
    }

    /** FML's notch -> SRG remapper when present (it is in every Forge client), identity otherwise. */
    static final class Names {
        private static final boolean FML;

        static {
            boolean present;
            try {
                present = FMLDeobfuscatingRemapper.INSTANCE != null;
            } catch (LinkageError absent) {
                present = false;
            }
            FML = present;
        }

        static String method(String owner, String name, String desc) {
            return FML ? FMLDeobfuscatingRemapper.INSTANCE.mapMethodName(owner, name, desc) : name;
        }

        static String desc(String desc) {
            return FML ? FMLDeobfuscatingRemapper.INSTANCE.mapMethodDesc(desc) : desc;
        }

        private Names() {}
    }

    private static boolean calls(MethodNode method, String owner) {
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof MethodInsnNode && owner.equals(((MethodInsnNode) insn).owner)) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static void addField(ClassNode node, FieldNode field) {
        node.fields.add(field);
    }
}
