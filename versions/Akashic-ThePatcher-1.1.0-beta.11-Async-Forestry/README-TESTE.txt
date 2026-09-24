AKASHIC THEPATCHER 1.1.0-beta.11 - ASYNC / FORESTRY
====================================================

INSTALLED ARTIFACT
!Akashic-ThePatcher-1.1.0-beta.11-Async-Forestry.jar

SHA-256
1fb3947f88442e4f9ebe6bdc8526e7ed288d31bbfa7f0f04ad8da220fa3a152d

CURRENT REQUIRED CONFIG
Hodgepodge:
  B:asyncIconLoading=false
  B:removeExtraIconLoad=true

Technic JVM:
  -XX:CompileCommand=exclude,forestry/core/genetics/Genome.getChromosomes

FIRST LIVE GATE
1. Start the pack normally.
2. Do not change graphics/resource packs for this run.
3. Confirm the Forestry CompilerOracle exclusion.
4. Reach the main menu.
5. Enter the same lobby/server.
6. Remain connected beyond the prior native-crash point.
7. Preserve latest.log and any hs_err_pid file if generated.

EXPECTED
- Forestry C2 guard says exclusion active.
- Hybrid async scheduler runs.
- MFR/Chisel/Mekanism/CoFH use main-thread fallback.
- No THashMap key-set-full error.
- No LinkageError / duplicate ASMEventHandler race.
- No Chisel TextureSubmap registration NPE.
- No Forestry C2 native crash.
- High Texture Attemption remains enabled.

ROLLBACK
rollback-pre-beta11-20260924-030822

STATUS
Offline regression: 16/16 PASS
Targeted beta.11 checks: 24 PASS
Installed hash audit: PASS
Live acceptance: PENDING