# Architecture (as of 1.1.0-beta.35)

Facts below come from scanning the beta.35 JAR (`data/` + `analysis-tools/`) and from checkpoints. Where something is inference it says so.

## 1. What the JAR is

One file, `!Akashic-ThePatcher-<version>.jar` (the leading `!` makes it sort first in `mods/`). 159 entries, Java 8 bytecode (class major <= 52). It is simultaneously:

* a **Forge coremod** - `MANIFEST.MF`: `FMLCorePlugin: com.akashic.thepatcher.core.AkashicThePatcherLoadingPlugin`, which registers LaunchWrapper class transformers;
* a **Mixin provider config** - `TweakClass: ...MixinTweaker` (UniMixins) and `MixinConfigs: mixins.akashicasynccompat.json` (+ a refmap);
* a normal **mod** (`mcmod.info`, main class `AkashicThePatcherMod`) for lifecycle hooks and logging.

All patching is **client-side**; nothing here is needed on the server.

## 2. Two code families

| Family | Packages | Origin | Source in this repo |
|---|---|---|---|
| **Core bundle** | `com.akashic.thepatcher.*` (modules `rgb` 20 classes, `network` 14, `startupaudit` 14, `sampler` 7, `dimdoors` 6, ...) and `com.akashic.forbiddenbackhand`, `forbiddenhologram` | beta.3 and the overlay chain | **binary only** (except beta.4's overlay workspace, beta.34 startup-audit/forestry/windowguard sources, and the beta.35 decompiled Backhand transformer) |
| **AsyncCompat** | `dev.akashic.asynciconcompat.*`, `mixins.akashicasynccompat.json` | merged in beta.11; extended alpha.2.3 -> 2.15 | **full Gradle projects** in `projects/` |

Source coverage of the beta.34 jar: 30 of 104 top-level classes have sources on disk (details: `data/source-coverage-beta34.txt`).

## 3. Module map

**Bundle modules** (master switch `module.<name>` in `config/akashic-thepatcher.properties`, default on):
`botaniaCache`, `industrialUpgradeOreDictGuard`, `industrialUpgradeDraconicCompat`, `samplerHudGuard`, `handoff`, `backhand`, `dimDoorsClientSync` (beta.4 used `dimDoorsClientSyncGuard`), `hologram`, `rgbTextBackport`, `rgbJsonStyles`, `mekanismCreativeGuard`, `legacyRegister`, `baubles`, `necroTempusBossbar`; plus `disableBundledModuleWhenLegacyJarPresent` and `verboseSummary`.

**Startup / rendering modules** (system properties; defaults in brackets):

| Property | Purpose | Introduced |
|---|---|---|
| `akashic.asyncItemIcons` [false] | run `Item.registerIcons` on worker threads (opt-in) | beta.11 (default flipped in beta.34) |
| `akashic.stitchLayoutCache` [true] | persistent Stitcher geometry cache | beta.13 |
| `akashic.itemsAtlasBatch` [true] | batch the items atlas GL upload | beta.18 |
| `akashic.itemsAtlasBatch.guard` [true] | items batch: stay off with Angelica's `AtlasAssembler`, skip a flush with nothing cancelled | beta.40 |
| `akashic.dragonApiResourcePack.disable` [false] | DragonAPI: stop skipping resource packs that are not `AbstractResourcePack` | beta.41 |
| `akashic.crashRecoveryBridge.disable` [false] | CoreTweaks crash recovery: stop resetting Angelica/Iris render state | beta.41 |
| `akashic.crashRecoveryBridge.angelicaReset` [true] | CoreTweaks crash recovery: also run Angelica's `RenderRecovery.resetAfterCrash()` | beta.41 |
| `akashic.miraiShaderBridge.disable` [false] | Mirai x Angelica shaders: stop pointing Angelica's DH probe at Mirai's coremod and drawing the deferred translucent LODs | beta.42 |
| `akashic.angelicaShaderPackFolders.disable` [false] | Angelica: stop stripping the trailing `/` of `world0/` folder names in `.zip` shader packs (Java 8) | beta.42 |
| `akashic.hologramFastPath.disable` [false] | HolographicDisplays-style holograms: stop freezing the carriers (horse/item/slime on a wither skull) and drawing their text in one pass | beta.43 |
| `akashic.hologramFastPath.range` [unset] | hologram text range in blocks measured from the line (unset: the vanilla 1.7 rule, viewer within 64 blocks of the horse) | beta.43 |
| `akashic.agricraftIrrigation.disable` [false] | AgriCraft: keep the client config's "Disable Irrigation" instead of always registering the irrigation blocks | beta.45 |
| `akashic.backhandProxyReturn.disable` [false] | Backhand: stop accepting a mod list captured since the previous JoinGame as the proof for the next one (the return from a vanilla lobby behind the proxy ends in TRANSITION again, offhand blocked) | beta.48 |
| `akashic.baublesExpandedApi.disable` [false] | Baubles Expanded: do not add 2.2.26's default `onSlotContentsChanged` to an older embedded `IBaubleExpanded` copy (both sides) | beta.47 |
| `akashic.angelicaGlLookup.disable` [false] | AkashicBackpackCompat: do not resolve GL methods that Angelica 2.2.40 moved out of `GLStateManager` in their new class | beta.47 |
| `akashic.projectorSlotGuard.disable` [false] | Immersive Petroleum: keep the original `VIEWS[currentItem]` lookup of the projector HUD (crashes with Backhand's offhand slot 36) | beta.46 |
| `akashic.chromaPlayerApiPreload.disable` [false] | ChromatiCraft x PlayerAPI: stop holding back `ENTITYPUSHOUT` from Mixin's `EntityPlayerSP` pre-read (Et Futurum elytra) | beta.42 (standalone jar 1.0.0 before) |
| `akashic.blocksAtlasBatch` [true] | batch the blocks atlas GL upload | beta.30 |
| `akashic.forceOpaqueTerrainBlendOff` [true] | force `GL_BLEND` off on opaque terrain | beta.25 |
| `akashic.terrainVertexAlphaProbe` | vertex-alpha telemetry | beta.25 |
| `akashic.fmlPhaseAudit` | FML per-phase timing | beta.31 |
| `akashic.forestryC2Guard.*` | status properties of the Forestry guard | beta.11 |

(The exact name and default of any property should be confirmed in code before relying on this table; names were extracted from class constants.)

## 4. The patching techniques used

1. **LaunchWrapper `IClassTransformer` (ASM)** - used when a target class is loaded too early for Mixin (Forestry `Genome`, FML `LoadController`, Backhand, window lifecycle, hotspot audit).
2. **Mixin** - used for classes that load later (TextureMap, Stitcher, TextureUtil, Celeritas/Angelica internals, `ForgeHooksClient`).
3. **Fail-closed / fail-open fingerprints** - behavioural patches check the exact third-party class fingerprint (SHA-256 or bytecode shape) and *do nothing* if it differs: Forestry 4.11.36, Angelica 2.2.18 (`563d907f...`), GTNHLib 0.11.51 (`9d31e3a5...`), Numina 0.4.1.105. Batchers fail open to vanilla and stay off for the session.
4. **Kill-switches** on every behaviour change.
5. **Overlay builds** - see below.

## 5. How a release is built (the overlay model)

```
previous release jar  +  overlay of changed/added classes  ->  version-patch  ->  new jar
```

* `tools/PatchThePatcherVersion.java` (in each `versions/*/tools/`) rewrites `Implementation-Version` in the manifest and `version` in `mcmod.info` and the mod class constant.
* Output is **deterministic**: two clean builds give the same SHA-256 (asserted by every audit).
* Gate suite: *inherited 16-gate regression* (RGB/text-format behaviour, created for beta.8), JUnit tests of the new component, class-major <= 52, byte-identity of inherited classes, and for batchers a runtime smoke on real Minecraft classes.
* AsyncCompat is built by Gradle (RetroFuturaGradle) from `projects/*`; the compiled classes are copied into the overlay. **The `reobfJar` output must be used** - beta.30/30.1 broke because MCP-named classes were shipped (`NoSuchMethodError Minecraft.getMinecraft`).

## 6. Runtime-environment contract

* Minecraft 1.7.10 with Forge (exact build not recorded in these documents), Java **8u51** (the Technic launcher JRE).
* Rendering stack: Angelica 2.2.18 (the fingerprinted patches were written for it; beta.38 adds Angelica 2.2.37 support for the NecroTempus font fix and the whitespace decoration profile), Celeritas, GTNHLib 0.11.51, Hodgepodge (its own `asyncIconLoading` must stay `false`).
* Pack-side: six HotSpot `CompileCommand` exclusions (see [HANDOFF.md](HANDOFF.md)).
* Remote server network sits behind a proxy; handoff/backhand modules rely on the FML handshake classification (VANILLA/BUKKIT).

## 7. Known structural risks

* beta.35's Backhand transformer matches **obfuscated** owner/descriptor literals only (inference from decompilation) - a deobfuscated dev run would not exercise it.
* Core bundle source is missing: a fix to RGB/DimDoors/Network means decompiling or recovering the original project first.
* The passive diagnostics of beta.31 (FML phase audit) and beta.32 (hotspot audit) are still in the beta.35 JAR (the `startupaudit` package has 14 classes); they are passive but add startup work. beta.29's probes were a side branch and are not inherited.
