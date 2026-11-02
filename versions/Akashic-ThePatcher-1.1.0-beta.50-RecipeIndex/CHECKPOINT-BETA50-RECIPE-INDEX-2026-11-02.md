# beta.50 - RecipeIndex - 2026-11-02

`!Akashic-ThePatcher-1.1.0-beta.50-RecipeIndex.jar`, SHA-256 `2b746b0f70cc009b11192c3624ad7eaf05d177c6341dc9af99d03e256603f948`,
408516 B, 190 entries. Base: beta.49 (`c0b533a9...`). Built and gated by `tools/beta50-build.py` (report: `reports/final-build-audit.json`).

## Problem

Boot time of TEOF 5.5. A JFR profile of the client boot (Technic-Forbidden, `otimizacao-ram/BOOT.md`) put 9.7% of
the main thread's Java samples in Thaumcraft 4.2.3.5's `ThaumcraftCraftingManager.generateTags` and 5.4% in Eln
1.24.8's `CraftingRecipes.recipeExists`. Both walk the whole crafting recipe list (~16,500 recipes in the pack) once
per item:

* **Thaumcraft.** `generateTagsFromCraftingRecipes(item, meta, history)` derives an item's aspects from its recipes. It
  loops over `CraftingManager.getRecipeList()` and only looks further at recipes whose output is that item (the JFR
  leaf is `Item.getIdFromItem` on every recipe's output). RotaryCraft, Thermal Foundation/Expansion, MFR,
  ChromatiCraft, Extra Utilities, Thaumic Energistics and others trigger it while registering aspects (775+ calls).
* **Eln.** `checkRecipe()` asks `recipeExists(stack)` for each of its 373 items, and only logs the ones without a
  recipe. Each call compares the item with every recipe output through `Utils.areSame` (item and damage, then ore ids).

## Fix

`RecipeIndexTransformer` (both sides, registered last, after beta.47's Angelica GL lookup bridge) + `RecipeIndexHooks`:

| Target | Edit | Index |
|---|---|---|
| `ThaumcraftCraftingManager.generateTagsFromCraftingRecipes` | after its single `getRecipeList()` call: `ALOAD item, ILOAD meta, INVOKESTATIC thaumcraftRecipes(List, Item, int)` | Item -> positions of the recipes whose output is that item. Only "trusted" recipes are indexed: the four plain classes (`ShapedRecipes`, `ShapelessRecipes`, `ShapedOreRecipe`, `ShapelessOreRecipe`) and subclasses whose `getRecipeOutput` is still the plain class's. Every other entry stays in every list. The method gets the same recipes in the same order, minus trusted recipes it would skip anyway. |
| `CraftingRecipes.checkRecipe` | first instruction `elnBeginCheck()`; the 4 `recipeExists` calls become `elnRecipeExists(Object, ItemStack)` | Outputs' (item, damage) pairs and ore ids, built once per `checkRecipe` call. Any case that depends on an exception inside `areSame` (null item, a throwing `getDamage`) runs the original loop. `recipeExists` itself is not changed. |

Both indexes are rebuilt when the recipe list changes (same object, size and `modCount`). On any unexpected error they
switch off for the session and the original code runs. No branch is added (no stack map frame changes). The
transformer fails closed on the exact call shapes and runs the ASM verifier on each edited method.

Why it is equivalent:
* Thaumcraft's method reads that local only through `get(i)` and `size()`. The loop index has no other use, checked in
  the bytecode: every `iload 6` feeds `List.get`, every `aload 5` feeds `get`/`size`.
* A trusted recipe left out has a null output or another item. The original code skips such recipes before doing
  anything else (`getRecipeOutput().getItem() != item`).

Kill-switches:
* `-Dakashic.thaumcraftRecipeIndex.disable=true`
* `-Dakashic.elnRecipeCheckIndex.disable=true`
* Diagnostics: `-Dakashic.recipeIndex.verify=true` checks every answer against a full scan, made at call time.

## Offline gates (all PASS, `reports/final-build-audit.json`)

* Deterministic double build. 4 entries changed (plugin, mod class, manifest, `mcmod.info`), the 6 recipeindex classes
  added, 180 entries byte-identical to beta.49. Class major <= 52.
* Plugin: only `getASMTransformerClass` differs.
* Registration on the JVM verifier: client and server = beta.49's list + `RecipeIndexTransformer` last.
* `RecipeIndexGate` (`-Xverify:all`): shapes of both methods (same loops, same output tests) run as compiled and through
  the shipped transformer.
  * Thaumcraft: 3,840 queries on 6 random lists of 3,000 recipes, mutated between rounds (add, remove, set). The lists
    hold null outputs, metas up to 32767, a trusted subclass, a subclass overriding `getRecipeOutput` and an untrusted
    class whose outputs change between calls.
  * Eln: 6 lists with ore ids, a null-item output and an item whose `getDamage` throws.
  * Results: identical answers, 0 verify failures.
  * beta.49 and the kill-switches leave the shapes untouched.

## Real client (headless, TEOF 5.5, same instance as beta.48/49)

* **Verification boot** (`-Dakashic.recipeIndex.verify=true`):
  * both transformers applied (`Thaumcraft recipe index applied`, `Eln recipe check index applied (4 recipeExists call(s))`);
  * at least 775 Thaumcraft calls and all 373 Eln checks were compared with the full scan: **0 failures**;
  * the menu was reached.
* **Index coverage:** 3,732 of the ~16,500 recipes are untrusted (their classes override `getRecipeOutput`), so each
  Thaumcraft call goes through 18% of the list (2.76 M recipes handed instead of 15.4 M).
  * A first cut that trusted only the four exact classes still handed 28% of the list.
* **A/B, alternating, warm cache:** OFF = kill-switches, ON = indexes.

| Callback (FMLPhaseAudit top list) | OFF (3 boots) | ON (3 boots) |
|---|---:|---:|
| Eln, PostInit | 4.4 / 5.1 / 3.6 s | not in the top 15 (below 1.04 / 0.83 / 1.03 s) |
| RotaryCraft, PostInit | 2.8 / 1.9 / 2.2 s | 1.7 / 1.3 / 1.5 s |
| Extra Utilities, PostInit | 2.9 / 1.6 / 2.3 s | 2.3 / 1.2 / 2.0 s |
| Thaumcraft, PostInit | 3.5 / 3.6 / 3.8 s | 3.3 / 3.0 / 3.5 s |
| Thermal Foundation, LoadComplete | 1.23 / 0.82 / 0.66 s | 0.19 / 0.24 / 0.27 s |
| Menu (whole boot) | 350 / 317 / 297 s | 321 / 307 / 318 s |

Saved: **about 6-7 s per boot**. Eln accounts for 3.5-4 s; the Thaumcraft callers together for 2-3 s. Whole-boot times
move by ±25 s between runs on this VM, so the total does not show it; the callbacks do. The JFR share (15%) overstated
the wall-clock time, as it did for GeckoLib: the rest of `generateTags` (recursion over ingredients, aspect lists) is
not touched.

## Acceptance gate in the pack

`logs/latest.log`:
* `Thaumcraft recipe index applied: thaumcraft.common.lib.crafting.ThaumcraftCraftingManager ...`
* `Eln recipe check index applied: mods.eln.craft.CraftingRecipes (4 recipeExists call(s) ...)`
* `Recipe index: Thaumcraft calls=... Eln checks=373 fallbacks=0`
* no `recipe index disabled for this session`

Optional, slow, one boot: `-Dakashic.recipeIndex.verify=true` must show `verifyFailures=0` for both. Rollback: beta.49.
