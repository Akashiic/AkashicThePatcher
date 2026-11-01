# beta.49 - BlockIconCrossDeps - 2026-11-01

`!Akashic-ThePatcher-1.1.0-beta.49-BlockIconCrossDeps.jar`, SHA-256 `c0b533a9756eb7b26b3ba050226dbc2a4c953d7ad9d222a7016ce14963fd2fef`,
394231 B, 184 entries. Base: beta.48 (`b750f810...`). Built and gated by `tools/beta49-build.py` (report: `reports/final-build-audit.json`).

## Problem

TEOF 5.5 headless test client (Forge 1614, Java 8u504, Mesa llvmpipe, the full pack with beta.48), during boot-time
measurements for the pack's RAM/boot work (Technic-Forbidden, `otimizacao-ram/BOOT.md`). One boot in three of one series
aborted the texture reload:

```
Parallel block icon registration failed; aborting texture reload.
java.lang.NullPointerException
    at net.minecraft.block.BlockSandStone.getIcon
    at ganymedes01.etfuturum.blocks.BlockRedSandstoneSlab.registerBlockIcons
    ... (worker thread of the hybrid async icon loader)
```

Et Futurum's red sandstone slab reads vanilla sandstone's icons inside its own `registerBlockIcons`. The hybrid loader
(AsyncCompat, `MixinTextureMapHybridAsync`, unchanged since beta.34) runs every block not under a compatibility root
on a worker, so the slab can run before the sandstone job has set those icons. It is a timing race: it does not
depend on that series' experimental change (a transformer pre-filter that only shifted timing), and it can hit any
player with the pack. `CompatHooks` (byte-identical from beta.34 to beta.48) forces to the client thread only
`team.chisel.`, `com.cricketcraft.chisel.`, `mekanism.`, `powercrystals.minefactoryreloaded.` and `cofh.`.

## Which blocks have the pattern

Bytecode scan of the 215 jars of TEOF 5.5 (ASM `Analyzer` + `SourceInterpreter`): for every `registerBlockIcons`
(`func_149651_a`) and every method of the same class it reaches, flag (a) an icon getter (`getIcon`,
`getBlockTextureFromSide`, `getIcon(IBlockAccess...)`, item icon getters) on a receiver other than `this`, (b) a read of an
`IIcon`/`IIcon[]` field of another object, (c) a read of a static `IIcon` field of another class. 37 classes matched:

* **28 read another block's icon** (all now on the client thread):
  Et Futurum `BlockEndGateway`, `BlockNylium`, `BlockPurpurSlab`, `BlockRedSandstoneSlab`, `BlockSmoothQuartzSlab`,
  `BlockSmoothSandstoneSlab`, `BlockStoneSlab1`, `BlockStoneWall`, `BlockWoodBarkNew`, `BlockWoodBarkOld`;
  ChromatiCraft `BlockRFNode`, `BlockSpawnerShutdown`, `BlockBedrockCrack`, `BlockLightedLeaf`, `BlockEverFluid`,
  `BlockTieredOre`; ReactorCraft `BlockDuct`, `BlockReactorOre`; RotaryCraft `BlockPiping`; Twilight Forest
  `BlockTFAuroraSlab`, `BlockTFGiantBlock`, `BlockTFPlant`, `BlockTFTrollLjos`; Magic Bees `BlockEffectJar`,
  `BlockVisAuraProvider`; Botania `BlockFelPumpkin`; Thaumic Horizons `BlockGatewayPortal`; Extra Utilities
  `BlockCobblestoneCompressed`.
* **9 not cross-dependent:** 4 Mekanism (already under the `mekanism.` root), 4 OpenBlocks (read the static icons of
  their own nested `$Icons` class, written just before in the same method), Binnie `BlockETDoor` (writes the icons of
  its `DoorType` values and reads them back in the same method).

The scan is static: a block that reaches another block's icon through another class's helper is not seen. Such a
block keeps beta.48's behaviour; the fix for it is to add its class to the list.

## Fix

`src/dev/akashic/asynciconcompat/CompatHooks.java` = beta.34's source (the class shipped from beta.34 to beta.48) plus:

| Change | What |
|---|---|
| `CROSS_DEPENDENT_BLOCKS` | the 28 class names above |
| `shouldRunBlockSynchronously` | also true when the block's class, or a superclass below `Block`, is in the list (cached per class) |
| log | `Main-thread icon fallback activated for cross-dependent block <class> (reads another block's icon while registering)`, once per class and reload |
| `telemetry()` | adds `crossDependentSyncBlocks=N` |
| kill-switch | `-Dakashic.iconCrossDeps.disable=true`: exactly beta.48's decisions (one INFO line says it is off) |

Why sending these blocks to the client thread is enough: before running a client-thread block the hybrid loop waits
for every job submitted before it (`akashic$awaitIfNeeded("block")`), and the blocks they read come earlier in
the block registry, as they must in vanilla too (vanilla registers icons in the same order on one thread).
The cost is small: 28 classes (a few dozen block instances) out of the pack's blocks lose parallelism, and each costs one
wait for the jobs already in flight.

## Offline gates (all PASS, `reports/final-build-audit.json`)

* Deterministic double build; 4 entries changed (`CompatHooks`, mod class, manifest, `mcmod.info`), none added or
  removed, 180 byte-identical to beta.48; class major <= 52; `CompatHooks` compiled with `-Xlint:all -Werror`.
* `HooksDiff` vs beta.48: 4 fields added; `crossDependentAncestor`, `isCrossDependentBlock`,
  `crossDependentBlockClassNames` added; `<clinit>`, `beginReload`, `shouldRunBlockSynchronously`, `telemetry`
  changed; `shouldRunItemSynchronously`, `isUnsafeClassName`, `shouldRunSynchronously`, `unsafeRoot` and the
  constructor identical (so the beta.34 source still compiles to the shipped methods).
* `CrossDepsGate` under `-Xverify:all` through the shipped class (release JAR on the class path, stub `Block`/`Item`):
  * beta.48: the Et Futurum slab goes to a worker; the race replay (the mixin's block loop with a sandstone job and a
    slab job that reads sandstone's icon) reads null - the defect, reproduced deterministically;
  * beta.49: the slab and a subclass of it stay on the client thread; an unlisted Et Futurum block and a vanilla block
    stay parallel; Chisel and items unchanged; the list has the 28 classes; the race replay reads the icon;
  * beta.49 with the kill-switch: identical to beta.48.

## Real boot (headless client, TEOF 5.5)

Same instance as the beta.48 measurements (warm, Java 8u504, `-Xmx6G`, G1, Xvfb + llvmpipe, 4 vCPU), beta.48 swapped
for beta.49 and nothing else:

* Both boots reached the menu with no error; `Parallel block icon registration failed` never appeared.
* 25 of the 28 classes are registered in TEOF 5.5 (`BlockSpawnerShutdown`, `BlockNylium` and `BlockTFTrollLjos` are
  not); each logged `Main-thread icon fallback activated for cross-dependent block ...` once per reload.
* `Finished hybrid async icon loading ...; asyncBlocks=4032, syncBlocks=386, crossDependentSyncBlocks=36` (first block
  reload; beta.48 in the same instance: `asyncBlocks=4068, syncBlocks=350`) and `asyncBlocks=4404, syncBlocks=405,
  crossDependentSyncBlocks=36` in the final reload.

| | Menu | Block icons, first reload | Block icons, final reload |
|---|---:|---:|---:|
| beta.48 (earlier runs, same instance) | 293-325 s | 2296-2448 ms (5133 ms cold) | 807-1034 ms (3949 ms cold) |
| beta.49 run 1 (CoreTweaks cache invalidated by the new jar, build running in parallel) | 433 s | 2294 ms | 2838 ms |
| beta.49 run 2 (warm) | 344 s | 3131 ms | 1647 ms |

The icon registration times move by a second either way between runs of the same build on this VM; no cost of the
36 client-thread blocks is visible above that noise. The race itself cannot be forced in a real client (it needs
the slab's worker to win against sandstone's), so its absence in two boots is consistent with the fix, not a proof;
the proof is `CrossDepsGate`.

## Acceptance gate in the pack

`logs/latest.log`, once per texture reload: `Main-thread icon fallback activated for cross-dependent block
ganymedes01.etfuturum.blocks.BlockRedSandstoneSlab ...` (and the other listed classes present in the pack), then
`Finished hybrid async icon loading in ...ms; asyncBlocks=..., syncBlocks=..., crossDependentSyncBlocks=N, ...`; never
`Parallel block icon registration failed`. Rollback: beta.48.
