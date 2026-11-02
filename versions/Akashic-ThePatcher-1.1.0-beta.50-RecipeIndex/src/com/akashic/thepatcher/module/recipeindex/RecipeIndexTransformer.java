package com.akashic.thepatcher.module.recipeindex;

import java.util.List;

import net.minecraft.launchwrapper.IClassTransformer;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.BasicVerifier;

import com.akashic.thepatcher.util.BundleLog;

/**
 * Boot time: recipe indexes for Thaumcraft and Eln (beta.50). Both sides.
 *
 * In TEOF 5.5 a JFR profile of the client boot put 9.7% of the main thread's Java samples in Thaumcraft's
 * ThaumcraftCraftingManager.generateTags (called by RotaryCraft, Thermal, MFR, ChromatiCraft, Extra Utilities and
 * others while registering aspects) and 5.4% in Eln's CraftingRecipes.recipeExists. Both walk the whole crafting
 * recipe list once per item.
 *
 * thaumcraft.common.lib.crafting.ThaumcraftCraftingManager.generateTagsFromCraftingRecipes(Item, int, ArrayList):
 *   after its single CraftingManager.getRecipeList() call: ALOAD item, ILOAD meta,
 *   INVOKESTATIC RecipeIndexHooks.thaumcraftRecipes(List, Item, int) List. The method only reads that list with
 *   get(i) and size(). Kill-switch -Dakashic.thaumcraftRecipeIndex.disable=true.
 * mods.eln.craft.CraftingRecipes.checkRecipe():
 *   first instruction INVOKESTATIC RecipeIndexHooks.elnBeginCheck(); its INVOKESPECIAL recipeExists(ItemStack) calls
 *   become INVOKESTATIC RecipeIndexHooks.elnRecipeExists(Object, ItemStack) (same stack). recipeExists itself is kept.
 *   Kill-switch -Dakashic.elnRecipeCheckIndex.disable=true.
 *
 * No branch is added, so no stack map frame changes. Fails closed: the exact call shapes must be found (Thaumcraft: one
 * getRecipeList call; Eln: at least one recipeExists call and no hook yet), every edited method must pass the ASM
 * verifier, else the class is kept as it is.
 */
public final class RecipeIndexTransformer implements IClassTransformer {

    static final String THAUMCRAFT = "thaumcraft.common.lib.crafting.ThaumcraftCraftingManager";
    static final String ELN = "mods.eln.craft.CraftingRecipes";

    static final String HOOKS = "com/akashic/thepatcher/module/recipeindex/RecipeIndexHooks";
    static final String CRAFTING_MANAGER = "net/minecraft/item/crafting/CraftingManager";
    static final String GET_RECIPE_LIST = "func_77592_b";
    static final String GET_RECIPE_LIST_DESC = "()Ljava/util/List;";
    static final String TC_METHOD = "generateTagsFromCraftingRecipes";
    static final String TC_METHOD_DESC = "(Lnet/minecraft/item/Item;ILjava/util/ArrayList;)Lthaumcraft/api/aspects/AspectList;";
    static final String TC_HOOK_DESC = "(Ljava/util/List;Lnet/minecraft/item/Item;I)Ljava/util/List;";
    static final String ELN_OWNER = "mods/eln/craft/CraftingRecipes";
    static final String ELN_CHECK = "checkRecipe";
    static final String ELN_EXISTS = "recipeExists";
    static final String ELN_EXISTS_DESC = "(Lnet/minecraft/item/ItemStack;)Z";
    static final String ELN_HOOK_DESC = "(Ljava/lang/Object;Lnet/minecraft/item/ItemStack;)Z";

    private static volatile boolean thaumcraftOffLogged;
    private static volatile boolean elnOffLogged;

    /**
     * Called by AkashicThePatcherLoadingPlugin.getASMTransformerClass right after beta.47's Angelica GL lookup bridge
     * registers, so this transformer is the last one in the list (both sides).
     */
    public static void register(List<String> transformers) {
        transformers.add(RecipeIndexTransformer.class.getName());
    }

    @Override
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        final String target = transformedName == null ? name : transformedName;
        final boolean thaumcraft = THAUMCRAFT.equals(target);
        if (!thaumcraft && !ELN.equals(target)) {
            return bytes;
        }
        if (thaumcraft ? Boolean.getBoolean(RecipeIndexHooks.THAUMCRAFT_DISABLE)
                       : Boolean.getBoolean(RecipeIndexHooks.ELN_DISABLE)) {
            logDisabled(thaumcraft);
            return bytes;
        }
        final String what = thaumcraft ? "Thaumcraft recipe index" : "Eln recipe check index";
        try {
            final ClassNode node = new ClassNode();
            new ClassReader(bytes).accept(node, 0);
            final String result = thaumcraft ? patchThaumcraft(node) : patchEln(node);
            if (!result.startsWith("ok")) {
                BundleLog.warn(what + " skipped for " + target + ": " + result + "; original bytecode retained.");
                return bytes;
            }
            final ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(writer);
            BundleLog.info(what + " applied: " + target + " (" + result.substring(3) + ").");
            return writer.toByteArray();
        } catch (ThreadDeath | VirtualMachineError fatal) {
            throw fatal;
        } catch (Throwable error) {
            BundleLog.error(what + " rejected " + target + "; original bytecode retained.", error);
            return bytes;
        }
    }

    private static void logDisabled(boolean thaumcraft) {
        if (thaumcraft && !thaumcraftOffLogged) {
            thaumcraftOffLogged = true;
            BundleLog.info("Thaumcraft recipe index DISABLED by -D" + RecipeIndexHooks.THAUMCRAFT_DISABLE + "=true.");
        } else if (!thaumcraft && !elnOffLogged) {
            elnOffLogged = true;
            BundleLog.info("Eln recipe check index DISABLED by -D" + RecipeIndexHooks.ELN_DISABLE + "=true.");
        }
    }

    static String patchThaumcraft(ClassNode node) throws AnalyzerException {
        MethodNode method = null;
        for (Object o : node.methods) {
            final MethodNode m = (MethodNode) o;
            if (TC_METHOD.equals(m.name) && TC_METHOD_DESC.equals(m.desc)) method = m;
        }
        if (method == null || (method.access & Opcodes.ACC_STATIC) == 0) {
            return "no static " + TC_METHOD + TC_METHOD_DESC;
        }
        MethodInsnNode listCall = null;
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (!(insn instanceof MethodInsnNode)) continue;
            final MethodInsnNode call = (MethodInsnNode) insn;
            if (HOOKS.equals(call.owner)) {
                return "already patched";
            }
            if (call.getOpcode() == Opcodes.INVOKEVIRTUAL && CRAFTING_MANAGER.equals(call.owner)
                && GET_RECIPE_LIST.equals(call.name) && GET_RECIPE_LIST_DESC.equals(call.desc)) {
                if (listCall != null) {
                    return "getRecipeList called more than once";
                }
                listCall = call;
            }
        }
        if (listCall == null) {
            return "no CraftingManager.getRecipeList call";
        }
        final AbstractInsnNode store = real(listCall.getNext());
        if (!(store instanceof VarInsnNode) || store.getOpcode() != Opcodes.ASTORE) {
            return "getRecipeList result is not stored in a local";
        }
        final InsnList hook = new InsnList();
        hook.add(new VarInsnNode(Opcodes.ALOAD, 0));
        hook.add(new VarInsnNode(Opcodes.ILOAD, 1));
        hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, "thaumcraftRecipes", TC_HOOK_DESC, false));
        method.instructions.insert(listCall, hook);
        new Analyzer(new BasicVerifier()).analyze(node.name, method);
        return "ok 1 recipe list filtered by output item";
    }

    static String patchEln(ClassNode node) throws AnalyzerException {
        MethodNode check = null;
        for (Object o : node.methods) {
            final MethodNode m = (MethodNode) o;
            if (ELN_CHECK.equals(m.name) && "()V".equals(m.desc)) check = m;
        }
        if (check == null || (check.access & Opcodes.ACC_STATIC) != 0) {
            return "no instance " + ELN_CHECK + "()V";
        }
        int calls = 0;
        for (AbstractInsnNode insn = check.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (!(insn instanceof MethodInsnNode)) continue;
            final MethodInsnNode call = (MethodInsnNode) insn;
            if (HOOKS.equals(call.owner)) {
                return "already patched";
            }
            if (call.getOpcode() == Opcodes.INVOKESPECIAL && ELN_OWNER.equals(call.owner)
                && ELN_EXISTS.equals(call.name) && ELN_EXISTS_DESC.equals(call.desc)) {
                call.setOpcode(Opcodes.INVOKESTATIC);
                call.owner = HOOKS;
                call.name = "elnRecipeExists";
                call.desc = ELN_HOOK_DESC;
                call.itf = false;
                calls++;
            }
        }
        if (calls == 0) {
            return "no recipeExists call in checkRecipe";
        }
        check.instructions.insert(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, "elnBeginCheck", "()V", false));
        new Analyzer(new BasicVerifier()).analyze(node.name, check);
        return "ok " + calls + " recipeExists call(s) answered from an output index";
    }

    static AbstractInsnNode real(AbstractInsnNode insn) {
        while (insn != null && insn.getOpcode() < 0) insn = insn.getNext();
        return insn;
    }
}
