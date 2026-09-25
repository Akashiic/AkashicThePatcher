# Akashic-ThePatcher 1.1.0-beta.30 — BlockAtlasUploadBatcher

## Status
- Installed in Forbidden Series: YES
- Production approved: NO (requires runtime validation)
- Base: beta.28 NuminaFovFiniteGuard
- Base SHA-256: C84D21A316879B8AFE9A50E928CAEF7788ACF769C3D75926CDFF1A21FD038443
- Candidate SHA-256: 75499A492A62CC9540F64CF7A30E86ABAD5310E877A741E5049772D89C089EF6
- Rollback beta.29: rollback-installed-beta29.jar

## Scope
Functional optimization only for the large startup textures/blocks atlas.

## Safety policy
- startup only
- target path must be textures/blocks
- atlas >= 2048x2048
- actual allocated mip levels must be 0
- upload mipmaps.length must be 1
- blur=false
- clamp=false
- bounds must be valid
- overlapping upload rectangles are rejected
- first valid upload is executed by vanilla
- only exact-adjacent rectangles are merged
- unused atlas padding is never overwritten
- any unexpected input triggers fail-open to vanilla
- unexpected input disables block batching for the rest of the session
- no async/threading added

## Inheritance guarantees
- beta.28 ItemsAtlasUploadBatcher byte-identical
- beta.28 NuminaFovFiniteGuard byte-identical
- beta.28 mixin config byte-identical
- beta.28 refmap byte-identical
- beta.29 diagnostic/FML mixins absent

## Tests
BlockAtlasUploadBatcherTest: 6 tests, 0 failures, 0 errors, 0 skipped.

## Runtime acceptance gates
Next boot must show:
1. BlocksAtlasBatch armed for 4096x4096 startup atlas.
2. BlocksAtlasBatch FLUSH reason=normal.
3. stagedCalls approximately 19336.
4. cancelled approximately 19335.
5. mergedPixels == stagedPixels.
6. batchGlCalls materially below 19336.
7. no fail-open.
8. no GL errors/crash/mixin failure.
9. world/menu textures visually correct.
10. compare final AtlasProfile/UploadBreakdown and total boot time against beta.29/beta.28 baselines.
