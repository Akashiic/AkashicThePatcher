# Forestry C2 Runtime Protection - 2026-09-24

## Confirmed native crash signature
Two independent fatal JVM crashes were compared:
- hs_err_pid2844.log
- hs_err_pid9976.log

Both:
- Java 8u51-b16 / HotSpot 25.51-b03
- EXCEPTION_ACCESS_VIOLATION 0xc0000005
- same problematic jvm.dll offset: jvm.dll+0x4e4697
- fatal thread: C2 CompilerThread
- Current CompileTask:
  forestry.core.genetics.Genome::getChromosomes (288 bytes)

## Root cause of missing protection
Technic launcher process had remained alive since:
2026-09-23 23:44:38

settings.json was edited later, at:
2026-09-24 03:08:22

Technic keeps Java settings in memory after startup.
The long-lived launcher therefore kept launching Minecraft with stale JVM args.

Evidence:
- 00:02 launch included CompileCommand successfully.
- later launches from the same launcher process omitted it.
- current Technic source splitJvmArgString() passes custom javaArgs directly.

## Persistent configuration
settings.json contains:
-XX:CompileCommand=exclude,forestry/core/genetics/Genome.getChromosomes

## Current-session redundant protection
Technic was fully terminated and restarted.
Current guarded Technic PID:
916
started:
2026-09-24 14:09:51

It was started with process-local:
JAVA_TOOL_OPTIONS=-XX:CompileCommand=exclude,forestry/core/genetics/Genome.getChromosomes

This environment variable is NOT written globally to Windows.
It is inherited only by this Technic process and its child Minecraft process.

## Exact runtime validation
Java 21 launcher runtime:
PASS - accepts the CompileCommand via JAVA_TOOL_OPTIONS.

Technic game Java 8u51:
PASS - prints:
CompilerOracle: exclude forestry/core/genetics/Genome.getChromosomes

## Next acceptance gate
Launch Forbidden Series from the currently-open Technic process.

Required evidence:
1. Technic command contains -XX:CompileCommand=exclude,... OR game output shows JAVA_TOOL_OPTIONS.
2. HotSpot prints CompilerOracle exclusion.
3. ThePatcher Forestry-C2 guard reports exclusion active.
4. Enter server and remain past the previous crash window.
5. No new hs_err log.

Do not change mods/resource packs before this gate.