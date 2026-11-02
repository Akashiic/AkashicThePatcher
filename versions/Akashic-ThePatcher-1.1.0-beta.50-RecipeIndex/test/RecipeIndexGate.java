import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;

import akashic.gate.OtherRecipe;
import akashic.gate.ThrowingItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.ShapedRecipes;
import net.minecraft.item.crafting.ShapelessRecipes;
import net.minecraftforge.oredict.OreDictionary;
import net.minecraftforge.oredict.ShapedOreRecipe;
import net.minecraftforge.oredict.ShapelessOreRecipe;

/**
 * Runs the Thaumcraft and Eln shapes twice on the same recipe lists: once as compiled (reference) and once through
 * the transformer of the jar under test (as LaunchClassLoader would), and compares every answer.
 *
 *   java -Xverify:all -cp <test>:<jar>:<stubs>:<launchwrapper>:<asm-all-5> RecipeIndexGate <shapes dir> patched|unpatched
 *
 * patched: both shape classes must be transformed (and give the reference answers); unpatched: neither (beta.49, or
 * the kill-switches). Thaumcraft: 6 random lists (plain recipes of the four trusted classes, outputs with null items,
 * metas incl. 32767, a recipe class the index does not trust whose output changes between calls), every item x meta
 * queried, the list mutated between rounds (add, remove, set). Eln: 6 random lists with ore ids, an output with a
 * null item and an item whose getDamage throws (fallback path).
 */
public final class RecipeIndexGate {
    static int failures;

    public static void main(String[] args) throws Exception {
        final Path shapes = Paths.get(args[0]);
        final boolean expectPatched = "patched".equals(args[1]);
        final List<String> owned = Arrays.asList("thaumcraft.common.lib.crafting.ThaumcraftCraftingManager",
            "mods.eln.craft.CraftingRecipes");
        final ClassLoader parent = RecipeIndexGate.class.getClassLoader();
        final TransformingLoader reference = new TransformingLoader(parent, shapes, owned, "absent.Transformer");
        final TransformingLoader patched = new TransformingLoader(parent, shapes, owned,
            "com.akashic.thepatcher.module.recipeindex.RecipeIndexTransformer");

        final Method tcRef = reference.loadClass(owned.get(0)).getMethod("run", Item.class, int.class);
        final Method tcNew = patched.loadClass(owned.get(0)).getMethod("run", Item.class, int.class);
        final Object elnRef = reference.loadClass(owned.get(1)).newInstance();
        final Object elnNew = patched.loadClass(owned.get(1)).newInstance();
        final Method elnRefRun = elnRef.getClass().getMethod("run");
        final Method elnNewRun = elnNew.getClass().getMethod("run");
        check(patched.transformed == (expectPatched ? 2 : 0),
            "shapes transformed by the jar under test: " + patched.transformed + " (want " + (expectPatched ? 2 : 0) + ")");

        final List<Object> recipes = CraftingManager.func_77594_a().func_77592_b();
        long queries = 0;
        int mismatches = 0;
        for (int seed = 1; seed <= 6; seed++) {
            final Random random = new Random(seed);
            final Item[] items = new Item[40];
            for (int i = 0; i < items.length; i++) items[i] = new Item();
            for (int round = 0; round < 4; round++) {
                if (round == 0) {
                    recipes.clear();
                    for (int i = 0; i < 3000; i++) recipes.add(randomRecipe(random, items, true));
                } else {
                    for (int i = 0; i < 50; i++) recipes.add(random.nextInt(recipes.size()), randomRecipe(random, items, true));
                    for (int i = 0; i < 30; i++) recipes.remove(random.nextInt(recipes.size()));
                    recipes.set(random.nextInt(recipes.size()), randomRecipe(random, items, true));
                }
                // the untrusted recipes keep a call counter: run reference and patched on fresh, identical copies
                final List<Object> snapshot = new ArrayList<Object>(recipes);
                for (Item item : items) {
                    for (int meta : new int[] {0, 1, 2, 32767}) {
                        recipes.clear();
                        recipes.addAll(copy(snapshot));
                        final String want = describe(tcRef.invoke(null, item, meta), recipes);
                        recipes.clear();
                        recipes.addAll(copy(snapshot));
                        final String got = describe(tcNew.invoke(null, item, meta), recipes);
                        queries++;
                        if (!want.equals(got) && mismatches++ < 5) {
                            System.out.println("  TC MISMATCH seed=" + seed + " round=" + round + " meta=" + meta + " want=" + want + " got=" + got);
                        }
                    }
                }
                recipes.clear();
                recipes.addAll(snapshot);
            }
        }
        check(mismatches == 0, "Thaumcraft shape: " + queries + " queries, " + mismatches + " different answers");

        int elnMismatches = 0;
        int elnMissing = 0;
        for (int seed = 11; seed <= 16; seed++) {
            final Random random = new Random(seed);
            final Item[] items = new Item[60];
            for (int i = 0; i < items.length; i++) items[i] = i % 17 == 0 ? new ThrowingItem() : new Item();
            OreDictionary.IDS.clear();
            for (Item item : items) {
                if (random.nextInt(3) == 0) OreDictionary.IDS.put(item, new int[] {random.nextInt(25)});
            }
            recipes.clear();
            for (int i = 0; i < 2000; i++) recipes.add(randomRecipe(random, items, false));
            CraftingRecipesItems.fill(random, items);
            final List<?> want = new ArrayList<Object>((List<?>) elnRefRun.invoke(elnRef));
            final List<?> got = new ArrayList<Object>((List<?>) elnNewRun.invoke(elnNew));
            elnMissing += want.size();
            if (!want.equals(got) && elnMismatches++ < 5) {
                System.out.println("  ELN MISMATCH seed=" + seed + " want=" + want + " got=" + got);
            }
        }
        check(elnMismatches == 0, "Eln shape: 6 lists, " + elnMissing + " items without recipe, " + elnMismatches + " different lists");

        final Map<String, Long> counters = counters();
        if (counters != null) {
            System.out.println("COUNTERS " + counters);
            if (expectPatched) {
                check(counters.get("tcCalls") > 0 && counters.get("elnCalls") > 0, "hooks were called");
                check(counters.get("tcHanded") < counters.get("tcCalls") * 3000L, "Thaumcraft hook handed fewer recipes than the list has");
                check(counters.get("elnFallbacks") > 0, "Eln fallback path exercised (null-item / throwing getDamage)");
                check(counters.get("tcVerifyFail") == 0 && counters.get("elnVerifyFail") == 0, "verify mode: no failures");
            }
        }
        if (failures != 0) {
            System.out.println("RECIPE_INDEX_GATE FAIL failures=" + failures);
            System.exit(1);
        }
        System.out.println("RECIPE_INDEX_GATE PASS expect=" + args[1]);
    }

    /** The Eln shape's item list. */
    static final class CraftingRecipesItems {
        static void fill(Random random, Item[] items) throws Exception {
            final List<ItemStack> list = akashic.gate.ElnItems.ITEMS;
            list.clear();
            for (int i = 0; i < 300; i++) {
                final int pick = random.nextInt(items.length + 1);
                list.add(new ItemStack(pick == items.length ? null : items[pick], 1, random.nextInt(16)));
            }
        }
    }

    static Object randomRecipe(Random random, Item[] items, boolean dynamic) {
        final int kind = random.nextInt(dynamic ? 7 : 6);
        final int cost = random.nextInt(50);
        if (kind == 6) {
            return new OtherRecipe(cost, stack(random, items), stack(random, items));
        }
        if (kind == 5) {
            return new OtherRecipe(cost, stack(random, items));
        }
        final ItemStack output = random.nextInt(40) == 0 ? null : stack(random, items);
        switch (kind) {
            case 0: return new ShapedRecipes(output, cost);
            case 1: return new ShapelessRecipes(output, cost);
            case 2: return new ShapedOreRecipe(output, cost);
            case 3: return new ShapelessOreRecipe(output, cost);
            default: return random.nextInt(30) == 0 ? null : new ShapedRecipes(output, cost);
        }
    }

    static ItemStack stack(Random random, Item[] items) {
        final Item item = random.nextInt(50) == 0 ? null : items[random.nextInt(items.length)];
        final int meta = random.nextInt(10) == 0 ? 32767 : random.nextInt(16);
        return new ItemStack(item, 1, meta);
    }

    /** Fresh copies of the untrusted recipes (their call counters start at 0), plain recipes shared. */
    static List<Object> copy(List<Object> recipes) {
        final List<Object> out = new ArrayList<Object>(recipes.size());
        for (Object recipe : recipes) {
            if (recipe instanceof OtherRecipe) {
                final OtherRecipe other = (OtherRecipe) recipe;
                out.add(new OtherRecipe(other.cost, other.outputs));
            } else {
                out.add(recipe);
            }
        }
        return out;
    }

    /** Position of the winning recipe in the list the shape saw (copies of untrusted recipes are fresh per run). */
    static String describe(Object aspects, List<Object> recipes) {
        if (aspects == null) return "none";
        final thaumcraft.api.aspects.AspectList list = (thaumcraft.api.aspects.AspectList) aspects;
        int position = -1;
        for (int i = 0; i < recipes.size(); i++) {
            if (recipes.get(i) == list.recipe) {
                position = i;
                break;
            }
        }
        return position + "/" + list.cost;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Long> counters() {
        try {
            final Method m = Class.forName("com.akashic.thepatcher.module.recipeindex.RecipeIndexHooks").getDeclaredMethod("counters");
            m.setAccessible(true);
            return (Map<String, Long>) m.invoke(null);
        } catch (ClassNotFoundException absent) {
            return null;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static void check(boolean ok, String what) {
        System.out.println("GATE " + (ok ? "ok  " : "FAIL") + " " + what);
        if (!ok) failures++;
    }
}
