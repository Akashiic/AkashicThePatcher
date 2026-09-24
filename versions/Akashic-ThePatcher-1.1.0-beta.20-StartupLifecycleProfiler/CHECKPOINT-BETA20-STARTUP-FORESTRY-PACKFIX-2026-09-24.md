# Akashic-ThePatcher 1.1.0-beta.20 - StartupLifecycleProfiler

Date: 2026-09-24
Status: installed for live startup profiling + pack-side Forestry C2 validation.

## ThePatcher
Installed:
!Akashic-ThePatcher-1.1.0-beta.20-StartupLifecycleProfiler.jar

SHA-256:
00a29e58ebb428e915d3579ba3f6b9bceb6464d29d4f8c08ef7fe20285e3a48a

Base:
beta.19 ItemsAtlasBatchPackFix

Preserved byte-for-byte:
- ItemsAtlasUploadBatcher + State
- MixinTextureMapItemsUploadBatch
- MixinTextureUtilItemsUploadBatch
- StitchLayoutCache
- hybrid async icon loader
- UploadProfiler / AtlasProfiler
- Angelica mipmap probe
- Forestry Genome rewrite/helper/transformer

## Startup profiler
New passive instrumentation:
- LoadController.propogateStateMessage(FMLEvent)
- LoadController.sendEventToModContainer(FMLEvent, ModContainer)

At the end of each FML lifecycle event it logs:
StartupPhase event=<...> total=<...>ms dispatch=<...>ms outside=<...>ms modCalls=<...> mods=<...> top=[...]

Top 15 mods are emitted per phase.
Disable switch:
-Dakashic.startupLifecycleProfiler=false

Purpose:
Explain the ~110s delta between beta.14 (~5m00) and beta.19 (~6m51) that accumulated before final texture reload.

Verified beta.14 vs beta.19:
- Config delta: +25s
- PreInit delta: +45s accumulated
- Init delta: +77s accumulated
- PostInit delta: +96s accumulated
- LoadComplete delta: +109s accumulated
- final texture reload delta: ~0.4s only
Therefore regression is pre-final-reload, not StitchCache/items batching.

## Forestry C2 pack-side fix
Persistent source:
bin\modpack.jar!/version.json

Only version.json differs from pre-fix modpack.jar.
MANIFEST.MF, META-INF/FORGE.SF and META-INF/FORGE.DSA are byte-identical.

arguments.jvm contains six exclusions:
1. forestry/core/genetics/Genome.getChromosomes
2. com/akashic/thepatcher/module/forestry/ForestryGenomeCompat.loadChromosomes
3. com/akashic/thepatcher/module/forestry/ForestryGenomeCompat.loadStoredChromosome
4. com/akashic/thepatcher/module/forestry/ForestryGenomeCompat.repairInvalidAlleles
5. com/akashic/thepatcher/module/forestry/ForestryGenomeCompat.validateChromosome
6. com/akashic/thepatcher/module/forestry/ForestryGenomeCompat.fillMissingChromosomes

Per-user settings.json contains ZERO CompileCommand entries.

Guard beta.20 requires 6/6 and logs:
[Forestry-C2] Full CompilerOracle pack protection active (6/6)

The rewrite alone is no longer considered sufficient.

Java 8u51 harness:
6/6 CompilerOracle accepted
guard active=true
matched=6
rewrite=true

## Items batching accepted evidence
beta.19 live:
- stagedCalls=15980
- cancelled=15979
- batchGlCalls=16
- GL flush=227ms
- savedCalls~15963
Previous items GL: ~20-32s
New batch GL: ~0.23s

## Gates
- alpha.2.10 clean build/reobf/checkstyle PASS
- JUnit 9/9 PASS
- beta.20 deterministic build PASS
- inherited regressions 16/16 PASS
- Java 8 class major <=52
- rejected MixinForestryGenomeC2 absent
- pack-side modpack.jar delta audit PASS
- Forestry guard 6/6 Java 8u51 harness PASS

## Next live gate
1. Launch using current Technic process (fresh start 17:09:41).
2. Confirm launch command includes six CompileCommand args although settings.json has none.
3. Confirm guard logs Full CompilerOracle pack protection active (6/6).
4. Confirm ItemsAtlasBatch remains successful.
5. Collect StartupPhase summaries for Construction / PreInit / Init / PostInit / LoadComplete.
6. Enter server and remain long enough to cross prior C2 crash window.
7. Check zero new hs_err.