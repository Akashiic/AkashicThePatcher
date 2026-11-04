# beta.52 - HeldItemCullBox - 2026-11-05

`!Akashic-ThePatcher-1.1.0-beta.52-HeldItemCullBox.jar`, SHA-256 `9fb5ae22c89be71919d31b584f138668448271af78d0b383b72e8ed9cf5059af`,
419456 B, 194 entries. Base: beta.51 (`5e2fce96...`). Built and gated by `tools/beta52-build.py` (report:
`reports/final-build-audit.json`).

## Problem

Reported with the AkashicFishing 3D fishing rods (drawn by Kunftig through GeckoLib): looking at another player from
the side, at the edge of the screen, the 3D rod disappears and only the plain black fishing line is left.

Cause, read in the bytecode of 1.7.10 and Angelica 2.2.43, and reproduced in a TEOF 4.8 client:

* An entity is drawn only when **its own box** is in view.
  * Vanilla `RenderGlobal.renderEntities` tests `camera.isBoundingBoxInFrustum(entity.boundingBox)`, twice: once for
    the entity and once for its leash holder.
  * Angelica (Celeritas) redirects `isInRangeToRender3d` in that loop so it also needs
    `CeleritasWorldRenderer.isEntityVisible(entity)`. That method tests the same box against the chunk sections in
    view.
* A player's box is 0.6 × 1.8 blocks. A 3D rod held by a custom `IItemRenderer` reaches 2-3 blocks out of it.
* With the player just outside the view and the rod still in it, the player is skipped, and the rod with it.
* The fishing line belongs to the hook, another entity, so it is still drawn. Kunftig starts the line at the rod's tip
  captured while drawing the rod. With no rod drawn, that tip expires after a few frames and the line falls back to
  vanilla's line from the hand.

## Fix

`HeldItemCullTransformer` (client only, registered last, after beta.51's `GeckoAnimationCopyTransformer`) +
`HeldItemCullHooks`:

| Target | Edit |
|---|---|
| `RenderGlobal.renderEntities` | Both `entity.boundingBox` reads that feed `ICamera.isBoundingBoxInFrustum` become `DUP; GETFIELD; INVOKESTATIC cullBox(Entity, AxisAlignedBB)`. |
| `CeleritasWorldRenderer.isEntityVisible` (Angelica) | The box local goes through `cullBox` right before `isInfiniteExtentsBox(box)`, after the null fallback: `ALOAD entity; ALOAD box; INVOKESTATIC; ASTORE box`. |

What `cullBox(entity, box)` returns:
* `box` itself, unless the entity is living and holds an item whose `IItemRenderer` handles `EQUIPPED`.
  * In TEOF, Kunftig answers that only for rods with a 3D model.
* Otherwise, a new box grown by `-Dakashic.heldItemCull.margin` blocks (default 3, at most 16) on every side.

What does not change:
* The entity's own box (collisions, picking with the mouse).
* Every entity without such an item.
* The distance check (`isInRangeToRender3d`).

How it fits with the rest of the pack:
* No branch is added.
* ThePatcher sees Minecraft classes before FML's deobfuscation, so `RenderGlobal` is matched by notch names mapped
  through FML's remapper.
* Angelica's mixins are applied later, on the patched bytes. The `GETFIELD` and the frustum call stay where they were,
  so their injection points still match.
* The hooks live in ThePatcher's excluded package, so they use SRG member names.

Fails closed per class:
* The method must exist once with the vanilla/Angelica descriptor.
* It must hold exactly the expected sites: 2 in `renderEntities`, 1 in `isEntityVisible`.
* The edited method must pass the ASM `BasicVerifier`.
* Otherwise the class is kept as it is and the log says why.

Kill-switch: `-Dakashic.heldItemCull.disable=true`.

## Offline gates (all PASS, `reports/final-build-audit.json`)

* **Build:** deterministic double build.
  * 4 entries changed (plugin, mod class, manifest, `mcmod.info`).
  * 3 classes added (`HeldItemCullTransformer`, its `Names`, `HeldItemCullHooks`).
  * 187 entries byte-identical to beta.51. Class major <= 52.
* **Plugin:** only `getASMTransformerClass` differs.
* **Registration on the JVM verifier:** client = beta.51's list + `HeldItemCullTransformer` last; server = beta.51's
  list.
* **`HeldItemCullGate`** (`-Xverify:all`), on shapes of 1.7.10's `renderEntities` culling (entity test, then the leash
  holder) and of Angelica's `isEntityVisible`, loaded through the shipped transformer. The view covers x in [0, 10].
  * beta.52: a player just outside the view holding a 3D item is drawn by both tests. A mob out of view on a leash
    held by such a player is drawn too.
  * Not drawn: empty hand, an item whose renderer does not handle `EQUIPPED`, a plain item, a player 10 blocks out, a
    non-living entity.
  * The entities' own boxes are untouched.
  * beta.51 and the kill-switch draw only what is in view.
  * A `renderEntities` with one frustum test is skipped with the warning.
* **Real Angelica:** the real `CeleritasWorldRenderer` of Angelica **2.2.37** and **2.2.40** is patched once, with
  `cullBox` right before `isInfiniteExtentsBox`, and every method passes the verifier. A second pass changes nothing.

## Real client (TEOF 4.8, headless, Angelica 2.2.43)

AkashicFishing 1.4.0 rods + Kunftig (3D rods) + GeckoLib Unofficial 1.0.4 + ThePatcher beta.52, harness "edge" mode:
another player stands at the left edge of the view, holding the Akáshica irídio m6 across it (pointing right), and the
camera turns right in steps (0, 6, 10, 14, 18 and 24 degrees) until the body has left the view. The same is then done
with the line out (the hook in view).

* `latest.log`:
  * `Held item cull box applied: net.minecraft.client.renderer.RenderGlobal (2 frustum test(s) in renderEntities).`
    This is the notch class, mapped through FML.
  * `Held item cull box applied: com.gtnewhorizons.angelica.rendering.celeritas.CeleritasWorldRenderer (isEntityVisible).`
  * `Held item cull box: first grown box (net.minecraft.client.entity.EntityOtherPlayerMP holding net.minecraft.item.ItemFishingRod, +3.0 blocks).`
* **beta.51, same instance (the bug):** once the body leaves the view (14 degrees and more), the 3D rod is gone. Only
  vanilla's black line is left, coming from the hand outside the view.
* **beta.52:** the rod stays at every step until it leaves the view itself, and the line keeps starting at the rod's
  tip.
* No ThePatcher, Kunftig or GeckoLib exception in the log.

## Acceptance gate in the pack

`logs/fml-client-latest.log` / `latest.log`:
* `Held item cull box applied: net.minecraft.client.renderer.RenderGlobal (2 frustum test(s) in renderEntities).`
* `Held item cull box applied: com.gtnewhorizons.angelica.rendering.celeritas.CeleritasWorldRenderer (isEntityVisible).`
* `Held item cull box: first grown box (...)` the first time a player with a 3D rod is near.
* Never `Held item cull box skipped` / `rejected` / `turned off`.

In game: another player holding a 3D rod at the side of the screen keeps the rod (and the line from its tip) until
the rod itself leaves the view. Rollback: beta.51, or `-Dakashic.heldItemCull.disable=true`.
