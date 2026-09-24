# Akashic-ThePatcher 1.1.0-beta.16 - UniversalForestryC2

Date: 2026-09-24
Status: installed for zero-config live validation.

## Candidate
releases\!Akashic-ThePatcher-1.1.0-beta.16-UniversalForestryC2.jar

SHA-256:
8ec2e72b2fb9488ac759e24705c22a1a05181afb107b861ca67a4c8139a4a483

## Exact Forestry target
Forestry-4.11.36.jar
SHA-256:
78daf273c4d112324ea7fb3ad9fbac99ecd088c1b78982811deebd58da712d6f

forestry/core/genetics/Genome.class SHA-256:
fe62518c6126cc715610ef3ced8685edac0e5aeb0c0315a5c1d2d284d55f2683

Target method:
private static IChromosome[] getChromosomes(
    NBTTagCompound,
    ISpeciesRoot)

Original observed code length:
288 bytes

## Universal mitigation
MixinForestryGenomeC2 @Overwrite replaces the private Forestry method
with a 6-byte delegation to ForestryGenomeC2Shim.

ForestryGenomeC2Shim preserves the original algorithm while splitting it:
- loadStoredChromosomes
- repairAndValidateChromosome
- fillMissingChromosomes

Preserved semantics include:
- invalid slot skipping
- clearInvalidChromosomes repair
- mutation of the original chromosome NBT when repaired
- invalid allele RuntimeException
- species-template fallback for missing chromosomes

Runtime proof marker:
ForestryGenomeC2Shim ACTIVE: using split Genome.getChromosomes implementation

Runtime property:
akashic.forestryC2Shim.executed=true

## Guard behavior
With beta.16:
- shim present, no CompileCommand -> guard active
- shim present + CompileCommand -> guard active with redundant protection
- neither present -> warning / inactive

The JVM CompileCommand is no longer required when the universal shim is bundled.

## Gates
- alpha.2.8 build/reobf/checkstyle PASS
- differential Forestry test suite: 4 new tests PASS
- total JUnit: 9 tests, 0 failures/errors/skips
- beta.16 deterministic build PASS
- targeted structural gates PASS
- inherited ThePatcher regression: 16/16 PASS
- Java 8 class major <= 52
- Forestry dependency NOT bundled
- standalone AsyncCompat absent
- beta.15 performance classes preserved byte-for-byte

## Live test environment
CompileCommand intentionally removed from Technic settings.json.
JAVA_TOOL_OPTIONS intentionally absent from restarted Technic process.
Technic restarted clean at:
2026-09-24 14:50:31

Expected startup guard:
[Forestry-C2] Universal Genome shim bundled; JVM CompileCommand not required

Expected live execution marker after Forestry genetics are exercised:
ForestryGenomeC2Shim ACTIVE

Acceptance:
- no CompilerOracle exclusion required
- connect to server
- remain past previous 13-second crash window
- no new hs_err
- normal disconnect/shutdown

## Rollback
C:\Users\User\Desktop\Codex\Akashic-ThePatcher-1.1.0-beta.16-UniversalForestryC2\rollback-pre-beta16-20260924-145011