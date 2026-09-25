# Akashic-ThePatcher 1.1.0-beta.32 — HotspotAudit

## Status
- Installed: YES
- Production approved: NO (diagnostic build; requires one runtime boot)
- Base: beta.31 FMLPhaseAudit
- Base SHA-256: 45A2C442E9173E33364084B6C73BDD570D570C330A609D6B812ADE4D83EE1228
- Candidate SHA-256: EE26B8B61C1C93E7611A7AD898989356CB9E38BF03C68BE09882559A1B107F30
- Rollback: rollback-installed-beta31.jar

## Contract
Diagnostic only. No target method is cancelled, redirected, reordered, skipped, cached, parallelized, or replaced.

## Runtime smoke
PASS on 18/18 real installed classes and 20/20 span entries.

## Instrumented hotspots
- Decocraft.Props.loadModels
- GeckoLib.initialize
- ChromatiCraft.CCAPICore.load
- ChromatiCraft.ChromaRecipes.loadDictionary
- ChromatiCraft.ChromaDimensionManager.initialize
- ChromatiCraft.ChromaStructures.buildStructures
- ChromatiCraft.ChromaRecipes.addRecipes
- ChromatiCraft.ChromaDescriptions.loadData
- ChromatiCraft.addTileEntities
- ChromatiCraft.addEntities
- CustomNPC.LoadConfiguration.init
- CustomNPC.EnchantInterface.load
- CustomNPC.CustomItems.load
- CustomNPC.CommonProxy.load
- CustomNPC.ClientProxy.load
- Eln.ConfigHandler.loadConfig
- Eln.MqttManager.init
- MobsInfo.ClientProxy.loadComplete
- MobsInfo.generateMobRecipeMap
- MobsInfo.generateVillagerTrades

## Inherited byte-identical
- beta31 FMLPhaseAudit
- beta30.2 BlockAtlasUploadBatcher
- ItemsAtlasUploadBatcher
- NuminaFovFiniteGuard
- mixin config/refmap

## Acceptance
Next boot should produce both FMLPhaseAudit PHASE summaries and FMLPhaseAudit SPAN entries for the targeted methods. Compare hotspot sums against enclosing mod callback time and identify optimization candidates with semantic equivalence.
