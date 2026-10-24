# beta.37 - ForestryGenomeBarrier - 2026-10-24

## Why another build after beta.36
Request: fix the Forestry `Genome` crash for good, with no side effects. It has been around since beta.11, and every earlier fix had a weak point:

| Build | Approach | Weak point |
|---|---|---|
| beta.11-15 | `-XX:CompileCommand=exclude` on the launcher | depends on the launcher (Technic kept stale args; SKLauncher drops them) |
| beta.16 | Mixin `@Overwrite` + reimplementation | rejected |
| beta.17-35 | transformer **replaces** Forestry's body with `ForestryGenomeCompat` (a reimplementation), only for one exact fingerprint | reimplemented logic (risk of divergence); refuses (= unprotected) on any other Forestry build; "rewrite alone is not sufficient" -> still needed 6 JVM args |
| beta.36 | pads the reimplementation past HugeMethodLimit | launcher-independent, but still runs a reimplementation, still fingerprint-bound |

## beta.37: Forestry's own code, never JIT-compiled
`ForestryGenomeC2Transformer` no longer replaces anything. It leaves Forestry's `Genome.getChromosomes` exactly as shipped and inserts an unreachable block in front of it:

```
GOTO body
[frame: args | Throwable]  NOP x 8192  ATHROW     // dead, never executed
body: [frame: args | -]    Forestry's original 288 bytes, unchanged
```

The method is now 8484 bytes, above HotSpot's `HugeMethodLimit` (8000, a `develop` constant in 8u51). With `DontCompileHugeMethods=true` (the product default) HotSpot never compiles it (C1, C2 or OSR) and never inlines it into callers. The crashing C2 compilation therefore cannot start, whatever its root cause turns out to be.

* **Semantics:** this is Forestry's own code. `test/BodyCompare` checks, with ASM 5.0.3, that every instruction, operand, label, line number, try/catch and local variable is identical after the head. It also checks that the 16 other methods of `Genome` and the class shape are unchanged.
* **Switch alignment:** the head is 3 + 8192 + 1 = 8196 bytes, a multiple of 4, so `tableswitch`/`lookupswitch` padding cannot shift. The transformer checks that the code length grew by exactly 8196.
* **Matching:** owner + name + descriptor only. Since nothing semantic changes, the fingerprint refusal is gone, and other Forestry builds with this method are covered too. The transformer is idempotent: a method already over the limit is left alone.
* **Kill-switch:** `-Dakashic.forestryGenomeBarrier.disable=true`.
* **Removed:** `ForestryGenomeCompat` (nothing references it anymore).
* **Guard:** logs `[Forestry-C2] Genome compile barrier armed ...` at coremod init. When `Genome` loads, the transformer logs `Genome compile barrier applied: Forestry's original getChromosomes kept unchanged, code 288 -> 8484 bytes`. The guard warns `UNPROTECTED` only with `-XX:-DontCompileHugeMethods` and no exclusion.

## The only side effect: interpreted speed
`getChromosomes` now always runs in the interpreter (the same as with the old CompileCommand). `GenomeBench`, a full 15-chromosome bee genome, measured per call:

| JVM | Forestry original (JIT) | beta.37 (interpreter) |
|---|---|---|
| 8u51 | ~1.2 us | ~4.1 us |
| 8u462 | ~1.3 us | ~4.8 us |
| 21 | ~0.5 us | ~3.8 us |

That is about +3 us each time a genome is read from NBT. Even 1000 reads in one tick would cost ~3 ms. Callees (`Chromosome`, NBT, allele registry) are still JIT-compiled normally.

## Root cause
The crash was **not** reproduced outside the game: see `investigations/forestry-c2-8u51-linux-repro/` (Zulu 8u51 = HotSpot 25.51-b03 on Linux, real Forestry classes, 6 variants x 4M calls). The fix does not depend on the root cause.

## Gates (reports/final-build-audit.json) - all PASS
* Deterministic double build, SHA-256 `2209938684e5ae9f0f86946a2ae3612fcf2bb9528d590fd29e268eb47a3e483c`. Delta vs beta.36: 5 entries changed, `ForestryGenomeCompat.class` removed, the other 153 byte-identical, class major <= 52.
* The real Forestry 4.11.36 `Genome.class` (SHA-256 `fe62518c...`) was transformed by the bundled transformer under the **real** launchwrapper 1.12 + ASM 5.0.3 on 8u51: applied (288 -> 8484), idempotent, and the kill-switch works.
* `BodyCompare`: the original body and the other methods are preserved.
* `GenomeChaos`, 600k calls with `-Xverify:all -XX:+PrintCompilation`, run on 8u51, 8u462 and 21:
  * The control (original Forestry) is compiled at tiers 3 and 4.
  * The barrier build is **never compiled**, including under `-Xcomp`.
  * The result digests (results, NBT mutations, exceptions; 21,564 exception and 36,822 repair cases) are **identical** to the original on every JVM.
* `GuardHarness` on 8u51 returns all three verdicts.

## Live acceptance gate
1. Exactly one `!Akashic-ThePatcher-*.jar` in `mods/` (beta.37).
2. The log shows `[Forestry-C2] Genome compile barrier armed` and later `Genome compile barrier applied: ... 288 -> 8484 bytes`.
3. Play through the old crash window (join, NEI, bees/trees); no new `hs_err_pid*.log`.

The CompileCommand arguments in the Technic `version.json` are now unnecessary and harmless.

Rollback: beta.36 (`45a00f1e...`) or beta.35.

## Rebuild
```
python3 tools/beta37-build.py --jdk8u51 <zulu 8u51> --jdk8 <jdk8> --jdk21 <jdk21> \
  --forestry-jar Forestry-4.11.36.jar --libs <dir with asm-all-5.0.3, launchwrapper-1.12, guava-17.0, log4j-api/core 2.0-beta9> \
  --asm9 asm-9.x.jar --out <jar>
```
