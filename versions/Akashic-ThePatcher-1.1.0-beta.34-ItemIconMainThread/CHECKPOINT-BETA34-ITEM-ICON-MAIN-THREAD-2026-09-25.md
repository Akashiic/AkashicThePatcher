# Akashic-ThePatcher 1.1.0-beta.34 — ItemIconMainThread

## Incident
- Crash: 2026-09-25 20:23:27, "Registering texture".
- FMLClientHandler.trackBrokenTexture received a null RuntimeException message.
- Guava HashBasedTable rejects null column keys, masking the original per-sprite RuntimeException.
- Failing atlas: minecraft:textures/atlas/items.png.

## Runtime evidence
- First item-atlas attempt failed after HybridAsync completed, TextureManager removed resource packs and retried.
- Second item-atlas attempt reproduced the same failure and became fatal.
- Hybrid telemetry: asyncItems=6055, syncItems=347.
- Ars Magica 2 SpellTextureHelper ("Located 803 spell IIcons") executed on AkashicIconLoader worker.
- AMSpawnEgg invokes SpellTextureHelper.loadAllIcons and mutates shared singleton icon state.
- HybridAsync/CompatHooks/mixin config are byte-identical in beta31, beta32 and beta33.
- No other mod file changed between the prior session and the beta33 crash.

## Beta34 change
- Default akashic.asyncItemIcons=false.
- Every Item.registerIcons runs on the client thread by default.
- Diagnostic opt-in remains: -Dakashic.asyncItemIcons=true.
- No change to block policy, atlas batching, stitch cache, FMLPhaseAudit, HotspotAudit, WindowGuard, Forestry or other inherited features.

## Build/install
- Gradle clean reobfJar: PASS.
- Deterministic final package: PASS.
- Only AkashicThePatcherMod.class and CompatHooks.class differ from beta33.
- Installed SHA-256: FD13DEA51AD36B04C2357083B136F1174B4882C572F86C0492875E9B356570A8
- Runtime acceptance: PENDING next boot.
