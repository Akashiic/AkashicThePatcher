# History - the story of AkashicThePatcher

A chronological narrative, September 2026. For per-build detail read [CHANGELOG.md](CHANGELOG.md); for hashes and parents read [LINEAGE.md](LINEAGE.md). Dates are JAR modification times (UTC-3) - see [PROVENANCE.md](PROVENANCE.md) for how reliable that is.

**Cast:** *Forbidden Series* is a Minecraft **1.7.10** modpack (Technic launcher, Java 8u51, ~hundreds of mods, behind a Bukkit/Spigot-style proxy network). AkashicThePatcher is the **client-side** bundle of bug-fix coremods that the pack carries so players do not have to chase each crash by hand. It is a Forge/LaunchWrapper coremod plus Mixins (UniMixins) that patches *other people's* mods at class-load time.

## Phase 0 - the base bundle (before 2026-09-08)

beta.1 and beta.2 (and later beta.5, beta.6) are not on disk. The oldest surviving build is **beta.3**: a "unified client compatibility and handoff patch bundle" that already consolidated many previously separate small mods - RGB chat/text colour support, the Backhand (offhand) bridge, Baubles, the server handoff, a hologram renderer, the legacy item/block register, a MOTD module, and guards for Botania, Mekanism, IndustrialUpgrade, NecroTempus and the sampler HUD. Every module has a kill-switch in `config/akashic-thepatcher.properties`.

## Phase 1 - the DimDoors crash and the first overlay (2026-09-08 .. 09-22)

At 03:13 on 2026-09-08 a client crashed inside Dimensional Doors 2.2.6 (`PocketManager.getDimensionData`): the server sent a door-link update before the client had a dimension map. **beta.4** fixed it by *deferring* that notification until the map exists. It was built as a byte-reproducible **overlay** on beta.3 - this "previous jar + overlay classes + patched version strings" model is how nearly every later release was made. beta.4 was approved only for isolated homologation, and it is a **side branch**: beta.7 (2026-09-14) solved the same problem again with a different design, together with the RGB format work, and never contained beta.4's classes. beta.8 (09-22) added Angelica text compatibility and became the **verified production baseline**, plus the 16-gate regression suite every later build had to pass.

## Phase 2 - the white-world hypotheses (2026-09-23)

With the Angelica 2.2.18 (GTNH rendering) + Celeritas stack, blocks in the world rendered white or translucent. Two guesses were built as experiments:

* **beta.9** - Numina's `RenderState` push/pop vs Angelica's state tracking. Live test: the target class was never even loaded. Abandoned.
* **beta.10** - the Akashic Cinematic Menu's `RawScope` leaking `GL_TEXTURE_2D`, plus a fail-soft Angelica VSync call. Both patches ran; the world stayed white.

Both were then removed from the baseline so they would not be variables in the A/B configuration tests kept in `investigations/Angelica-Celeritas-AB-2026-09-23/`. Lesson recorded: *a mismatch in GL state is not a bug unless the state is not emulated by Angelica's fixed-function shader.*

## Phase 3 - startup time and the Forestry crash (2026-09-24, morning to afternoon)

Booting took ~7 minutes. The standalone AsyncCompat mod (hybrid asynchronous icon loading) was merged into ThePatcher as **beta.11**, with owner roots known to be thread-unsafe (Chisel, Mekanism, MineFactory Reloaded, CoFH) forced onto the client thread. The same build restored protection against a native **HotSpot C2 crash** in Forestry's `Genome.getChromosomes` (Java 8u51). Then came a measurement ladder, each step answering the previous one's numbers:

| build | what it did | headline number |
|---|---|---|
| beta.12 | atlas profiler | blocks stitch 18.8 s, items 11.2 s |
| beta.13 | persistent stitch-layout cache | FML->menu 7m06 -> **6m24** |
| beta.14 | upload profiler | warm boot 5m00; GL upload ~30 s |
| beta.15 | mipmap probe | uploads are all mip L0 |
| beta.16 x2 | Forestry fix, two designs | Mixin shim vs transformer |
| beta.17 | Forestry transformer chosen | zero-config fix |
| beta.18/19 | items atlas upload batching (+ packaging fix) | 15,980 GL calls -> 16 |
| beta.20 | lifecycle profiler | found a ~110 s regression before final reload |

Two decisions are visible only in the artifacts, not explained: the alternate beta.16 documentation calls the Mixin variant "canonical" while beta.17/20 adopt the transformer and explicitly forbid the Mixin; and beta.20 added the **pack-side** requirement of six JVM CompileCommand exclusions (delivered through the launcher pack's `version.json`) because a stale launcher process had kept old JVM arguments in memory.

## Phase 4 - hunting the world render bug (2026-09-24 evening .. 09-25 early)

beta.20's profiler Mixin crashed ("`LoadController` loaded too early") and was removed in beta.21, which instead put a **GL-state probe with conditional repair** on Celeritas' chunk draw. beta.22 fixed a buffer-size contract in that probe (LWJGL2 wants >=16 elements). Its output showed only emulated states diverging -> the GLSM cache is not the cause. beta.23 removed the probe and ran config A/Bs (VAO off, texture filtering off): still translucent. beta.24 tested persistent-mapped staging buffers (built, not installed); beta.25 forced opaque-terrain blend off and probed vertex alpha (two sibling variants exist; the "Blend/Alpha" one was inherited); beta.26 recorded draw submissions; beta.27 forced Angelica fast chunk tracking behind a VANILLA/BUKKIT proxy; beta.28 added a finite-FOV guard on `ForgeHooksClient.getOffsetFOV`, pointing at Numina 0.4.1.105 `FOVUpdateEventHandler`. Two other builds with the same beta.28 label were **rejected** and are kept for evidence.

**Honest status:** no document in this repository records that the white/translucent world was confirmed fixed. See [OPEN-ISSUES.md](OPEN-ISSUES.md).

## Phase 5 - startup audits and the window incident (2026-09-25)

* **beta.29** - a purely passive audit of startup (atlas batch eligibility, FML callbacks, a handful of slow spans). Its data led to **beta.30**: batching the *blocks* atlas upload (19,336 GL calls -> a few).
* beta.30 had a shadow-field defect and then a reobfuscation defect (`NoSuchMethodError Minecraft.getMinecraft`). **beta.30.1** and **beta.30.2** are the same feature fixed twice; all three declare beta.28 as base (side/superseded chain). beta.30.2 is what got installed.
* **beta.31/32** - early ASM per-phase FML audit and 20 named hotspot spans. Diagnostics only.
* **beta.33** - the client was found alive with no window (`MainWindowHandle=0`); `setDisplayMode` + `setFullscreen` were merged into one LWJGL call.
* **beta.34** - a crash while registering textures was traced to Ars Magica 2 icon code running on a worker thread; async *item* icons became opt-in.
* **beta.35 "BackhandLifecycleFix"** (23:54). Only the Backhand bytecode transformer changed (obfuscated-name constants). Source of this build exists only as a decompilation; the reasons are not written anywhere.
* **beta.36 "ForestryC2CompileBarrier"** (2026-10-24). Launched through SKLauncher, the pack lost the six JVM CompileCommands (they only lived in Technic's `version.json`) and the client died natively right after joining. The Genome helpers are now padded past HotSpot's `HugeMethodLimit`, so Java 8u51 never JIT-compiles them, whichever launcher is used.
* **beta.37 "ForestryGenomeBarrier"** (2026-10-24). The definitive form of that fix: the barrier now goes on **Forestry's own, unchanged** `Genome.getChromosomes`. The beta.17 reimplementation and its fingerprint lock are gone, and results were proved identical to Forestry on 8u51/8u462/21 with the real Forestry classes.
* **beta.38 "AngelicaFontCompat"** (2026-10-25). Updating Angelica to 2.2.37 crashed the client at start-up (`NoClassDefFoundError: BatchingFontRenderer`): Angelica 2.2.24 moved its font API to GL texture ids and NecroTempus' required Angelica mixin no longer matched. ThePatcher now rewrites NecroTempus' mixin and providers at load time (fail-closed, kill-switch), and the RGB whitespace underline fix got the Angelica 2.2.37 profile. A source patch for NecroTempus upstream is kept with the build.
* **beta.39 "AngelicaFontMixinTrust"** (2026-10-25). The first real-client run of beta.38 (a new headless test client: Forge + Mesa + Xvfb) still crashed: UniMixins' Mixingasm keeps untrusted transformers away from mixin classes, so the rewrite never happened. One annotation (`@MixinSafeTransformer`) fixed it; the same harness then showed the splash, the cinematic menu and no crash with NecroTempus 2.0.0 and 2.1.0. Lesson: offline gates for anything that touches mixin classes must include Mixingasm.
* **beta.40 "ItemsAtlasAngelicaGuard"** (2026-10-25). In the pack with Angelica 2.2.37, items vanished from NEI: Angelica 2.2.24 started uploading atlas sprites itself, so beta.18's items-atlas batcher staged almost nothing and then uploaded its empty copy over the whole atlas. The batcher now stands aside when Angelica's `AtlasAssembler` is present and never uploads a batch it did not withhold. Lesson: a batcher that owns the whole texture must check that it saw every upload.
* **beta.41 "RenderCrashRecovery"** (2026-10-26). A morning of crash reports: DragonAPI cast Waystones' "Modernity Textures" pack to `AbstractResourcePack` and crashed every ChromatiCraft TESR; CoreTweaks kept the game running, but Iris was left in its block-entity phase, so every block outline crashed again until a restart. DragonAPI now skips packs it cannot read, and CoreTweaks' recovery now runs Angelica's own reset plus a reset of Iris' phase bookkeeping. Lesson: when a crash handler keeps the game alive, every static begin/end state machine in the render path has to be reset with it.
* **beta.42 "MiraiShaderBridge"** (2026-10-26). With Angelica's shaders on, Akashic: Horizons of The Mirai (the pack's Distant Horizons fork) drew no distant terrain. Three causes stacked up: Angelica's Iris looks for DH by the class name upstream DH adopted on 2026-09-10 (`DistantHorizonsTweaker`), which Mirai 0.1.19 predates; Mirai still had upstream's deferred translucent LOD pass commented out; and on Java 8 Angelica did not find the `world0/` folders inside `.zip` shader packs at all, so BSL (which has no `dimension.properties`) ran none of its programs and reported "no DH support". ThePatcher now bridges all three, and the separate elytra fix JAR of the same day was folded in, so the pack again needs one ThePatcher JAR. Lesson: an integration that switches itself on by probing a class name breaks silently when either side renames; and a shader pack that "loads" can still be running none of its programs.
* **beta.43 "HologramFastPath"** (2026-10-26). Many HolographicDisplays holograms dropped the FPS hard. On a 1.7 client every hologram line is a real mob: a horse aged -1700000 (a giant inverted model whose name tag lands back on the line) riding a wither skull that ray-traces and smokes every tick; the bundled hologram module of beta.3 had to force all of them visible because Angelica and EntityCulling cull the inverted horse box. A headless client replaying HolographicDisplays' packets measured it (24 FPS with 900 lines against 102 without), and ThePatcher now treats those carriers like a 1.21 client treats a text display: they stop ticking, leave the entity pipeline, and their text is drawn in one pass (48 FPS with the same 900 lines, the same lines visible). Lesson: measure with the real packets - the obvious A/B (turn the hologram module off) looked like a 4x win only because it made the holograms invisible.
* **beta.44 "Angelica240Decoration"** (2026-10-29). Updating Angelica to 2.2.40 cut short the struck-through separators of the tab list: Angelica still forgets to extend underline/strikethrough over spaces, and ThePatcher's fix for that (beta.38) only knows the `drawString` versions it was audited on, so it skipped 2.2.40. beta.44 adds the 2.2.40 profile; nothing else changes.
* **beta.45 "AgriCraftIrrigationGuard"** (2026-10-29). One player could not join the server at all: "Fatally missing blocks and items", always after the lobby. Their AgriCraft config had irrigation disabled, so their client never registered the five irrigation blocks the server has, and FML refuses a client that is missing ids. The client now always registers them, whatever the file says. Lesson: in a 1.7.10 pack every client-side config switch that decides whether a block or item exists is part of the network contract with the server.
* **beta.46 "ProjectorOffhandGuard"** (2026-10-29) - the latest. The same player, once in the server, crashed with `ArrayIndexOutOfBoundsException: 36` from Immersive Petroleum's projector HUD: it looks up the held projection by the selected slot in a 9-slot array, and Backhand points the selected slot at its offhand slot (36) while it runs an offhand action. Slot 36 now means "no projection". Their other report, a lobby that always dropped them with `EOFException`, was an old install of the pack: its Hodgepodge 2.7.189 cannot read the tile entity packets of Hodgepodge 2.7.194+.

## What the history teaches a new maintainer

1. Never trust a version string: several builds claim a base that is not their content-nearest neighbour. Use [LINEAGE.md](LINEAGE.md).
2. Every behaviour change ships with a kill-switch (`-Dakashic.*` or `module.*`).
3. Diagnostics and fixes are separate builds. Probes are removed again once they have answered their question.
4. Live acceptance is the weakest-documented step; write it down this time (see [HANDOFF.md](HANDOFF.md)).
