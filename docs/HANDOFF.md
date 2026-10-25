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

Current: `!Akashic-ThePatcher-1.1.0-beta.40-ItemsAtlasAngelicaGuard.jar`, SHA-256 `242f07422c1a8013ca72755b8cc3765636a2cc1a3f04b98075475707b78676f2`. (beta.38 must not be installed: see the CHANGELOG.)

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
* **Hodgepodge** `asyncIconLoading=false` (ThePatcher's own scheduler replaces it).
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

1. **Live-accept beta.40** with Angelica 2.2.37 (gates in its checkpoint and beta.39's: items visible in NEI, no `ItemsAtlasBatch FLUSH`; headless real-client runs already passed, beta.39's `tools/headless-client/` reproduces them) and **beta.37** through SKLauncher, then **beta.35** and write the result in the CHANGELOG (it is installed but has no acceptance record). In particular confirm Backhand behaves in a real client (see the obfuscation caveat in ARCHITECTURE section 7).
2. Decide the fate of the **white/translucent world bug** ([OPEN-ISSUES.md](OPEN-ISSUES.md)). Candidates not yet excluded are listed there.
3. Recover or rewrite the **core bundle sources** (RGB, DimDoors, Network, Backhand...) - the decompiled beta.35 class is a stopgap.
4. Decide on a LICENSE (none is chosen; code is therefore "all rights reserved" by default).
5. Put the verified JAR hashes of third-party dependencies in a lock file so builds are reproducible from this repo alone.
