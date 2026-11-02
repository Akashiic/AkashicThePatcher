package com.akashic.thepatcher.module.recipeindex;

import java.lang.reflect.Field;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraftforge.oredict.OreDictionary;

import com.akashic.thepatcher.util.BundleLog;

/**
 * Recipe indexes for two quadratic boot-time scans (beta.50). See RecipeIndexTransformer.
 *
 * Thaumcraft: ThaumcraftCraftingManager.generateTagsFromCraftingRecipes(item, meta, history) walks the whole crafting
 * recipe list for every item whose aspects it derives, and only looks further at recipes whose output is that item.
 * thaumcraftRecipes() hands it a list with the same recipes in the same order, minus the recipes of the four plain
 * recipe classes (ShapedRecipes, ShapelessRecipes, ShapedOreRecipe, ShapelessOreRecipe, exact classes) whose output
 * is null or another item: those it would skip anyway. Recipes of any other class, and entries that are not
 * recipes, stay in every list, so the method sees them exactly as before.
 *
 * Eln: CraftingRecipes.checkRecipe() logs "No recipe for" items, asking recipeExists(stack) for each of its items, which
 * walks the whole list calling Utils.areSame(stack, output). elnRecipeExists() answers from an index of the outputs'
 * (item, damage) pairs and ore ids, built once per checkRecipe call; whenever the answer depends on an exception
 * inside areSame it runs the original loop instead.
 *
 * Both indexes are rebuilt when the recipe list changes (same object, size and modCount) and turn themselves off
 * for the session on any unexpected error (the original code then runs unchanged).
 */
public final class RecipeIndexHooks {

    public static final String THAUMCRAFT_DISABLE = "akashic.thaumcraftRecipeIndex.disable";
    public static final String ELN_DISABLE = "akashic.elnRecipeCheckIndex.disable";
    /** Diagnostics: also checks every answer against a full scan (as slow as the original). */
    public static final String VERIFY = "akashic.recipeIndex.verify";

    static final Set<String> PLAIN_RECIPE_CLASSES = new HashSet<String>(Arrays.asList(
        "net.minecraft.item.crafting.ShapedRecipes",
        "net.minecraft.item.crafting.ShapelessRecipes",
        "net.minecraftforge.oredict.ShapedOreRecipe",
        "net.minecraftforge.oredict.ShapelessOreRecipe"));

    private static final boolean VERIFY_ON = Boolean.getBoolean(VERIFY);
    private static volatile boolean thaumcraftBroken;
    private static volatile boolean elnBroken;

    static final AtomicLong TC_CALLS = new AtomicLong();
    static final AtomicLong TC_BUILDS = new AtomicLong();
    static final AtomicLong TC_SCANNED = new AtomicLong();
    static final AtomicLong TC_HANDED = new AtomicLong();
    static final AtomicLong TC_VERIFY_FAIL = new AtomicLong();
    static final AtomicLong ELN_CALLS = new AtomicLong();
    static final AtomicLong ELN_FALLBACKS = new AtomicLong();
    static final AtomicLong ELN_VERIFY_FAIL = new AtomicLong();

    private static ThaumcraftIndex thaumcraftIndex;
    private static ElnIndex elnIndex;
    private static long lastReport;

    private RecipeIndexHooks() {}

    // ------------------------------------------------------------------------------------------------ Thaumcraft

    /** Replaces the recipe list read at the top of generateTagsFromCraftingRecipes. */
    public static List<?> thaumcraftRecipes(List<?> recipes, Item item, int meta) {
        if (thaumcraftBroken || recipes == null || item == null) {
            return recipes;
        }
        try {
            final List<?> result;
            synchronized (RecipeIndexHooks.class) {
                ThaumcraftIndex index = thaumcraftIndex;
                if (index == null || !index.state.matches(recipes)) {
                    final ListState state = ListState.of(recipes);
                    if (state == null) {
                        return recipes;
                    }
                    index = ThaumcraftIndex.build(recipes, state);
                    thaumcraftIndex = index;
                    TC_BUILDS.incrementAndGet();
                }
                result = index.candidates(recipes, item);
            }
            TC_CALLS.incrementAndGet();
            TC_SCANNED.addAndGet(recipes.size());
            TC_HANDED.addAndGet(result.size());
            if (VERIFY_ON) {
                verifyThaumcraft(recipes, result, item);
            }
            reportMaybe();
            return result;
        } catch (ThreadDeath | VirtualMachineError fatal) {
            throw fatal;
        } catch (Throwable error) {
            thaumcraftBroken = true;
            BundleLog.error("Thaumcraft recipe index disabled for this session; the full recipe list is used again.", error);
            return recipes;
        }
    }

    /** Every recipe left out must be a plain recipe whose output is null or not the item (checked now). */
    private static void verifyThaumcraft(List<?> recipes, List<?> handed, Item item) {
        final IdentityHashMap<Object, Boolean> kept = new IdentityHashMap<Object, Boolean>();
        for (Object recipe : handed) {
            kept.put(recipe, Boolean.TRUE);
        }
        int previous = -1;
        for (Object recipe : handed) {
            int position = indexOfIdentity(recipes, recipe, previous + 1);
            if (position < 0) {
                fail("order", recipe);
                return;
            }
            previous = position;
        }
        for (Object recipe : recipes) {
            if (recipe == null || kept.containsKey(recipe)) continue;
            if (!PLAIN_RECIPE_CLASSES.contains(recipe.getClass().getName())) {
                fail("non-plain recipe left out", recipe);
                return;
            }
            final ItemStack output = ((IRecipe) recipe).func_77571_b();
            if (output != null && output.func_77973_b() == item) {
                fail("matching recipe left out", recipe);
                return;
            }
        }
    }

    private static int indexOfIdentity(List<?> list, Object value, int from) {
        for (int i = from; i < list.size(); i++) {
            if (list.get(i) == value) return i;
        }
        return -1;
    }

    private static void fail(String what, Object recipe) {
        if (TC_VERIFY_FAIL.incrementAndGet() <= 5) {
            BundleLog.warn("Thaumcraft recipe index VERIFY FAILED (" + what + "): " + recipe);
        }
    }

    static final class ThaumcraftIndex {
        final ListState state;
        final Map<Item, int[]> byOutput;
        final int[] always;

        private ThaumcraftIndex(ListState state, Map<Item, int[]> byOutput, int[] always) {
            this.state = state;
            this.byOutput = byOutput;
            this.always = always;
        }

        static ThaumcraftIndex build(List<?> recipes, ListState state) {
            final Map<Item, IntList> lists = new IdentityHashMap<Item, IntList>();
            final IntList always = new IntList();
            for (int i = 0; i < recipes.size(); i++) {
                final Object recipe = recipes.get(i);
                if (recipe == null || !PLAIN_RECIPE_CLASSES.contains(recipe.getClass().getName())) {
                    always.add(i);
                    continue;
                }
                final ItemStack output = ((IRecipe) recipe).func_77571_b();
                final Item item = output == null ? null : output.func_77973_b();
                if (item == null) {
                    continue; // skipped by the original in any case
                }
                IntList list = lists.get(item);
                if (list == null) {
                    list = new IntList();
                    lists.put(item, list);
                }
                list.add(i);
            }
            final Map<Item, int[]> byOutput = new IdentityHashMap<Item, int[]>(lists.size() * 2);
            for (Map.Entry<Item, IntList> entry : lists.entrySet()) {
                byOutput.put(entry.getKey(), entry.getValue().toArray());
            }
            return new ThaumcraftIndex(state, byOutput, always.toArray());
        }

        List<Object> candidates(List<?> recipes, Item item) {
            final int[] own = byOutput.get(item);
            final int[] other = always;
            final int ownLength = own == null ? 0 : own.length;
            final ArrayList<Object> out = new ArrayList<Object>(ownLength + other.length);
            int a = 0;
            int b = 0;
            while (a < ownLength || b < other.length) {
                final int next;
                if (b >= other.length || (a < ownLength && own[a] < other[b])) {
                    next = own[a++];
                } else {
                    next = other[b++];
                }
                out.add(recipes.get(next));
            }
            return out;
        }
    }

    // ------------------------------------------------------------------------------------------------ Eln

    /** First instruction of CraftingRecipes.checkRecipe(): the index lives for one checkRecipe call. */
    public static void elnBeginCheck() {
        synchronized (RecipeIndexHooks.class) {
            elnIndex = null;
        }
    }

    /** Replaces CraftingRecipes.recipeExists(stack) at its four call sites in checkRecipe(). */
    public static boolean elnRecipeExists(Object craftingRecipes, ItemStack stack) {
        if (stack == null) {
            return false;
        }
        ELN_CALLS.incrementAndGet();
        if (!elnBroken) {
            try {
                final int answer = elnIndexed(stack);
                if (answer >= 0) {
                    final boolean exists = answer == 1;
                    if (VERIFY_ON && exists != originalRecipeExists(stack)
                        && ELN_VERIFY_FAIL.incrementAndGet() <= 5) {
                        BundleLog.warn("Eln recipe check index VERIFY FAILED for " + stack + " (index said " + exists + ")");
                    }
                    reportMaybe();
                    return exists;
                }
                ELN_FALLBACKS.incrementAndGet();
                reportMaybe();
            } catch (ThreadDeath | VirtualMachineError fatal) {
                throw fatal;
            } catch (Throwable error) {
                elnBroken = true;
                BundleLog.error("Eln recipe check index disabled for this session; the original scan is used again.", error);
            }
        }
        return originalRecipeExists(stack);
    }

    /** 1 / 0, or -1 when the answer has to come from the original scan. */
    private static int elnIndexed(ItemStack stack) {
        final List<?> recipes = CraftingManager.func_77594_a().func_77592_b();
        ElnIndex index;
        synchronized (RecipeIndexHooks.class) {
            index = elnIndex;
            if (index == null || !index.state.matches(recipes)) {
                final ListState state = ListState.of(recipes);
                if (state == null) {
                    return -1;
                }
                index = ElnIndex.build(recipes, state);
                elnIndex = index;
            }
        }
        return index.valid ? index.query(stack) : -1;
    }

    /** CraftingRecipes.recipeExists as compiled in Eln 1.24.8 (iterator, instanceof, output read twice). */
    static boolean originalRecipeExists(ItemStack stack) {
        final List<?> recipes = CraftingManager.func_77594_a().func_77592_b();
        for (Object o : recipes) {
            if (!(o instanceof IRecipe) || ((IRecipe) o).func_77571_b() == null) continue;
            final ItemStack output = ((IRecipe) o).func_77571_b();
            if (output == null) {
                throw new NullPointerException("getRecipeOutput(...) must not be null");
            }
            if (mods.eln.misc.Utils.areSame(stack, output)) return true;
        }
        return false;
    }

    static final class ElnIndex {
        final ListState state;
        final boolean valid;
        final Map<Item, Set<Integer>> exact = new IdentityHashMap<Item, Set<Integer>>();
        final Set<Item> damageThrew = new HashSet<Item>();
        final Set<Integer> oreIds = new HashSet<Integer>();

        private ElnIndex(ListState state, boolean valid) {
            this.state = state;
            this.valid = valid;
        }

        static ElnIndex build(List<?> recipes, ListState state) {
            final ElnIndex index = new ElnIndex(state, true);
            for (Object o : recipes) {
                if (!(o instanceof IRecipe) || ((IRecipe) o).func_77571_b() == null) continue;
                final ItemStack output = ((IRecipe) o).func_77571_b();
                if (output == null) {
                    return new ElnIndex(state, false); // the original throws here; let it
                }
                final Item item = output.func_77973_b();
                if (item == null) {
                    continue; // areSame is false for it with any stack
                }
                try {
                    final int damage = output.func_77960_j();
                    Set<Integer> damages = index.exact.get(item);
                    if (damages == null) {
                        damages = new HashSet<Integer>();
                        index.exact.put(item, damages);
                    }
                    damages.add(damage);
                } catch (Exception e) {
                    index.damageThrew.add(item);
                } catch (Throwable error) {
                    return new ElnIndex(state, false);
                }
                try {
                    for (int id : OreDictionary.getOreIDs(output)) {
                        index.oreIds.add(id);
                    }
                } catch (Exception e) {
                    // areSame's ore comparison is false for this output
                } catch (Throwable error) {
                    return new ElnIndex(state, false);
                }
            }
            return index;
        }

        int query(ItemStack stack) {
            final Item item = stack.func_77973_b();
            if (item == null || damageThrew.contains(item)) {
                return -1;
            }
            final int damage;
            final int[] ids;
            try {
                damage = stack.func_77960_j();
                final Set<Integer> damages = exact.get(item);
                if (damages != null && damages.contains(damage)) {
                    return 1;
                }
                ids = OreDictionary.getOreIDs(stack);
            } catch (Exception e) {
                return -1;
            }
            for (int id : ids) {
                if (oreIds.contains(id)) return 1;
            }
            return 0;
        }
    }

    // ------------------------------------------------------------------------------------------------ shared

    /** Identity, size and modCount of the recipe list; null for lists whose modCount cannot be read. */
    static final class ListState {
        private static Field modCountField;
        final Object list;
        final int size;
        final int modCount;

        private ListState(Object list, int size, int modCount) {
            this.list = list;
            this.size = size;
            this.modCount = modCount;
        }

        static ListState of(List<?> list) {
            if (!(list instanceof AbstractList)) {
                return null;
            }
            final Integer modCount = modCount(list);
            return modCount == null ? null : new ListState(list, list.size(), modCount);
        }

        boolean matches(List<?> current) {
            if (current != list || current.size() != size) {
                return false;
            }
            final Integer now = modCount(current);
            return now != null && now == modCount;
        }

        private static Integer modCount(List<?> list) {
            try {
                Field field = modCountField;
                if (field == null) {
                    field = AbstractList.class.getDeclaredField("modCount");
                    field.setAccessible(true);
                    modCountField = field;
                }
                return field.getInt(list);
            } catch (Exception e) {
                return null;
            }
        }
    }

    static final class IntList {
        private int[] values = new int[8];
        private int size;

        void add(int value) {
            if (size == values.length) {
                values = Arrays.copyOf(values, size * 2);
            }
            values[size++] = value;
        }

        int[] toArray() {
            return Arrays.copyOf(values, size);
        }
    }

    private static void reportMaybe() {
        final long now = System.nanoTime();
        if (now - lastReport > 10_000_000_000L) {
            lastReport = now;
            BundleLog.info(summary());
        }
    }

    /** One line for the log, written at most every 10 s while either index is in use. */
    public static String summary() {
        final long calls = TC_CALLS.get();
        final long scanned = TC_SCANNED.get();
        final long handed = TC_HANDED.get();
        return "Recipe index: Thaumcraft calls=" + calls + " builds=" + TC_BUILDS.get() + " recipes handed "
            + handed + " of " + scanned + (VERIFY_ON ? " verifyFailures=" + TC_VERIFY_FAIL.get() : "")
            + "; Eln checks=" + ELN_CALLS.get() + " fallbacks=" + ELN_FALLBACKS.get()
            + (VERIFY_ON ? " verifyFailures=" + ELN_VERIFY_FAIL.get() : "");
    }

    /** Used by gates. */
    static Map<String, Long> counters() {
        final Map<String, Long> out = new HashMap<String, Long>();
        out.put("tcCalls", TC_CALLS.get());
        out.put("tcBuilds", TC_BUILDS.get());
        out.put("tcHanded", TC_HANDED.get());
        out.put("tcVerifyFail", TC_VERIFY_FAIL.get());
        out.put("elnCalls", ELN_CALLS.get());
        out.put("elnFallbacks", ELN_FALLBACKS.get());
        out.put("elnVerifyFail", ELN_VERIFY_FAIL.get());
        return out;
    }
}
