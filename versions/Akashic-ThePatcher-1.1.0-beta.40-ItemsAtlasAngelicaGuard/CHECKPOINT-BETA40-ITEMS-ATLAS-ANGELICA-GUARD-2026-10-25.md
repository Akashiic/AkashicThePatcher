# beta.40 - ItemsAtlasAngelicaGuard - 2026-10-25

## Incident
With beta.39 and **Angelica 2.2.37** the pack started, but in NEI (and in the inventory / in hand) part of the items were **invisible**. Blocks were fine. The client log had the reason:

```
ItemsAtlasBatch armed path=textures/items texture=149 atlas=4096x4096 stageBytes=67108864
ItemsAtlasBatch FLUSH path=textures/items reason=normal atlas=4096x4096 stagedCalls=1 cancelled=0 firstVanilla=true stagedPixels=256 batchGlCalls=16 uploadedBytes=67108864 ...
AtlasProfile path=textures/items ... upload=4ms/16518
```

## Root cause
`ItemsAtlasUploadBatcher` (beta.18/19) speeds up the first items-atlas load: a HEAD inject on `TextureUtil.uploadTextureMipmap` copies each sprite into a CPU copy of the whole atlas and cancels the GL call; before `onTextureStitchedPost` the copy is uploaded over the **whole** atlas in 16 large `glTexSubImage2D` calls. That is only correct when every sprite went through `uploadTextureMipmap`.

Angelica 2.2.24 (PR #2167 "Parallelize atlas loading", class `com.gtnewhorizons.angelica.textures.atlas.AtlasAssembler`) uploads the stitched sprites itself, from `TextureMap.loadTextureAtlas` (`@ModifyExpressionValue` on `Stitcher.getStichSlots`), and its `@Redirect` of `uploadTextureMipmap` returns early for sprites it already uploaded. Only one sprite reached the batcher (the first, which the batcher always lets through), so the 64 MB copy was empty except for 16x16 pixels, and the flush overwrote Angelica's finished atlas with transparent texels. Items drawn from `textures/items` vanished; `ItemBlock`s (blocks atlas) and custom renderers were unaffected. The blocks batcher (beta.30, `policy=exact-regions`) only uploads the regions it staged and was harmless (`uploadedBytes=1296`).

Immediate workaround on beta.39: `-Dakashic.itemsAtlasBatch=false`.

## Fix
New class `dev.akashic.asynciconcompat.upload.ItemsAtlasAngelicaGuard` and two calls inserted into the shipped `ItemsAtlasUploadBatcher` bytecode (`tools/PatchBeta40Classes.java`; the class has no exact source, see SOURCES):

* `begin`: after the `textures/items` check, `if (ItemsAtlasAngelicaGuard.bypassBatch(path)) return;` - the batch is not armed when `AtlasAssembler.class` is on the class path (Angelica 2.2.24+, which already batches the upload). Log: `ItemsAtlasBatch bypass path=textures/items reason=angelica-atlas-assembler`.
* `flush`: before uploading, `if (skipFlush(cancelledUploads, ...)) { STATE.remove(); startupBatchConsumed = true; return; }` - when no staged upload was cancelled, GL already has every staged texel, so nothing is uploaded. Log: `ItemsAtlasBatch skip-flush ...`. This protects any other mod that uploads atlas sprites without `uploadTextureMipmap`.

Kill-switch: `-Dakashic.itemsAtlasBatch.guard=false` (beta.39 behaviour). `-Dakashic.itemsAtlasBatch=false` still disables the batcher entirely. With Angelica 2.2.23 or older nothing changes (no `AtlasAssembler`; a vanilla batch has cancelled uploads and is flushed as before).

## Gates (reports/final-build-audit.json) - all PASS
* Deterministic double build, SHA-256 `242f07422c1a8013ca72755b8cc3765636a2cc1a3f04b98075475707b78676f2`, 337237 B, 161 entries. vs beta.39: `ItemsAtlasUploadBatcher`, mod class, manifest, mcmod.info changed; `ItemsAtlasAngelicaGuard` added; the other 156 byte-identical.
* `MethodDiff`: only `begin` and `flush` of `ItemsAtlasUploadBatcher` differ; header and fields unchanged; both methods pass ASM's `BasicVerifier` and the JVM's `-Xverify:all`.
* `ItemsAtlasGuardGate` (the real batcher classes, SRG stubs, a simulated GL texture pre-filled the way AtlasAssembler leaves it):
  * beta.39: the flush overwrites all 4,194,304 texels of a filled 2048x2048 atlas - **the bug, reproduced**;
  * beta.40: atlas intact, 0 uploads; a vanilla batch (2 of 3 sprites cancelled) is still uploaded and the cancelled sprites reach the texture;
  * beta.40 + the real Angelica 2.2.37 jar on the class path: the batch never arms;
  * beta.40 + Angelica 2.2.23 / 2.2.18: as without Angelica;
  * beta.40 + 2.2.37 + `-Dakashic.itemsAtlasBatch.guard=false`: beta.39 behaviour (kill-switch works).

## Real-client runs (reports/real-client/)
Headless client from beta.39 (`../Akashic-ThePatcher-1.1.0-beta.39-AngelicaFontMixinTrust/tools/headless-client/`), 150 s each:

| Run | Mods | Result |
|---|---|---|
| G | beta.40, Angelica 2.2.37, NecroTempus 2.0.0, UniMixins, GTNHLib, CinematicMenu | `NecroTempus Angelica font fix applied`, `ItemsAtlasBatch bypass path=textures/items reason=angelica-atlas-assembler`, splash, cinematic menu, no crash |
| H | beta.40, Angelica 2.2.18, UniMixins, GTNHLib, CinematicMenu | guard inactive (`reason=small-atlas`, as beta.39), splash, cinematic menu, no crash |

Limit: the test instance has only vanilla items (16x16 items atlas), so the 4096x4096 NEI scenario itself was not reproduced in a real client; it is covered by the gate above and by the user's log.

## Live acceptance gate
Angelica 2.2.37, full pack: the log must show `ItemsAtlasBatch bypass path=textures/items reason=angelica-atlas-assembler` and **no** `ItemsAtlasBatch FLUSH`; items in NEI, inventory and hand must be visible. Rollback: beta.39 with `-Dakashic.itemsAtlasBatch=false`.
