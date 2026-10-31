# Akashic-ElnEndlessIdsFix

Small client/server coremod that makes **Electrical Age** (age-series `Eln`, checked against **1.24.6 and 1.24.8**) work with
**EndlessIDs**. It is a separate JAR, not part of ThePatcher.

Install `dist/Akashic-ElnEndlessIdsFix-1.0.0.jar` in `mods/` **on every client** (fix 1). Installing it on the
server too adds fix 2. It does nothing to other mods. When Electrical Age is not installed, it does nothing at all.

> Resumo (pt-BR): com o EndlessIDs, ver um **Auto Miner** do Electrical Age ou ligar o **Portable Ore Scanner**
> derrubava o cliente (`UnsupportedOperationException: A mod that is incompatible with EndlessIDs has tried to
> access the block array of a chunk`). Este JAR troca a leitura crua do chunk por `getBlockByExtId`, que o
> EndlessIDs suporta. Coloque-o na pasta `mods/` de todos os clientes, e também do servidor (fix 2). Testado contra os
> JARs do Eln 1.24.6 e 1.24.8.

## Symptom (fix 1, client)

The client crashes every frame an Auto Miner is in render range (so it crashes again right after logging in next to
one), or when a Portable Ore Scanner is switched on:

```
java.lang.UnsupportedOperationException: A mod that is incompatible with EndlessIDs has tried to access the block array of a chunk like in vanilla! Crashing in fear of potential world corruption!
	at net.minecraft.world.chunk.storage.ExtendedBlockStorage.emergencyCrash(SourceFile:1697)
	at net.minecraft.world.chunk.storage.ExtendedBlockStorage.handler$blm000$endlessids$crashLSBArray(SourceFile:1852)
	at net.minecraft.world.chunk.storage.ExtendedBlockStorage.func_76658_g(SourceFile)
	at mods.eln.item.electricalitem.PortableOreScannerItem$RenderStorage.generate(PortableOreScannerItem.kt:410)
	at mods.eln.transparentnode.autominer.AutoMinerRender.refresh(AutoMinerRender.java:197)
	at mods.eln.node.transparent.TransparentNodeEntity.clientRefresh(TransparentNodeEntity.kt:149)
	at mods.eln.client.FrameTime.tick(FrameTime.java:69)
```

(First seen 2026-10-31 with Eln 1.24.6, EndlessIDs 1.7.4, Angelica 2.2.41, ThePatcher beta.47.)

## Cause

`PortableOreScannerItem$RenderStorage.generate` (the X-ray picture on the scanner and on the Auto Miner's screen)
casts rays through the world and, for every block it meets, reads the id straight from the chunk's raw arrays
(`PortableOreScannerItem.kt:410-414`):

```kotlin
var blockId = storage.blockLSBArray[yLocal shl 8 or (zLocal shl 4) or xLocal].toUByte()
if (storage.blockMSBArray != null) {
    blockId = blockId or (storage.blockMSBArray[xLocal, yLocal, zLocal] shl 8).toUByte()
}
blockKey = (blockId + (storage.getExtBlockMetadata(xLocal, yLocal, zLocal).toUInt() shl 12)).toUShort()
```

EndlessIDs stores block ids in its own format and makes `getBlockLSBArray()` crash on purpose, so a mod that reads
it cannot misread ids and corrupt the world. (This code was also wrong without EndlessIDs: `(msb shl 8).toUByte()`
is always 0, so every block with an id of 256 or more, i.e. every modded ore, was read as another block.)

## Fix

The coremod registers one `IClassTransformer` (sorting index 1001) that touches two Electrical Age classes. Each rewrite
checks the exact bytecode shape it expects first. If the shape does not match, it leaves the class unchanged and logs
`... not patched: <reason>`.

1. **`PortableOreScannerItem$RenderStorage.generate` (client).** The instructions from `storage.blockLSBArray` to the
   `blockKey` store are replaced with `blockKey = ElnEndlessIdsHooks.scannerBlockKey(storage, x, y, z)`. The hook reads
   the block with `ExtendedBlockStorage.getBlockByExtId` (`func_150819_a`), which EndlessIDs supports, and builds the
   same key as Electrical Age: `id | (meta & 15) << 12`. A block whose id does not fit in 12 bits (only possible with
   EndlessIDs) cannot have an entry in Electrical Age's 65536-entry ore map. It is drawn as stone if it is opaque, or as
   glass if it is not, so it is not mistaken for another block. The surrounding code (null checks, cache, colours) is
   unchanged.
2. **`AutoMinerSlowProcess.checkIsOre` (server).** It looks up `OreColorMapping.map[getIdFromBlock(block) + (meta << 12)]`.
   With an id above 4095, this lookup either reads another block's entry or runs past the end of the array
   (`ArrayIndexOutOfBoundsException` in the Auto Miner's tick). `getIdFromBlock` is replaced by
   `ElnEndlessIdsHooks.oreMapBlockId` (ids above 4095 become a large negative number). The index goes through
   `ElnEndlessIdsHooks.oreMapIndex`, which maps anything out of range to entry 0 (air, never an ore). Blocks that are
   `BlockOre`/`BlockRedstoneOre`/Eln ores are still found by the `instanceof` checks before this lookup. This fix only
   matters on the server; on the client it changes nothing.

Log lines (`fml-client-latest.log` / server log, logger `Akashic-ElnEndlessIdsFix`):

```
Patched Electrical Age PortableOreScannerItem$RenderStorage.generate for EndlessIDs (OK storage=47 xyz=48,49,50 key=42 removed=...)
Patched Electrical Age AutoMinerSlowProcess.checkIsOre for EndlessIDs (OK)
```

Kill-switches (JVM arguments): `-Dakashic.elnOreScanner.disable=true` (fix 1), `-Dakashic.elnAutoMinerOreKey.disable=true`
(fix 2).

Electrical Age 1.24.6 and 1.24.8 carry byte-identical copies of both classes
(`PortableOreScannerItem$RenderStorage.class` sha256 `c60f505e...`, `AutoMinerSlowProcess.class` sha256 `7ec27a5d...`),
so the same fix applies to both (1.24.7 was not checked but sits between them). It was written against the
[1.24.8 source](https://github.com/age-series/ElectricalAge/tree/1.24.8).

## Verification

`tools/check.sh <libs dir>` (the libs come from `tools/ci/fetch_build_libs.sh`; CI runs it on every push) does the
following:

* rebuilds the JAR with `build.py` and compares it byte for byte with `dist/` (deterministic build);
* `tools/hooktest/HookTest.java` checks the hooks against stand-in `Block`/`ExtendedBlockStorage` classes (the raw
  array throws, as with EndlessIDs). It covers air, vanilla ids, ids 256-4095, metadata masking, ids 5000/70000
  (opaque/translucent), and checkIsOre indexes that used to read another block's entry or run past the end;
* `tools/VerifyPatch.java` downloads the real hash-pinned `Eln-1.24.6.jar` and `Eln-1.24.8.jar`, runs the
  transformer on both classes and checks:
  * no `getBlockLSBArray`/`getBlockMSBArray` call is left, and every hook is called exactly once;
  * ASM's `BasicVerifier` accepts every method;
  * HotSpot's own bytecode verifier accepts the patched classes. The original classes are checked the same way as a
    control, and a deliberately broken patch was confirmed to be rejected;
  * transforming twice is a no-op, and unrelated classes come back unchanged.

Not done: a run inside the full modpack. The patched classes pass the JVM verifier, but nobody has watched them run
in the real game yet. After installing, check the log lines above, then look at an Auto Miner and switch on a
Portable Ore Scanner.

## Build

```
python3 build.py --javac javac --libs <dir with launchwrapper-1.12.jar, asm-all-5.0.3.jar, log4j-api.jar> --out dist/Akashic-ElnEndlessIdsFix-1.0.0.jar
```

Minecraft and Forge types come from `src/stubs` (SRG names, as in the running game). The stubs are compiled but not
packaged. The JAR holds only `ElnEndlessIdsPlugin`, `ElnEndlessIdsTransformer` and `hook/ElnEndlessIdsHooks`.
