# Akashic-ThePatcher 1.1.0-beta.15 - MipmapProbe

Date: 2026-09-24
Status: installed for one-boot Angelica mipmap-generation profiling.

## Base
Directly derived from beta.14 UploadProfiler.
beta.14 SHA-256:
bb352119296eddd4e6e74c8d46a05cb209fedaefdb8f755c120ee8422a0f0ddc

## Candidate
releases\!Akashic-ThePatcher-1.1.0-beta.15-MipmapProbe.jar

SHA-256:
1e7008edf76156c07f19be7151554d87c677b675db723a7c61c89e3d5f09bd0d

## Exact Angelica target
angelica-2.2.18.jar
SHA-256:
563d907f3035aedc7761be0f882674d6e736b63c5860e126e7af7df0d9f15c50

Target:
com.gtnewhorizons.angelica.glsm.GLStateManager
private static void maybeGenerateMipmap(int, int)

Confirmed bytecode invoke:
RenderBackend.generateMipmap(I)V

## Purpose
Read-only extension of beta.14 UploadProfiler.

New metrics per atlas:
- mipChecks = number of maybeGenerateMipmap calls while atlas profiling is active
- mipGen = number of actual RenderBackend.generateMipmap invocations
- mipGenMs = aggregate time spent inside RenderBackend.generateMipmap

No upload, mipmap, GL, sprite or texture behavior is changed.

## Preserved
- beta.14 UploadProfiler core preserved
- beta.13 StitchCache preserved
- existing 6 StitchCache files preserved byte-for-byte during install
- hybrid async scheduler preserved
- CoFH/MFR/Chisel/Mekanism main-thread fallbacks preserved
- AtlasProfile preserved
- Forestry guard preserved
- ParallelMipMap absent
- standalone AsyncCompat absent

## Gates
- alpha.2.7 build/reobf/checkstyle PASS
- JUnit: 5 tests, 0 failures/errors/skips
- deterministic beta.15 build PASS
- targeted gates PASS
- exact Angelica jar SHA gate PASS
- exact maybeGenerateMipmap method gate PASS
- exact generateMipmap invoke gate PASS
- inherited ThePatcher regression 16/16 PASS
- Java 8 class major <= 52

## Previous evidence
beta.14 warm boot:
FML -> menu: 5m00s
blocks GL path: 12.574s / 19,336 calls / 44,782,196 bytes
items GL path: 19.783s / 15,980 calls / 61,496,384 bytes
combined: 35,316 calls and ~32.357s GL path

All observed upload calls were mip level L0.

## Next boot
Boot normally to the main menu.
StitchCache should remain HIT.
Look for UploadBreakdown lines containing:
mipChecks=
mipGen=
mipGenMs=

Entering the server is optional for this profiling gate.

## Rollback
C:\Users\User\Desktop\Codex\Akashic-ThePatcher-1.1.0-beta.15-MipmapProbe\rollback-pre-beta15-20260924-134219