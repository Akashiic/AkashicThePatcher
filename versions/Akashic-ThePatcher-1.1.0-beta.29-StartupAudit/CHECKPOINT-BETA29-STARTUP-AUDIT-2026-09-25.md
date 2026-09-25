# Akashic-ThePatcher 1.1.0-beta.29 — StartupAudit

## Status
- Installed in Forbidden Series: YES
- Production approved: NO (requires runtime boot evidence)
- Base: beta.28 NuminaFovFiniteGuard
- Base SHA-256: C84D21A316879B8AFE9A50E928CAEF7788ACF769C3D75926CDFF1A21FD038443
- Candidate SHA-256: 45CC1B2D1A8F80E0CC2416050477F17D96A151568FFFEC73090AEAB69B029317
- Rollback: rollback-installed-beta28.jar

## Contract
Diagnostic-only patch. No startup work is skipped, delayed, parallelized, cached, redirected, cancelled, or replaced by beta.29.

## Probes
### BlockAtlasEligibility
Observes the startup textures/blocks atlas:
- configured and allocated mip levels
- atlas dimensions / texture ID / anisotropy
- upload count and mip-array shape
- blur/clamp flags
- invalid/out-of-bounds uploads
- duplicate exact destination rectangles
- base/level pixel totals
- uploaded / animated sprite count
- observer overhead
- conservative batch eligibility verdict

### FMLLifecycle
Measures LoadController.sendEventToModContainer:
- per-mod callback duration
- slow callback threshold: 50 ms
- phase totals and top 12 callbacks

### StartupSpan
Measures:
- NEI.ClientHandler.loadPluginsList
- MobsInfo.generateMobRecipeMap
- MobsInfo.processMobRecipeMap
- MobsInfo.MobHandler.sortCachedRecipes
- elapsed time and heap delta

## Build gates
- RFG compileJava: PASS
- deterministic JAR rebuild: PASS
- inherited beta.28 classes bit-identical except version class: PASS
- inherited beta.28 refmap bit-identical: PASS
- no removed classes: PASS
- passive-only static gate: PASS
- forbidden mutation APIs absent from new mixins: PASS
- Java class major <= 52: PASS

## Next boot
Prefer a clean machine baseline:
- >= ~5 GB free physical RAM if practical
- lower browser/background memory pressure
- no config changes between boots

After boot, inspect:
- The game loaded in ...
- BlockAtlasEligibility END ...
- FMLLifecycle SUMMARY ...
- FMLLifecycle slow ...
- StartupSpan ...
- AtlasProfile / UploadBreakdown
