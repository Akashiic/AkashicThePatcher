# Akashic-ThePatcher 1.1.0-beta.11 - Async / Forestry checkpoint

Date: 2026-09-24
Status: offline gates PASS; installed for live acceptance.

## Base of truth
Candidate derived directly from verified production beta.8.
beta.8 SHA-256:
010c3b9f4fdd20528a772904f13238aa836c8af13759aebfe7184ed07beed44b

beta.9 MPS/Numina experiment: NOT inherited.
beta.10 Cinematic/Angelica experiment: NOT inherited.

## Candidate
!Akashic-ThePatcher-1.1.0-beta.11-Async-Forestry.jar

SHA-256:
1fb3947f88442e4f9ebe6bdc8526e7ed288d31bbfa7f0f04ad8da220fa3a152d

Installed under the live Forbidden Series mods directory.

## Merged async scheduler
Standalone Akashic-Hodgepodge-AsyncCompat is no longer installed.
beta.11 embeds the validated alpha.2.3 scheduler.

Hodgepodge native async remains intentionally disabled:
B:asyncIconLoading=false
B:removeExtraIconLoad=true

Known unsafe roots executed on client thread:
- team.chisel.
- com.cricketcraft.chisel.
- mekanism.
- powercrystals.minefactoryreloaded.
- cofh.

The cofh fallback is based on the observed THashMap corruption in
cofh.core.render.IconRegistry during Thermal Expansion BlockWorkbench icon registration.

Abort cleanup in alpha.2.3:
- cancels pending futures
- completes in-flight icon futures exceptionally
- clears processing state
- shutdownNow on executor
- clears/nulls pending state
- cleans stale executors on subsequent reload
- protects synchronous fallback failures
- tolerates late worker completions after abort

## Forestry / Java 8u51 native crash
Observed fatal JVM evidence:
- EXCEPTION_ACCESS_VIOLATION
- fatal thread: C2 CompilerThread1
- JRE: 1.8.0_51-b16
- compile task: forestry.core.genetics.Genome::getChromosomes (288 bytes)
- evidence file: hs_err_pid2844.log

Primary runtime mitigation restored:
-XX:CompileCommand=exclude,forestry/core/genetics/Genome.getChromosomes

Exact syntax was validated on the Technic Java 8u51 and produced:
CompilerOracle: exclude forestry/core/genetics/Genome.getChromosomes

beta.11 contains ForestryC2RuntimeGuard, invoked early by ThePatcher loader,
which checks the required JVM argument and logs whether it is active.
Forestry genetics bytecode is intentionally not rewritten.

## Binary delta from beta.8
Changed existing classes:
- com/akashic/thepatcher/AkashicThePatcherMod.class
- com/akashic/thepatcher/core/AkashicThePatcherLoadingPlugin.class

Added classes:
- com/akashic/thepatcher/module/forestry/ForestryC2RuntimeGuard.class
- dev/akashic/asynciconcompat/CompatHooks.class
- dev/akashic/asynciconcompat/CompatState.class
- dev/akashic/asynciconcompat/mixins/MixinTextureMapHybridAsync.class

Added resources:
- mixins.akashicasynccompat.json
- mixins.akashicasynccompat.refmap.json

No standalone AsyncCompat @Mod class is embedded.

## Gates
Inherited beta.8 suite: 16/16 PASS.
Coverage includes RGB/Hodgepodge/format/legacy/bridge/trim gates and 327169 RGB trim checks.

Targeted beta.11 suite: 24 checks PASS.
Includes ZIP CRC, Mixin bootstrap, alpha.2.3 bit identity, CoFH/MFR/Chisel/Mekanism guards,
abort cleanup, Forestry guard, exclusion of beta.9/beta.10 experiments and Java 8 bytecode.

## Next live gate
Expected JVM marker:
CompilerOracle: exclude forestry/core/genetics/Genome.getChromosomes

Expected ThePatcher marker:
[Forestry-C2] CompilerOracle exclusion active for Genome.getChromosomes

Expected texture behavior:
- hybrid scheduler active
- MFR/Chisel/Mekanism/CoFH main-thread fallbacks
- no No free or removed slots available
- no Parallel block icon registration failed
- no stale AkashicIconLoader pool after failure
- no new Forestry C2 hs_err crash

High Texture Attemption was not modified.

## Benchmark
Last clean stable baseline:
FML start -> first cinematic menu ~= 7m25s.

Do not use the alpha.2.2 7m13s run as a valid clean benchmark because its first final
texture reload failed and Minecraft performed resource-pack recovery.

## Rollback
Full pre-beta.11 rollback:
C:\Users\User\Desktop\Codex\Akashic-ThePatcher-1.1.0-beta.11-Async-Forestry\rollback-pre-beta11-20260924-030822

Contains beta.8, standalone AsyncCompat alpha.2.2, settings.json and hodgepodge.cfg.