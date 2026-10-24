# Forestry C2 crash - reproduction attempt on Linux HotSpot 25.51 (2026-10-24)

Goal: reproduce the native C2 crash (`EXCEPTION_ACCESS_VIOLATION`, `jvm.dll+0x4e4697`, compile task `forestry.core.genetics.Genome::getChromosomes (288 bytes)`) outside Minecraft. If it reproduced, a fix could be proved to stop it.

Setup:
* JVM: Azul Zulu `1.8.0_51-b16`, HotSpot `25.51-b03`, linux x64. That is the HotSpot build named in the hs_err logs, but this is the Linux binary, not Windows `jvm.dll`.
* Code: the **real** Forestry 4.11.36 jar (SHA-256 `78daf273...`, the same fingerprint the patcher checks) plus guava 17 and log4j 2.0-beta9. Minecraft classes are replaced by small stubs (`versions/Akashic-ThePatcher-1.1.0-beta.37-ForestryGenomeBarrier/test/stubs/`).
* Drivers:
  * `GenomeRepro.java`: one bee root, uniform genomes.
  * `GenomeChaos.java`: lives in the beta.37 `test/` folder. It drives three species roots (bee/tree/butterfly, distinct receiver classes) with full, partial, shuffled, bad-slot, no-species and invalid-allele genomes. It toggles `Config.clearInvalidChromosomes`, exercises the RuntimeException path, and checks every result against a reference model.

Result (`variants.result.txt`): **not reproduced.** In every run C2 compiled `Genome::getChromosomes` at tier 4 and the JVM stayed alive. Each run made 4,000,000 calls; the variants were tiered (2 seeds), `-XX:-TieredCompilation`, `-Xcomp`, `-XX:-TieredCompilation -Xbatch`, and the pack's own G1 flags.

What this means: the crash needs something this setup does not have. It could be Windows-only (`jvm.dll`), a type profile only the real game produces, or the classes other coremods inject. The cause in HotSpot is therefore **not identified**. The beta.37 fix does not depend on knowing it: the method is never handed to the JIT at all. The beta.37 gates prove that with `-XX:+PrintCompilation`. In the same runs, the control (unpatched Forestry) is compiled.
