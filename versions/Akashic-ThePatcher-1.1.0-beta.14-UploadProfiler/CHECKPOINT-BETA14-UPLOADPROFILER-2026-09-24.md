# Akashic-ThePatcher 1.1.0-beta.14 - UploadProfiler

Date: 2026-09-24
Status: installed for one-boot GL upload profiling.

## Base
Directly derived from accepted beta.13 StitchCache.
beta.13 SHA-256:
4ea949e6962e09cee40cedb2da0bdc862e58e3cd77fede3e09334fedf95a570d

## Candidate
releases\!Akashic-ThePatcher-1.1.0-beta.14-UploadProfiler.jar

SHA-256:
bb352119296eddd4e6e74c8d46a05cb209fedaefdb8f755c120ee8422a0f0ddc

## Purpose
Read-only profiling of TextureUtil upload cost.

Measures:
- uploadTextureSub total time
- filter/clamp state setup
- CPU copyToBufferPos time
- GL11.glTexSubImage2D time
- GL call count
- bytes transferred
- per-mip-level sub/copy/GL/calls/bytes

Expected runtime marker:
UploadBreakdown path=textures/blocks ...
UploadBreakdown path=textures/items ...

## Preserved behavior
- beta.13 StitchLayoutCache remains active and byte-identical
- existing 6 stitch cache files preserved byte-for-byte during installation
- hybrid async icon scheduler preserved
- CoFH/MFR/Chisel/Mekanism fallbacks preserved
- Forestry runtime guard preserved
- AtlasProfile preserved
- ParallelMipMap remains absent
- standalone AsyncCompat remains absent

## Gates
- alpha.2.6 build/reobf/checkstyle PASS
- JUnit: 5 tests, zero failures/errors/skips
- beta.14 deterministic build PASS
- beta.14 targeted gates PASS
- inherited regression suite 16/16 PASS
- Java 8 class major <= 52
- installed SHA-256 verified

## Last accepted performance checkpoint
beta.13 cache HIT:
FML -> menu: 6m24s
final texture reload: 87.819s

Main remaining measured costs:
GL upload blocks: 12.255s
GL upload items: 18.249s
combined: ~30.504s

## Next boot
No config or resource-pack changes are needed.
The existing stitch cache should HIT immediately.
One normal boot to the main menu is enough for the upload breakdown.
Entering the server is optional for this profiling gate.

## Rollback
C:\Users\User\Desktop\Codex\Akashic-ThePatcher-1.1.0-beta.14-UploadProfiler\rollback-pre-beta14-20260924-131607