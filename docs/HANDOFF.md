# Handoff - how to continue this project

Written 2026-10-24 from a filesystem snapshot. Read [HISTORY.md](HISTORY.md) first (10 minutes), then this file.

## 1. Where things are

| You want | Go to |
|---|---|
| The installable JAR of any version | `releases/<tag>/*.jar` (hashes in `releases/SHA256SUMS.txt`, also GitHub Releases) |
| Which version came from which | [LINEAGE.md](LINEAGE.md), `data/lineage.json` |
| What each version did and why | [CHANGELOG.md](CHANGELOG.md) |
| Each version's original checkpoint / audit / tools | `versions/<folder>/` (the folder name appears in the tag's commit message and in `data/commit_plan.json`) |
| Source of the AsyncCompat component (Gradle) | `projects/` (10 snapshots; the highest alpha number is the latest) |
| Source of the beta.4 DimDoors overlay | `versions/_workspace-AkashicDev-ThePatcher-1.1.0-beta.4/` |
| Source of beta.35's changed class | `versions/Akashic-ThePatcher-1.1.0-beta.35-BackhandLifecycleFix/recovered-source/` (**decompiled**, not original) |
| The experiments and A/B configs behind the render hunt | `investigations/` |
| Analysis scripts (jar diffing, lineage, comparisons) | `analysis-tools/` |

## 2. Installing a build (what the author did)

1. Close Minecraft **and** the Technic launcher (a running process keeps the JAR locked and, for JVM arguments, keeps stale values in memory - this cost a debugging session in beta.11/20).
2. In the pack's `mods/` folder keep exactly **one** `!Akashic-ThePatcher-*.jar`.
3. Keep the previous JAR somewhere as rollback (every checkpoint lists its rollback set).
4. Start, then read `logs/latest.log` for the lines listed under the build's "acceptance gates" in the CHANGELOG / its checkpoint.

Current: `!Akashic-ThePatcher-1.1.0-beta.48-BackhandProxyReturn.jar`, SHA-256 `b750f8109dff722e2f91195573d5c80668555553645ee621fed0e95c6f4253e5` (Angelica 2.2.40, Hodgepodge 2.7.219 in the pack). (beta.38 must not be installed: see the CHANGELOG.)

## 3. Pack-side requirements that are NOT in the JAR

* **Forestry / Java 8u51 C2 crash protection.** Since **beta.36** this lives in the JAR. Since **beta.37**, Forestry's own unchanged `Genome.getChromosomes` is padded past HotSpot's `HugeMethodLimit`, so it is never JIT-compiled. The logs show `[Forestry-C2] Genome compile barrier armed`, then `Genome compile barrier applied: ... 288 -> 8484 bytes`. Kill-switch: `-Dakashic.forestryGenomeBarrier.disable=true`. No launcher setting is needed (this is what broke SKLauncher). The only thing that disables it is `-XX:-DontCompileHugeMethods`; do not add that flag. Up to beta.35 the guard required six JVM arguments (it logs `[Forestry-C2] Full CompilerOracle pack protection active (6/6)` when satisfied):

```
-XX:CompileCommand=exclude,forestry/core/genetics/Genome.getChromosomes
-XX:CompileCommand=exclude,com/akashic/thepatcher/module/forestry/ForestryGenomeCompat.loadChromosomes
-XX:CompileCommand=exclude,com/akashic/thepatcher/module/forestry/ForestryGenomeCompat.loadStoredChromosome
-XX:CompileCommand=exclude,com/akashic/thepatcher/module/forestry/ForestryGenomeCompat.repairInvalidAlleles
-XX:CompileCommand=exclude,com/akashic/thepatcher/module/forestry/ForestryGenomeCompat.validateChromosome
-XX:CompileCommand=exclude,com/akashic/thepatcher/module/forestry/ForestryGenomeCompat.fillMissingChromosomes
```

  The author delivered them through the **modpack's `version.json` `arguments.jvm`** (not the user launcher settings) with `investigations/forestry-versionjson-packfix/`. Every launcher setting file in the original folders contained a client id and server address, so none is committed - recreate yours.
* **Angelica 2.2.24+ and NecroTempus.** Since **beta.38** (working from **beta.39**, which is trusted by UniMixins' Mixingasm) ThePatcher rewrites NecroTempus' Angelica font mixin and providers at load time when the installed Angelica uses GL texture ids (2.2.24+). Logs: `NecroTempus Angelica font fix armed` / `... applied`. Kill-switch: `-Dakashic.necroTempusAngelicaFont.disable=true`; if you use it with Angelica 2.2.24+, set `angelicaGlyphsIntegration=false` and `angelicaModernFontsIntegration=false` in NecroTempus' config (category `Angelica`) or the client crashes at start-up. A NecroTempus release with the source fix in `versions/*beta.38*/necrotempus-source-fix/` makes the patch a no-op.
* **Angelica 2.2.24+ and the items atlas batcher.** Since **beta.40** the items-atlas upload batcher (beta.18) stays off when Angelica's `AtlasAssembler` is present (log `ItemsAtlasBatch bypass ... reason=angelica-atlas-assembler`); with beta.39 it erased the items atlas (invisible items). Kill-switch `-Dakashic.itemsAtlasBatch.guard=false`.
* **DragonAPI and resource packs that are not `AbstractResourcePack`** (Waystones' "Modernity Textures"). Since **beta.41** DragonAPI skips them instead of crashing ChromatiCraft/RotaryCraft TESRs (logs `DragonAPI resource pack guard applied: ...` and one `... is not an AbstractResourcePack ...` warning per pack class). Kill-switch `-Dakashic.dragonApiResourcePack.disable=true`; without beta.41, deselect that pack.
* **CoreTweaks' crash handler and Angelica.** Since **beta.41** a crash recovered by CoreTweaks also resets Angelica (`RenderRecovery.resetAfterCrash`) and Iris' `GbufferPrograms` phase state (logs `CoreTweaks crash recovery bridge ...`). Kill-switches `-Dakashic.crashRecoveryBridge.disable=true` (all) and `-Dakashic.crashRecoveryBridge.angelicaReset=false` (keep only the Iris reset). Without beta.41, restart the game after any CoreTweaks error screen.
* **Et Futurum elytra (ChromatiCraft + PlayerAPI).** Since **beta.42** the fix is inside ThePatcher (`ChromaPlayerApiPreloadFix`, armed from `injectData` on the client); remove `Akashic-ChromaPlayerApiPreloadFix-1.0.0.jar` from `mods/` (if both are installed, ThePatcher's copy stays off and logs why). Before beta.42 it was `projects/Akashic-ChromaPlayerApiPreloadFix/dist/Akashic-ChromaPlayerApiPreloadFix-1.0.0.jar`, a separate coremod. Without it, ChromatiCraft's `ENTITYPUSHOUT` patch throws while Mixin pre-reads `EntityPlayerSP`, Mixin drops Et Futurum's elytra take-off mixin, and the elytra never opens (log: `@Mixin target net.minecraft.client.entity.EntityPlayerSP was not found ... backlytra.client.MixinEntityPlayerSP`). Logs (ThePatcher) `ChromaPlayerApiPreload fix armed around ...ChromaASMHandler$ASMExecutor` / `... held back ChromatiCraft ENTITYPUSHOUT ...`. Kill-switch `-Dakashic.chromaPlayerApiPreload.disable=true`. Details in its README.
* **Angelica shaders with Akashic: Horizons of The Mirai (the pack's Distant Horizons).** Since **beta.42**: Angelica's Iris detects Mirai (log `Mirai x Angelica shader bridge armed` / `... applied` three times, then Angelica's `DH Ready, binding Iris event handlers...`), Mirai draws the deferred translucent LODs (`... first deferred translucent LOD pass` once a DH-capable pack runs), and on Java 8 `.zip` packs with `world0/` folders load (`Angelica shader pack folder fix applied`). Use a shader pack with `dh_terrain` programs (BSL 8.2.09 and Complementary Unbound r5.9.3 in the pack have them); a pack without them makes Angelica print "This pack doesn't have DH support" and DH then draws its own pass. Kill-switches `-Dakashic.miraiShaderBridge.disable=true` and `-Dakashic.angelicaShaderPackFolders.disable=true`. The bridge switches itself off when the installed DH already has upstream's `DistantHorizonsTweaker` (a Mirai rebased on upstream after 2026-09-10).
* **HolographicDisplays holograms (hologram fast path).** Since **beta.43** hologram carriers (horse aged <= -1000000, or an item/slime, riding a wither skull) stop ticking and their text is drawn in one pass (logs `Hologram fast path armed`, eight `Hologram fast path applied: ...`, then `Hologram fast path: first frame with N hologram text line(s) ...`). It needs nothing from the pack and works with or without the old hologram module (`module.hologram`; turning that off saves its 5-tick reflective entity scan). Kill-switch `-Dakashic.hologramFastPath.disable=true`; `-Dakashic.hologramFastPath.range=<blocks>` measures the text range from the line instead of the 1.7 rule. Benchmark harness (HolographicDisplays' packets replayed in a headless client): `versions/*beta.43*/tools/holobench/`.
* **Hodgepodge** `asyncIconLoading=false` (ThePatcher's own scheduler replaces it).
* **Backhand behind the proxy (return from the lobby).** Since **beta.48** the offhand keeps working after backend -> vanilla lobby -> backend on one proxy connection (before, Backhand ended the return in `TRANSITION` with `offhandAllowed=false`). Log of a good return: `remote FML mod list captured: type=MODDED` then `join game consumed fresh FML capability proof: type=MODDED ... offhandAllowed=true`. Kill-switch `-Dakashic.backhandProxyReturn.disable=true`.
* **Only Baubles Expanded ships `baubles/api/expanded`.** Some jar of the pack embeds an older copy of `IBaubleExpanded` that loads before `BaublesExpanded-2.2.26-GTNH.jar`; since **beta.47** ThePatcher adds the missing 2.2.26 method and logs `Baubles Expanded API guard applied ... Jars carrying this class, the first one is in use: [...]`. Clean the first jar of that list (compile-only API) so the guard has nothing to do.
* **AkashicBackpackCompat with Angelica 2.2.40.** Its `GlApi` looks GL methods up in `GLStateManager` by name; Angelica 2.2.40 moved them. Since **beta.47** ThePatcher resolves them in their new class (`Angelica GL lookup bridge applied ...`). The proper fix is in AkashicBackpackCompat (use `GLSMOwners` or plain `GL11` calls).
* **Hodgepodge 2.7.194+ on every client.** Hodgepodge 2.7.194 changed the wire format of `S35PacketUpdateTileEntity` (`directTileEntityArraySerialization`, default `true`, must match on both ends). A client on 2.7.193 or older is dropped as soon as it receives a tile entity update from a sender that has it: `DecoderException: java.io.EOFException` in `PooledGzipInputStream.readHeader` / `S35PacketUpdateTileEntity`, then `Bad packet id <n>` (beta.46's checkpoint). The pack ships 2.7.219; an old install of the pack shows exactly this in the lobby. ThePatcher cannot fix it.
* **Angelica 2.2.18 / GTNHLib 0.11.51** are the targets of several fail-closed patches. Different versions = those patches silently do nothing. beta.38 was checked against **Angelica 2.2.37**: the NecroTempus font fix and the whitespace decoration profile cover it, three AsyncCompat Angelica mixins still resolve, and `MixinAngelicaProxyVanillaChunkTracking` (beta.27) is skipped because Angelica 2.2.22 removed its target (see OPEN-ISSUES).

## 4. Building

* The Gradle projects in `projects/` need compile-time dependencies (Angelica, GTNHLib, UniMixins, ...) that are **not committed** (third-party). Put them in the project's `libs/` as each `build.gradle` expects. Use JDK 8-compatible output (class major <= 52) and the **`reobfJar`** artifact.
* **CI publishes releases.** Commit the new `releases/<tag>/` JAR together with its `SHA256SUMS.txt`, `data/lineage.json` (with `tag`/`jar`) and CHANGELOG entry and push to `main`: `.github/workflows/ci.yml` verifies, then creates the tag and the GitHub Release. A lineage note starting with "do not install" makes it a pre-release. Add the new build script to the workflow's rebuild steps when it is self-contained.
* To make a release: take the previous release JAR, replace/add the changed classes, patch version strings with `PatchThePatcherVersion.java`, build twice and compare SHA-256, then run the gates listed in [ARCHITECTURE.md](ARCHITECTURE.md) section 5. Examples of the whole procedure scripted: `versions/*beta34*/tools/beta34-final-build.py` and the beta.4 `Overlay.java`.
* **Never** edit a release JAR by hand without recording the hash change in `data/` and a changelog entry.

## 5. Rules the author followed (keep them)

1. One variable per build; probes are separate builds and are removed after answering their question.
2. Every behaviour change has a kill-switch and fails closed/open against an exact fingerprint of the third-party class.
3. A build is "homologated" only after: deterministic rebuild, class-major check, byte-identity of inherited classes, and **one real boot** with the log lines read.
4. Name and mark rejected builds; do not delete them.

## 6. Next actions, in priority order

1. **Live-accept beta.48** in the pack (gate in its checkpoint: lobby round trip, offhand works after it). **Live-accept beta.47** in the pack (gate in its checkpoint: the Baubles guard line names the jar with the stale API copy, the elytra goes into a bauble slot without crashing; `Angelica GL lookup bridge applied` and the inventory opens with a backpack on). **Live-accept beta.46** in the pack (gate in its checkpoint: `Immersive Petroleum projector slot guard applied` in the log, holding the projector while using the offhand no longer crashes). **Live-accept beta.45** in the pack (gate in its checkpoint: `AgriCraft irrigation guard applied` in the log, `registering tile.agricraft:waterTank` during pre-init, and a client with `"Disable Irrigation"=true` joins the server). **Live-accept beta.44** in the pack (gate in its checkpoint: `Angelica whitespace decoration endpoint fix installed` in the log, tab list separators full length). **Live-accept beta.43** in the pack (gate in its checkpoint: the hub holograms look the same, eight `Hologram fast path applied` lines, FPS at the hub against beta.42). **Live-accept beta.42** in the pack (gate in its checkpoint: shaders on with BSL or Complementary, Mirai renderer enabled, distant terrain visible through the shader pack; elytra take-off; the beta.41 gates) - a headless real client already passed the shader A/B. **Live-accept beta.41** (gate in its checkpoint: ChromatiCraft loot chest/altar with Waystones' pack selected, and a block outline after a recovered crash) and **beta.40** with Angelica 2.2.37 (gates in its checkpoint and beta.39's: items visible in NEI, no `ItemsAtlasBatch FLUSH`; headless real-client runs already passed, beta.39's `tools/headless-client/` reproduces them) and **beta.37** through SKLauncher, then **beta.35** and write the result in the CHANGELOG (it is installed but has no acceptance record). In particular confirm Backhand behaves in a real client (see the obfuscation caveat in ARCHITECTURE section 7).
2. Decide the fate of the **white/translucent world bug** ([OPEN-ISSUES.md](OPEN-ISSUES.md)). Candidates not yet excluded are listed there.
3. Recover or rewrite the **core bundle sources** (RGB, DimDoors, Network, Backhand...) - the decompiled beta.35 class is a stopgap.
4. Decide on a LICENSE (none is chosen; code is therefore "all rights reserved" by default).
5. Put the verified JAR hashes of third-party dependencies in a lock file so builds are reproducible from this repo alone.
