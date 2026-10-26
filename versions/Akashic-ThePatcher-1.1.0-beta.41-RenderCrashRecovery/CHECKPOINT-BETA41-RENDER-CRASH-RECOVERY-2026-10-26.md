# beta.41 - RenderCrashRecovery - 2026-10-26

## Incident
Five client crash reports from 2026-10-25, all with beta.40, Angelica 2.2.37, DragonAPI V33b (`ForbiddenFix1`), ChromatiCraft V33a, Waystones-X 1.0.15 and CoreTweaks 0.3.4.7-GTNH, Java 8u51, GTX 550 Ti. Selected resource packs: `[1.7.10]MC-New-Textures[v2.0.2].zip`, `Waystones Modernity Textures`.

| Time | Exception | Where |
|---|---|---|
| 10:07 | `NullPointerException` | `RenderGlobal.renderSky` (`RenderGlobal.java:1024`) |
| 10:19 | `ClassCastException: WaystonesAlternateResourcePack cannot be cast to AbstractResourcePack` | `ReikaImageLoader.getImageFromResourcePack:94` <- ChromatiCraft `RenderLootChest` (world TESR, via `celeritas$renderTileEntities`) |
| 10:21 | `IllegalStateException: GbufferPrograms in weird state, tried to call begin function when entities = false, blockEntities = true, outline = false` | `GbufferPrograms.beginOutline` <- `RenderGlobal.drawSelectionBox` (lobby, another server behind the same proxy) |
| 10:22 | same as 10:21 | same |
| 18:27 | same `ClassCastException` as 10:19 | ChromatiCraft `RenderColoredAltar` via `AltarItemRenderer` in the creative inventory |

The reports have CoreTweaks' `tweak.crashhandler.MixinMinecraft` in the stack: the game did not close, CoreTweaks wrote the report and returned to its error screen.

## Root cause 1 - DragonAPI x Waystones' resource pack (10:19, 18:27)
DragonAPI looks for every mod texture in the selected resource packs first and casts each selected pack to `AbstractResourcePack` without a check (`ReikaImageLoader.getImageFromResourcePack`, and the same in `ReikaTextureHelper.initializeColorOverrides`), because it reads files by raw path through `AbstractResourcePack.getInputStreamByName`. Waystones registers its "Modernity Textures" as a `WaystonesAlternateResourcePack`, which implements `IResourcePack` only. While that pack is selected, the first ChromatiCraft/RotaryCraft TESR that binds a DragonAPI texture throws, in the world and in the creative tab. Immediate workaround without beta.41: deselect "Waystones Modernity Textures".

## Root cause 2 - CoreTweaks recovery leaves Iris in the block-entity phase (10:21, 10:22)
Angelica 2.2.37's `celeritas$renderTileEntities` (`mixins/early/celeritas/terrain/MixinRenderGlobal`) calls `GbufferPrograms.beginBlockEntities()`, renders all TESRs and then `endBlockEntities()`, with no `finally`. The 10:19 exception skipped `endBlockEntities`, so the static flag `blockEntities` stayed `true`. Angelica resets its render state after a recovered crash only through BetterCrashes (`BetterCrashesCompat` -> `RenderRecovery.resetAfterCrash()`); CoreTweaks' `CrashHandler.resetState()` resets GL and the Tessellator only, and even `resetAfterCrash` does not touch `GbufferPrograms`. From then on every `beginOutline` (looking at a block) threw, until the game was restarted. This is the exact message, reproduced offline with the real Angelica 2.2.37 class.

## Not explained - 10:07 renderSky NPE
The NPE is thrown inside `renderSky` itself (no deeper frame), in the overworld of the survival server. With the crash report alone it is not possible to say which reference was null (the class is patched by Forge and several mixins; the line number does not map to a single statement). It was recovered by CoreTweaks and the game ran for 12 more minutes. beta.41's Angelica reset after a recovered crash may help with follow-up crashes, but nothing in this build targets it. Needed: `logs/fml-client-latest.log` of a session where it happens, with what was going on (server switch, dimension change, world load). See OPEN-ISSUES.

## Fix
Two new client transformers, registered in `AkashicThePatcherLoadingPlugin.getASMTransformerClass` right after `NecroTempusAngelicaFontTransformer` (`tools/PatchBeta41Classes.java`):

* `DragonApiResourcePackTransformer` (+ `DragonApiResourcePackHooks`):
  * `ReikaImageLoader.getImageFromResourcePack` starts with `if (DragonApiResourcePackHooks.isUnsupportedPack(res)) return null;` - `null` already means "this pack does not have the image": DragonAPI tries the next pack and then the mod's own texture;
  * in `ReikaTextureHelper.initializeColorOverrides`, the `getCurrentResourcePacks()` result is filtered by `withoutUnsupportedPacks`.
  * One warning per pack class: `DragonAPI resource pack guard: resource pack <class> is not an AbstractResourcePack; DragonAPI skips it ...`. Fail-closed: only methods that still have the unchecked cast are edited, and only if the result passes the ASM verifier. Kill-switch `-Dakashic.dragonApiResourcePack.disable=true`.
* `CoreTweaksCrashRecoveryTransformer` (+ `AngelicaCrashRecoveryHooks`): CoreTweaks' `CrashHandler.resetState()` calls `AngelicaCrashRecoveryHooks.afterCoreTweaksReset()` before it returns, which
  1. runs Angelica's own `RenderRecovery.resetAfterCrash()` (what Angelica does for BetterCrashes: GL state manager, display-list compilation, Iris pipeline, TESR batches, fonts, HUD cache); `-Dakashic.crashRecoveryBridge.angelicaReset=false` skips this step;
  2. puts `GbufferPrograms`' phase bookkeeping back to its between-frames values (`entities`, `blockEntities`, `outline`, `entityLoop`, `particles`, `particleDepth`, the particle save arrays, the override-phase and special-condition stacks, the declared translucency) and logs what the crash had left set.
  * Every step catches its own failure (the crash handler must never crash); fields and classes are looked up by name and skipped when absent. Kill-switch `-Dakashic.crashRecoveryBridge.disable=true`.

Everything is client-only; the server transformer list is unchanged.

## Gates (reports/final-build-audit.json) - all PASS
* Deterministic double build, 165 entries; vs beta.40: plugin, mod class, manifest, mcmod.info changed; 4 classes added; the other 157 byte-identical; class major <= 52; `MethodDiff`: only `getASMTransformerClass` differs in the plugin.
* Registration (JVM `-Xverify:all`): client = beta.40's 18 names + the two new ones after NecroTempus; server = beta.40's 6.
* `DragonApiGate` (a test reconstruction of the two DragonAPI methods' bytecode shape - the real DragonAPI jar is not redistributable - loaded through the bundled transformer, `-Xverify:all`), with the crash report's pack list `[MC-New-Textures, Waystones Modernity Textures]`:
  * beta.40, beta.41 with the kill-switch, beta.41 on the server side: `ClassCastException` in `bindTexture` and in the dye colours - **crash reproduced**;
  * beta.41: the texture comes from MC-New-Textures, the dye colours too, with only the Waystones pack DragonAPI falls back to the mod texture.
* `CrashRecoveryGate` (the **real** Angelica 2.2.37 `GbufferPrograms`): `blockEntities = true` gives exactly the 10:21 message; after CoreTweaks' `resetState()` (shape reconstruction, transformed):
  * beta.41: no exception, all flags idle, the log says `left set by the crash: [blockEntities=true]`, `RenderRecovery.resetAfterCrash` called once; also when `RenderRecovery` throws, when CoreTweaks' own step fails, with `angelicaReset=false` (0 calls), and with Angelica's **real** `RenderRecovery` (which cannot run outside the game: its error is logged, the Iris reset still happens);
  * beta.40, beta.41 with the kill-switch, the server side: the exception stays.
* `RealCoreTweaksGate`: the bundled transformer on the **real** CoreTweaks 0.3.4.7-GTNH `CrashHandler`: one hook before the single `RETURN` of `resetState()`, every other instruction and method unchanged, ASM verifier passes.

Not covered offline: the real DragonAPI V33b-ForbiddenFix1 bytecode (the shape gate follows DragonAPI's published source, whose line numbers match the crash report: 94, 249, 160); a real client run.

## Live acceptance gate
Full pack, "Waystones Modernity Textures" selected:
1. `logs/fml-client-latest.log` shows `DragonAPI resource pack guard applied: Reika.DragonAPI.IO.ReikaImageLoader ...` and `... ReikaTextureHelper ...` at start-up, and `CoreTweaks crash recovery bridge applied: makamys.coretweaks.tweak.crashhandler.CrashHandler...` the first time CoreTweaks handles a crash.
2. Look at a ChromatiCraft loot chest / altar in the world and in the creative tab: no crash; one `DragonAPI resource pack guard: resource pack net.blay09.mods.waystones.client.resource.WaystonesAlternateResourcePack is not an AbstractResourcePack ...` warning.
3. After any recovered crash, re-join and look at a block: no `GbufferPrograms in weird state`; the log has `Iris GbufferPrograms reset; left set by the crash: [...]`.

Rollback: beta.40 and deselect "Waystones Modernity Textures"; restart the game after any CoreTweaks error screen.
