# beta.53 - NeoStatesDragonApiWaterPass - 2026-11-16

`!Akashic-ThePatcher-1.1.0-beta.53-NeoStatesDragonApiWaterPass.jar`, SHA-256
`17ab2facd1e35ef1871987731e6ca4bbf9a0b68237d3d29dd0a23e18084905e3`, 426176 B, 196 entries. Base: beta.52
(`9fb5ae22...`). Built and gated by `tools/beta53-build.py` (report: `reports/final-build-audit.json`).

## Problem

A TEOF 5.6 player with an Intel HD Graphics 2000 (i5-2400, OpenGL 3.1, last driver 9.17.10.4459) crashed at start-up:
Angelica needs an OpenGL 3.3 core context (`Pixel format not accelerated`). The Forbidden Series launcher got a
compatibility mode that leaves Angelica, its atlas fix and Mirai (Distant Horizons) out of `mods/` on such GPUs. With
Angelica gone, the client then stopped in pre-init:

```
InjectionError: Critical injection failure: Callback method neostates$holdersNeedTheWaterPass(...) in
mixins.neostates.early.json:client.MixinWorldRenderer_WaterPass from mod neostates failed injection check, (0/1)
succeeded. Scanned 0 target(s).
```

Cause, read in the bytecode of the pack's jars:

* Neo States (Akashic, 0.1.0-alpha.10) adds `client.MixinWorldRenderer_WaterPass` **only when Angelica is absent**
  (`NeoStatesCore`: `present("com/gtnewhorizons/angelica/AngelicaMod.class")`). It is a required
  (`defaultRequire = 1`) `@WrapOperation` on `Block.getRenderBlockPass()` inside `WorldRenderer.updateRenderer`: a
  block that may hold water asks for the translucent pass, or its water is skipped in a section with no other
  translucent block. With Angelica in the pack it never ran, so it was never seen with DragonAPI.
* DragonAPI V33b's coremod (`ASM/Patchers/Hooks/Event/Render/RenderBlockEvent`) rewrites that very call in
  `updateRenderer` into `RenderBlockAtPosEvent.getMaxRenderPass(block, x, y, z)` (and `canRenderInPass` into
  `checkCanRenderPass`). `getMaxRenderPass` calls `block.getRenderBlockPass()` itself (`func_149701_w`, first
  instruction) and keeps the highest pass of its listeners.
* So the injection finds no call to wrap, and Neo States fails the start, as designed for a moved target.

## Fix

`NeoStatesWaterPassTransformer` (client only, registered last, after beta.52's `HeldItemCullTransformer`,
`@MixinSafeTransformer` so UniMixins' Mixingasm lets it see mixin classes, beta.39's lesson) +
`NeoStatesWaterPassHooks`.

Armed once, only when all hold (else it logs why and changes nothing):
* Neo States is installed (`com/akashic/neostates/api/NeoStates.class`) and DragonAPI is
  (`Reika/DragonAPI/Instantiable/Event/Client/RenderBlockAtPosEvent.class`).
* Angelica is absent (the same resource test Neo States does: with Angelica, Neo States does not add the mixin).
* Neo States' mixin has the shape this fix knows: exactly one `@WrapOperation`, on the vanilla call. A Neo States that
  wraps DragonAPI's hook itself (an upstream fix) leaves both classes alone.

| Target | Edit |
|---|---|
| `com.akashic.neostates.mixin.early.client.MixinWorldRenderer_WaterPass` (as Mixin reads it) | The `@WrapOperation` of `neostates$holdersNeedTheWaterPass` (`method = updateRenderer`, `at = INVOKE Block.getRenderBlockPass()I`) gets `require = 0`. Nothing else of the class changes; it still applies wherever the vanilla call is there. |
| `Reika.DragonAPI.Instantiable.Event.Client.RenderBlockAtPosEvent.getMaxRenderPass(Block, int, int, int)` | Right after its single `block.getRenderBlockPass()` call: `ALOAD 0; INVOKESTATIC NeoStatesWaterPassHooks.waterPass(I, Block)I`. |

`waterPass(pass, block)` is Neo States' rule at the same place: `pass < 1` and Neo States' public API says the block may
hold water (`NeoStates.canHoldWater`) -> 1, else `pass`. The listeners' `Math.max` still runs after it. Result: the same
sections get the translucent pass as with Neo States on the vanilla call; nothing else changes, and with Angelica
nothing here runs.

Fails closed per class: exact name, descriptor and annotation values; exactly one call site, on the block parameter;
the edited method passes the ASM `BasicVerifier`; a second pass changes nothing. Kill-switch
`-Dakashic.neoStatesWaterPass.disable=true` (then the original crash comes back without Angelica).

## Offline gates (all PASS, `reports/final-build-audit.json`)

* **Build:** deterministic double build. 4 entries changed (plugin, mod class, manifest, `mcmod.info`), 2 classes added,
  190 entries byte-identical to beta.52. Class major <= 52.
* **Plugin:** only `getASMTransformerClass` differs.
* **Registration on the JVM verifier:** client = beta.52's list + `NeoStatesWaterPassTransformer` last; server =
  beta.52's list.
* **Mixingasm:** trusted with the real `+unimixins-all-1.7.10-0.3.1.jar`; without UniMixins the class still loads.
* **`NeoStatesWaterPassGate`** (`-Xverify:all`), on the **real Neo States 0.1.0-alpha.10 mixin source** (compiled
  against annotation stubs) and DragonAPI V33b's `RenderBlockAtPosEvent` shape (from `javap` of the TEOF 5.6 jar):
  * beta.53 with Neo States + DragonAPI, no Angelica: the mixin gets `require=0` and nothing else changes (same methods
    and code), a second pass is a no-op; `getMaxRenderPass` gives 1 for a holder block in the opaque pass, 0 for an
    opaque block, 1 for a translucent one, and a listener's higher pass still wins.
  * Untouched (same bytes, Neo States never asked): kill-switch; Angelica 2.2.40 on the class path; no DragonAPI; an
    upstream-fixed Neo States (wraps DragonAPI's hook itself); beta.52.

## Real client (TEOF 5.6, Forbidden Series launcher end-to-end, OpenGL 3.1)

The launcher's "PC fraco (OpenGL 3.1)" job (AkashicLauncher `.github/workflows/e2e.yml`): Linux, Xvfb, Mesa capped with
`MESA_GL_VERSION_OVERRIDE=3.1` (as the Intel HD 2000), the real TEOF 5.6 release installed by the launcher, compatibility
mode on (Angelica, its atlas fix and Mirai left out of `mods/`), this JAR in place of beta.52, and the pack's own
**Neo States 0.1.0-alpha.10** (unchanged).

* Without the compatibility mode (and beta.52): Angelica's `OpenGL 3.3 core profile` crash, as the player.
* Compatibility mode + beta.52: `Critical injection failure ... MixinWorldRenderer_WaterPass ... Scanned 0 target(s)`.
* Compatibility mode + **beta.53**: the log shows
  * `NeoStates x DragonAPI water pass fix armed (Neo States and DragonAPI without Angelica).`
  * `NeoStates x DragonAPI water pass fix applied: com.akashic.neostates.mixin.early.client.MixinWorldRenderer_WaterPass (neostates$holdersNeedTheWaterPass require=0).`
    (Mixin accepted the rewritten mixin: the transformer is trusted.)
  * `NeoStates x DragonAPI water pass fix applied: Reika.DragonAPI.Instantiable.Event.Client.RenderBlockAtPosEvent (getMaxRenderPass: 1 site).`
  * and the game goes on to `Forge Mod Loader has successfully loaded ... mods` (the job's pass condition).
* The same run with Angelica (the normal "JOGAR com o pack publicado" job) is unaffected.

Not yet seen: a player in the world (water in slabs with no other translucent block nearby) and the server join on the
real Intel HD 2000.

## Acceptance gate in the pack

Without Angelica (compatibility mode), `logs/fml-client-latest.log` / `latest.log`:
* `NeoStates x DragonAPI water pass fix armed (Neo States and DragonAPI without Angelica).`
* `NeoStates x DragonAPI water pass fix applied: com.akashic.neostates.mixin.early.client.MixinWorldRenderer_WaterPass (neostates$holdersNeedTheWaterPass require=0).`
* `NeoStates x DragonAPI water pass fix applied: Reika.DragonAPI.Instantiable.Event.Client.RenderBlockAtPosEvent (getMaxRenderPass: 1 site).`
* `Forge Mod Loader has successfully loaded N mods`; water in a slab with no other translucent block nearby is drawn.
* Never `... skipped` / `rejected`.

With Angelica (everyone else): one line `NeoStates x DragonAPI water pass fix not needed: Angelica present ...` and
nothing else. Rollback: beta.52 (with Angelica), or `-Dakashic.neoStatesWaterPass.disable=true`.
