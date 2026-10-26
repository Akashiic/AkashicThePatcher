# beta.42 - MiraiShaderBridge - 2026-10-26

`!Akashic-ThePatcher-1.1.0-beta.42-MiraiShaderBridge.jar`, SHA-256 `245e67cbf8f82e4395a9706c21bbee2ebd94911774600aa1e06f188adc0415df`,
364197 B, 173 entries. Base: beta.41 (`8d7fddf3...`). Built and gated by `tools/beta42-build.py` (report: `reports/final-build-audit.json`).

## Problem

With Angelica 2.2.37 shaders on, **Akashic: Horizons of The Mirai 0.1.19** (`Akashic-Horizons-of-The-Mirai-forge17-0.1.19-dev-1.7.10.jar`,
SHA-256 `481d6f7b...986ea`, the pack's Distant Horizons 1.7.10 fork) drew no distant terrain through the shader pack.

## Diagnosis (three causes, each shown in a real client)

1. **Angelica does not see Mirai.** Angelica's Iris has Distant Horizons support (`net.coderbot.iris.compat.dh`, PR #1553, in 2.2.18,
   2.2.23 and 2.2.37). It switches on only when `Class.forName("com.seibel.distanthorizons.DistantHorizonsTweaker")` succeeds:
   `Iris.onRenderSystemInit` (then waits for DH's `DhApiAfterDhInitEvent` before loading the pack) and `DHCompat.run` (binds the DH API
   event handlers). Upstream DH 1.7.10 renamed its coremod to that name on 2026-09-10 (`dfca6541`, "Rename tweaker for Angelica
   compat"); Mirai is pinned at `b4abe2f`, before it, and ships `com.seibel.distanthorizons.forge17.DistantHorizonsLoadingPlugin`.
   Everything else matches: the 82 DH API references of Angelica 2.2.18/2.2.23/2.2.37 all resolve in the Mirai 0.1.19 JAR, and Mirai's
   core fires every event Angelica binds (`DhApiAfterDhInitEvent` is one-time and fires at client start in every renderer mode).
2. **Mirai never draws the deferred translucent LODs.** Upstream re-enabled `MixinRenderGlobal.renderDeferredLodsDuringTranslucentSetup`
   on 2026-09-10 (`ef720c8e`); Mirai still has it commented out ("TODO Fix"). With shaders Angelica sets
   `setDeferTransparentRendering(true)`, so without that call distant water would never be drawn.
3. **Angelica ignores the world folders of `.zip` shader packs on Java 8.** `ShaderPack` finds `world0/ world-1/ world1/` (packs without
   `dimension.properties`) with `Files.list(root)...map(Path::getFileName).map(Path::toString).filter(matches "world-?\\d+")`; Java 8's
   zip file system names directories `world0/`, nothing matches, and the pack loads with an empty program set (all its programs are
   in `world0/`). The pack runs Java 8 (`bin/1.7.10.json`: `jre-legacy`, 8). BSL 8.2.09 is such a pack: on Java 8 it ran none of its
   programs and Iris reported "No DH shader found in this pack" / "This pack doesn't have DH support". Complementary Unbound r5.9.3
   has a `dimension.properties` and is not affected. Not fixed in Angelica master as of 2026-10-26.

## Fix

| Class (client only) | What | Kill-switch |
|---|---|---|
| `module.mirai.MiraiShaderBridgeTransformer` (`@MixinSafeTransformer`) + `MiraiShaderBridgeHooks` | Armed only when Mirai is installed, `DistantHorizonsTweaker` is **not** (a Mirai rebased on upstream turns it off), and both Angelica probes have the exact shape (checked on both classes before either changes). The probe's LDC in `Iris` and `DHCompat` becomes Mirai's coremod class name. Mirai's `MixinRenderGlobal` (read by Mixin through the transformer chain, hence the Mixingasm trust of beta.39) gains upstream's `@Inject(method="sortAndRender", at=@At(value="INVOKE", target="...EntityRenderer;func_78463_b(D)V", shift=AFTER), require=0)`; the handler calls `RenderHelper.renderDeferredLods()` in pass 1 **only while DH's renderProxy defers transparent rendering**, i.e. while Angelica's DH support is active, so frames without shaders are unchanged (no deferred pass, no `CLIENT_DEFERRED_PASS_WALL` telemetry). | `-Dakashic.miraiShaderBridge.disable=true` |
| `module.angelica.AngelicaShaderPackFolderTransformer` + `AngelicaShaderPackFolderHooks` | The single `Path::toString` method reference of that stream (an `invokedynamic` handle in the `ShaderPack` constructor) becomes `AngelicaShaderPackFolderHooks.folderName(Path)`, which drops the trailing `/` (identity on Java 9+). Fail-closed: exactly one such reference, in a constructor, in a class with the `world-?\\d+` scan. | `-Dakashic.angelicaShaderPackFolders.disable=true` |
| `module.chroma.ChromaPlayerApiPreloadFix` | **Folded in:** Akashic-ChromaPlayerApiPreloadFix 1.0.0 (Et Futurum elytra take-off with ChromatiCraft + PlayerAPI, a separate coremod JAR until now, `projects/Akashic-ChromaPlayerApiPreloadFix/`). Same logic and nested Hide/Restore transformers; armed from ThePatcher's `injectData` on the client (ThePatcher sorts at 100300, after ChromatiCraft's 1001, and coremods are all injected before Mixin's DEFAULT phase). If the standalone JAR is installed and armed, this copy stays off and says so. | `-Dakashic.chromaPlayerApiPreload.disable=true` (same as 1.0.0) |

`AkashicThePatcherLoadingPlugin` (binary-only core bundle, edited by `tools/PatchBeta42Classes.java`): `getASMTransformerClass`
registers the two transformers right after CoreTweaks' (beta.41) on the client; `injectData` calls `ChromaPlayerApiPreloadFix.arm()`
before its single return (it checks the side itself, so no branch/frame is added). Server registration unchanged.

## Offline gates (all PASS, `reports/final-build-audit.json`)

* Deterministic double build; 4 entries changed (plugin, mod class, manifest, `mcmod.info`), 8 added, 161 byte-identical to beta.41;
  class major <= 52; only `getASMTransformerClass` and `injectData` differ in the plugin (MethodDiff).
* **One JAR, not separate ones:** `lineage_composition` walks beta.37 -> 38 -> 39 -> 40 -> 41 -> 42 and checks that every entry each
  build added or changed (19 entries) is in beta.42 as its newest version.
* Registration under `-Xverify:all`: client = beta.41 + the two new names after CoreTweaks'; server identical.
* `MiraiBridgeGate`: on the **real** `Iris`/`DHCompat` of Angelica 2.2.18, 2.2.23 and 2.2.37 exactly one instruction changes (the probe
  LDC, in `onRenderSystemInit` and `run`); on Mirai's **real** `MixinRenderGlobal` (0.1.19 JAR, `--mirai`) and on its shape the two
  existing injectors are unchanged and the handler is added with the expected annotation and body. Untouched for beta.41, kill-switch,
  server, no Mirai, upstream tweaker present, no Angelica.
* `HooksGate`: Mirai's deferred pass runs only for pass 1 while renderProxy defers (8 combinations).
* `LinkGate` (`--mirai`): the hooks' references and Angelica 2.2.18/2.2.23/2.2.37's DH API references resolve in the real Mirai JAR.
* `FolderGate`: the real `ShaderPack` of the three Angelica versions changes in exactly that one method handle; a shape with the same
  stream, transformed and **executed** on a `.zip` pack, finds `[world-1, world0, world1]` on Java 8, where beta.41 / kill-switch /
  server find `[]` (Angelica's bug reproduced); on Java 21 both find all three.
* `ChromaGate` (real `LaunchClassLoader` transformer list, ChromatiCraft shape): `[.., Hide, ChromatiCraft, Restore, ..]`; Mixin's
  pre-read of `EntityPlayerSP` without PlayerAPI's body passes and the patch stays pending; the real load applies `ENTITYPUSHOUT`
  once; beta.41 / kill-switch / server reproduce ChromatiCraft's throw; with the standalone JAR armed ThePatcher's copy stays off.
* CI (`.github/workflows/ci.yml`) rebuilds beta.42 without `--mirai` (the Mirai release is private) and compares it byte for byte.

## Real client (headless, `reports/real-client/`)

beta.39's headless client (Linux, Xvfb + openbox, Mesa llvmpipe GL 4.5, **Java 8u504**) with Forge 10.13.4.1614, UniMixins 0.3.1,
GTNHLib 0.11.51, Angelica 2.2.37 and Mirai 0.1.19 (all copied from TEOF-4.0-MANUAL, SHA-256 checked), the pack's Angelica configs,
and `tools/shader-probe/` (a probe mod that creates a world and logs Iris/DH state). 330 s per run, Mirai with its default config.

| Run (ThePatcher + shader pack) | Iris sees DH | pack folders | `dh_terrain` | Iris DH override | DH passes (last probe line) | LOD buffers in the translucent pass |
|---|---|---|---|---|---|---|
| beta.41 + BSL 8.2.09 `.zip` | **no** | `[]` (Angelica's Java 8 bug) | missing (so is `gbuffers_terrain`) | - | `OPAQUE_AND_TRANSPARENT` only (4930) | 0 |
| beta.42 + BSL 8.2.09 `.zip` | yes | `world0, world1, world-1` | present | `shouldOverride=true` | `OPAQUE` 938 + `TRANSPARENT` 938 | 77731 |
| beta.42, shaders off | (yes) | - | - | - | `OPAQUE_AND_TRANSPARENT` only (4368), as beta.41 | 0 |
| beta.41 + Complementary r5.9.3 `.zip` | **no** | `world0, world1, world-1` (`dimension.properties`) | present | none (no DH instance) | `OPAQUE_AND_TRANSPARENT` only (1880) | 0 |
| beta.42 + Complementary r5.9.3 `.zip` | yes | `world0, world1, world-1` | present | `shouldOverride=true` | `OPAQUE` 605 + `TRANSPARENT` 605 | 45203 |

Every beta.42 run with shaders logged `[AngelicaShaders]: DH Ready, binding Iris event handlers...`, `DH Iris events bound.` and
`Mirai x Angelica shader bridge: first deferred translucent LOD pass`; no run printed "No DH shader found" and none wrote a crash
report. Pictures (`reports/real-client/`): `complementary-horizon-beta41-vs-beta42.jpg` (y=120 at noon, same seed: beta.41 shows
only sky and fog beyond the vanilla distance - the reported symptom; beta.42 shows the distant land and ocean through the pack),
`bsl-zip-java8-beta41-vs-beta42-vs-noshader.jpg` (beta.41: BSL runs no program, the picture is broken; beta.42: BSL renders).
Key log lines, mod hashes and the last probe line of every run: `runs.json`. The two "view" runs use the probe with commands
enabled (teleport to y=120); in the first five the world was created without commands, so the player stayed on the ground.

Not covered: Windows / a real GPU, the full 212-mod pack, DH shadows (`dh_shadow`, neither pack has one), performance (llvmpipe),
the elytra fix in a real client after folding (the logic is unchanged; the 1.0.0 JAR passed the full-pack probe).

## Live acceptance gate (pack)

1. Remove `Akashic-ChromaPlayerApiPreloadFix-1.0.0.jar` from `mods/` if present; install beta.42 as the only ThePatcher JAR.
2. Mirai renderer enabled (`rendererMode` not `DISABLED`), shaders on with BSL 8.2.09 or Complementary r5.9.3.
3. `fml-client-latest.log`: `Mirai x Angelica shader bridge armed` + three `... applied`, `Angelica shader pack folder fix applied`,
   `[AngelicaShaders]: DH Ready, binding Iris event handlers...` / `DH Iris events bound.`, after joining a world
   `... first deferred translucent LOD pass`, and **no** `No DH shader found in this pack` / "This pack doesn't have DH support" in chat.
4. Visual: distant terrain beyond the vanilla render distance is lit/fogged by the shader pack; distant water appears.
5. Elytra: jump in mid-air with an elytra equipped opens it (log `ChromaPlayerApiPreload fix armed around ...`).
6. beta.41's gates (ChromatiCraft TESR with Waystones' pack; block outline after a recovered crash).
Rollback: beta.41 (+ the standalone elytra JAR).
