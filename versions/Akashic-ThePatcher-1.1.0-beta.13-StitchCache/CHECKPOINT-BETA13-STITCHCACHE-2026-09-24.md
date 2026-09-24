# Akashic-ThePatcher 1.1.0-beta.13 - StitchCache

Date: 2026-09-24
Status: installed for two-boot cache acceptance.

## Base
Directly derived from beta.12 AtlasProfiler.
beta.12 SHA-256:
ec498ac793af3dfc755a71b71b15b7bb85fae742b5727d1085c41975d5e274ed

## Candidate
!Akashic-ThePatcher-1.1.0-beta.13-StitchCache.jar

SHA-256:
4ea949e6962e09cee40cedb2da0bdc862e58e3cd77fede3e09334fedf95a570d

## New component
Persistent fail-closed Stitcher geometry cache.

Cache stores only:
- final atlas width/height
- sprite name
- packed x/y
- packed width/height
- final rotation

It does not cache:
- pixels
- PNG data
- OpenGL textures
- texture IDs
- mipmap contents

## Validation
Fingerprint SHA-256 covers:
- cache format version
- mipmap level
- max atlas width/height
- power-of-two mode
- max tile dimension
- sorted sprite names
- sprite dimensions
- effective Holder dimensions after current Angelica gutter logic
- initial holder rotation

Disk payload is CRC32 protected.

Any mismatch/corruption/application failure:
- cache rejected
- normal Stitcher.doStitch runs
- successful normal layout seeds/replaces cache

Disable switch:
-Dakashic.stitchLayoutCache=false

## Runtime sequence
First beta.13 boot:
- StitchLayoutCache MISS
- normal doStitch
- StitchLayoutCache SEEDED

Second identical boot:
- StitchLayoutCache HIT
- cached Stitcher.Slot leaves reconstructed
- vanilla/Angelica getStichSlots continues normally

## Baseline from beta.12 without ParallelMipMap
FML -> menu: 7m06s
Final texture reload: 115.778s

Main measured final atlas costs:
blocks stitch: 18.834s
items stitch: 11.230s
combined stitch: ~30.064s

blocks GL upload: 12.554s
items GL upload: 18.275s
combined upload: ~30.829s

## Gates
AsyncCompat alpha.2.5:
- build/reobf/checkstyle PASS
- JUnit 5 tests PASS, 0 failures/errors/skips
- cache fingerprint/order test PASS
- cache persistence round-trip PASS
- CRC corruption rejection PASS
- dimension invalidation PASS
- Java 8 class major 52

Merged beta.13:
- deterministic build PASS
- targeted gates PASS
- inherited beta.8 regression suite 16/16 PASS

## Installed state
ParallelMipMap: absent
Standalone AsyncCompat: absent
Hodgepodge:
B:asyncIconLoading=false
B:removeExtraIconLoad=true

## Rollback
C:\Users\User\Desktop\Codex\Akashic-ThePatcher-1.1.0-beta.13-StitchCache\rollback-pre-beta13-20260924-042352