# Akashic-ThePatcher 1.1.0-beta.31 — FMLPhaseAudit

## Status
- Installed: YES
- Production approved: NO (diagnostic build; requires one runtime boot)
- Base: beta.30.2 BlockAtlasReobfFix
- Base SHA-256: A47B80478D5E64736FD624C75569CAB00A2DBC11270BEF0F24E89E624B5A705B
- Candidate SHA-256: 45A2C442E9173E33364084B6C73BDD570D570C330A609D6B812ADE4D83EE1228
- Rollback: rollback-installed-beta30.2.jar

## Scope
Early ASM diagnostic only. No startup work is skipped, reordered, parallelized, cached, cancelled, or replaced.

## Instrumentation
Target: cpw.mods.fml.common.LoadController

Global:
- propogateStateMessage(FMLEvent)
- buildModList(FMLLoadEvent)

Per mod:
- sendEventToModContainer(FMLEvent, ModContainer)

Each phase summary records:
- wall time
- current-thread CPU time when supported
- sum of per-mod callbacks
- time outside mod callbacks
- callback count
- counts >= 50 ms / >= 250 ms / >= 1000 ms
- heap delta
- top 15 slowest mods

## Safety / gates
- Hook aggregation logs once per phase, not once per mod.
- Transformer is registered as an early LaunchWrapper IClassTransformer.
- Runtime LoadController smoke test against Technic modpack.jar: PASS.
- Original EventBus.post preserved exactly once.
- Original ModContainer.registerBus preserved exactly once.
- Original sendEventToModContainer call preserved exactly once.
- Transformer idempotency test: PASS.
- Hook sorting test: PASS.
- 4 tests total, 0 failures/errors/skips.
- BlockAtlasUploadBatcher inherited byte-identical from beta.30.2.
- ItemsAtlasUploadBatcher inherited byte-identical.
- NuminaFovFiniteGuard inherited byte-identical.
- Mixin config/refmap inherited byte-identical.

## Expected runtime markers
[Akashic-ThePatcher/FMLPhaseAudit] LoadController instrumentation installed.
FMLPhaseAudit SPAN name=LoadController.buildModList ...
FMLPhaseAudit PHASE event=... wall=... cpu=... callbackSum=... outsideCallbacks=... top=...

## Next action
Run one normal boot. Then compare actual FML phase totals and top callbacks with beta.30.2.
