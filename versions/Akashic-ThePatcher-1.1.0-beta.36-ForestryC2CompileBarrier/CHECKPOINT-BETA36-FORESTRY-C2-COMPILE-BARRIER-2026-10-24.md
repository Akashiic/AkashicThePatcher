# beta.36 - ForestryC2CompileBarrier - 2026-10-24

## Incident
Joining the server through **SKLauncher** closes the client a few seconds after the world loads (two sessions: 04:48 and 05:02, 2026-10-24). Neither `latest.log` nor `fml-client-latest.log` contains a Java exception or a crash report: the log just stops (last line 05:10:44, ~20 s after "Connecting to br2..."). This is how the known Forestry / Java 8u51 C2 native crash looked in beta.11 (`EXCEPTION_ACCESS_VIOLATION`, `C2 CompilerThread`, `jvm.dll+0x4e4697`, compile task `forestry.core.genetics.Genome::getChromosomes`).

Evidence in the SKLauncher logs:
* JVM `1.8.0_51` from `.minecraft\runtime\jre-legacy` (the affected HotSpot 25.51).
* Line 590 of both FML logs: `[Forestry-C2] INCOMPLETE JVM protection (0/6). Missing CompileCommand exclusions: [...]`.
* The FML JVM argument list has no `-XX:CompileCommand`: SKLauncher does not read the `arguments.jvm` the Technic pack's `version.json` carries (see `investigations/forestry-versionjson-packfix/`). The only protection left was the bundled rewrite, which beta.17's own checkpoint says "is no longer considered sufficient".

**Not proven:** the user did not attach an `hs_err_pid*.log`. If one exists in the instance folder (`.minecraft\instances\Forbidden_Series\`) and its "Current CompileTask" names `Genome::getChromosomes` or `ForestryGenomeCompat`, the diagnosis is confirmed. If it names something else, this build does not address it.

## Fix: compile barrier carried inside the JAR
HotSpot refuses to compile (C1, C2 and OSR) **and** to inline any method whose bytecode is longer than `HugeMethodLimit` (8000 bytes, a `develop` constant in 8u51) while `DontCompileHugeMethods` is `true` (the product default). For these methods that gives the same result as `-XX:CompileCommand=exclude` - they only run in the interpreter - but it needs no launcher setting.

`tools/ApplyForestryCompileBarrier.java` adds the following to the start of each of the five `ForestryGenomeCompat` helpers (`loadChromosomes`, `loadStoredChromosome`, `repairInvalidAlleles`, `validateChromosome`, `fillMissingChromosomes`):

```
GOTO body
[frame: args | Throwable]  NOP x 8192  ATHROW     // dead code, never runs
body: [frame: args | -]    <original code, unchanged>
```

That is one extra `GOTO` per call. The original instructions, exception tables and frames are kept as they were (expanded F_NEW frames, `ClassWriter(0)`, no frame recomputation and no class loading). `Genome.getChromosomes` is still rewritten by the beta.17 transformer into a 4-opcode call to `loadChromosomes`. That wrapper can still be compiled, but C2 cannot inline its now-huge callee, so the code shape that used to crash no longer reaches C2.

`ForestryC2RuntimeGuard` was rewritten from the beta.35 bytecode with the same public API, and now also checks the barrier at boot. It reads the bundled class and parses each helper's `Code` length, then reads `DontCompileHugeMethods` through `HotSpotDiagnosticMXBean`. Its log line is one of these:
* `[Forestry-C2] In-JAR compile barrier active (5/5 Genome helpers > 8000 bytecode bytes, DontCompileHugeMethods=true) ...` - expected on SKLauncher and every other launcher.
* `[Forestry-C2] Full CompilerOracle pack protection active (6/6); in-JAR compile barrier also active` - Technic with the version.json packfix.
* `WARN: [Forestry-C2] UNPROTECTED ...` - only if someone passes `-XX:-DontCompileHugeMethods` and no exclusions.

It also sets the new property `akashic.forestryC2Guard.compileBarrier`.

## Delta vs beta.35 (5 of 159 entries)
`ForestryGenomeCompat.class` (padded), `ForestryC2RuntimeGuard.class` (new source in `src/`), `AkashicThePatcherMod.class` (`VERSION` + `@Mod.version`: `1.1.0-beta.34` -> `1.1.0-beta.36`, because beta.35 never bumped it), `META-INF/MANIFEST.MF` and `mcmod.info` (`1.1.0-beta.35` -> `1.1.0-beta.36`). The other 154 entries are byte-identical.

## Offline gates (reports/final-build-audit.json) - all PASS
* Deterministic: built twice, identical SHA-256 `45a00f1ea27778d9d01d392180c1a3980e440370beeb84ec0af9213e836e2bf4`.
* Changed classes are class major 52.
* `test/GenomeCompatHarness` (stubs of the Forestry/NBT API, 2,000,000 hot calls plus the invalid-allele repair/exception paths), run with `-Xverify:all -XX:+PrintCompilation`:
  * Java 8 (Zulu 8u462) and Java 21: padded class -> results correct, **no** `ForestryGenomeCompat` method compiled.
  * Control, beta.35 class -> all 5 helpers compiled (this proves the probe would see a compilation).
* `test/GuardHarness` on Java 8 against the built JAR: barrier verdict with default flags; `UNPROTECTED` with `-XX:-DontCompileHugeMethods`; 6/6 + barrier with the six CompileCommands.

Not tested: the exact 8u51-b16 binary (not redistributable here) and a real Minecraft boot. `HugeMethodLimit`/`DontCompileHugeMethods` behave the same in every HotSpot 8 update.

## Live acceptance gate (next boot through SKLauncher)
1. Exactly one `!Akashic-ThePatcher-*.jar` in `mods/` (remove beta.35).
2. `fml-client-latest.log` contains `[Forestry-C2] In-JAR compile barrier active (5/5`.
3. Join the server and stay well past the old crash window (>2 min, open NEI, look at bees/saplings).
4. No new `hs_err_pid*.log`.

Rollback: beta.35 (`c48d3ec3...`).

## Rebuild
```
python3 tools/beta36-build.py --jdk21 <jdk21> --jdk8 <jdk8> --asm asm-9.x.jar --asm-tree asm-tree-9.x.jar --out <jar>
```
