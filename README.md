# AkashicThePatcher

Client-side bug-patch coremod bundle for the **Forbidden Series** Minecraft **1.7.10** modpack: Forge coremod + Mixins that fix crashes, startup time and compatibility problems in other mods at class-load time.

This repository is the **complete recorded history** of the project, reconstructed from the author's files: **52 builds** from `1.1.0-beta.3` (2026-09-08) to `1.1.0-beta.49-BlockIconCrossDeps` (2026-11-01, current), with each build's checkpoints, audits, tools, source (where it exists), the investigations behind them, and the provenance of every file.

> Portuguese version: [README.pt-BR.md](README.pt-BR.md)

## Start here

| If you want to... | Read |
|---|---|
| Understand the story | [docs/HISTORY.md](docs/HISTORY.md) |
| See what each version changed (newest first) | [docs/CHANGELOG.md](docs/CHANGELOG.md) |
| See which build descends from which (proven by hashes) | [docs/LINEAGE.md](docs/LINEAGE.md) |
| Understand the code and build model | [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) |
| Continue the work | [docs/HANDOFF.md](docs/HANDOFF.md) |
| Know what is unknown or unresolved | [docs/OPEN-ISSUES.md](docs/OPEN-ISSUES.md) |
| Know where files came from / what was left out | [docs/PROVENANCE.md](docs/PROVENANCE.md), [docs/PROVENANCE-TABLES.md](docs/PROVENANCE-TABLES.md) |

## Layout

```
releases/<tag>/        the JARs (43) + releases/SHA256SUMS.txt
versions/<folder>/     per-build checkpoint docs, audits, build tools, recovered source
projects/              AsyncCompat-family Gradle projects (source of the dev.akashic.asynciconcompat component)
investigations/        experiments, A/B configs and test harnesses (world-render hunt, Forestry C2 crash)
analysis-tools/        scripts used to diff and compare builds
data/                  machine-readable: manifest, excluded files, lineage, commit plan
docs/                  everything above
```

Git tags `v1.1.0-beta.*` correspond to `releases/` folders; every tag also has a GitHub Release with the JAR attached and its SHA-256.

## Which JAR do I install?

The newest, `!Akashic-ThePatcher-1.1.0-beta.49-BlockIconCrossDeps.jar` (SHA-256 `c0b533a9756eb7b26b3ba050226dbc2a4c953d7ad9d222a7016ce14963fd2fef`). beta.49 = beta.48 plus a fix for a **boot-time race of the parallel icon loader**: Et Futurum's red sandstone slab (and 27 other block classes found by a bytecode scan of the pack: Et Futurum, ChromatiCraft, ReactorCraft, RotaryCraft, Twilight Forest, Magic Bees, Botania, Thaumic Horizons, Extra Utilities) reads another block's icon while registering its own; on a worker it could run first and abort the texture reload (`Parallel block icon registration failed`, `NullPointerException` in `BlockSandStone.getIcon`). Those blocks now run on the client thread after the jobs before them (kill-switch `-Dakashic.iconCrossDeps.disable=true`). beta.48 = beta.47 plus a **Backhand fix behind the proxy**: after backend -> vanilla lobby -> backend on one connection, the offhand stayed blocked for the rest of the session; now the backend's mod list counts for the JoinGame that follows it (kill-switch `-Dakashic.backhandProxyReturn.disable=true`). beta.47 = beta.46 plus two crash fixes: putting Et Futurum's **elytra in a bauble slot** no longer throws `NoSuchMethodError: ...IBaubleExpanded.onSlotContentsChanged` (an older copy of the Baubles Expanded API embedded in another jar was loaded instead of 2.2.26's; ThePatcher adds the missing default method and the log names the jars carrying the copy), and with **Angelica 2.2.40** opening the inventory or seeing a player who wears an **Adventure Backpack** no longer crashes in AkashicBackpackCompat (`Missing GL API glPushMatrix in class ...GLStateManager`: Angelica 2.2.40 moved those methods to other classes, ThePatcher resolves them there). Details and the phone ANR analysis in its checkpoint; kill-switches `-Dakashic.baublesExpandedApi.disable=true`, `-Dakashic.angelicaGlLookup.disable=true`. beta.46 = beta.45 plus an **Immersive Petroleum projector guard**: with Backhand, holding the projector while the offhand is in use crashed the client (`ArrayIndexOutOfBoundsException: 36` in `ProjectorClient.selectedView`, the HUD looked up the projection of slot 36, Backhand's offhand slot, in a 9-slot array); that slot now means "no projection" (details in its checkpoint; kill-switch `-Dakashic.projectorSlotGuard.disable=true`). The same checkpoint explains a lobby disconnect (`DecoderException: java.io.EOFException`, then `Bad packet id 91`) caused by a Hodgepodge older than 2.7.194 in the client, which no ThePatcher version fixes: keep the pack's Hodgepodge. beta.45 = beta.44 plus an **AgriCraft irrigation guard**: a client whose `config/agricraft/Configuration.cfg` has `"Disable Irrigation"=true` did not register AgriCraft's water tank, channels, valve and sprinkler, so joining the server (which has them) ended with "Connection Lost - Fatally missing blocks and items" (log: `Failed to connect to server: there are 10 missing blocks and items`); the client now always registers them (details in its checkpoint; kill-switch `-Dakashic.agricraftIrrigation.disable=true`). beta.44 = beta.43 plus the **Angelica 2.2.40** profile of the whitespace decoration fix: with Angelica 2.2.40, struck-through/underlined spaces (the NecroTempus tab list separators) were cut short because the fix did not recognise 2.2.40's `drawString` (log: `Angelica whitespace decoration fix skipped: unsupported drawString fingerprint dba827d8...`); details in its checkpoint. beta.43 = beta.42 plus a **hologram fast path** for HolographicDisplays-style holograms (on a 1.7 client every line is a horse aged -1700000 riding a wither skull): the carriers stop ticking (no skull ray traces or smoke particles), the giant inverted horse models are no longer drawn (not in the shader shadow pass either), and all hologram text is drawn in one pass, like a 1.21 client draws text displays; the same lines stay visible. In a headless real client with HolographicDisplays' packets, 900 lines went from 24 to 48 FPS in a small instance and from 3.5 to 11.4 FPS with the full pack (details and the live gate in its checkpoint; kill-switch `-Dakashic.hologramFastPath.disable=true`). One JAR carries everything from beta.38 to beta.43. beta.42 = beta.41 plus **Angelica shaders for Akashic: Horizons of The Mirai** (the pack's Distant Horizons fork): Angelica's Iris now detects Mirai and draws its distant terrain (LODs) with the shader pack's DH programs, Mirai draws the deferred translucent LODs (distant water), and on Java 8 Angelica finds the `world0/` programs of `.zip` shader packs such as BSL (before, BSL ran none of its programs on Java 8). It also **includes the Et Futurum elytra fix** (Akashic-ChromaPlayerApiPreloadFix 1.0.0, a separate JAR before; remove that JAR). One JAR carries everything from beta.38 to beta.42 (checked entry by entry in its build audit). beta.41 = beta.40 plus two crash fixes: DragonAPI no longer crashes (`ClassCastException ... WaystonesAlternateResourcePack`) on ChromatiCraft/RotaryCraft block entities when Waystones' "Modernity Textures" pack is selected, and after a crash that CoreTweaks' crash handler recovers, Angelica/Iris render state is reset, so the game no longer crashes again with `GbufferPrograms in weird state` at the next block outline. beta.40 = beta.39 plus a fix for **invisible items (NEI, inventory, hand) with Angelica 2.2.24+**: the items-atlas upload batcher no longer overwrites the atlas Angelica already uploaded. It keeps everything in beta.37 (the Forestry C2 protection inside the JAR, works with Technic, SKLauncher and others) and makes the pack work with **Angelica 2.2.24+** (tested against 2.2.37): it fixes NecroTempus' Angelica font integration at load time (the `NoClassDefFoundError: BatchingFontRenderer` start-up crash) and restores the RGB whitespace underline fix on Angelica 2.2.37. **Do not install beta.38**: its NecroTempus fix is skipped in a real client (UniMixins' Mixingasm does not trust it); beta.39 is the working version, verified in a headless real client. Do **not** install the two builds marked `REJECTED` (beta.28) or the diagnostic/probe builds unless you are reproducing an investigation. Other pack-side requirements are in [docs/HANDOFF.md](docs/HANDOFF.md).

## Honest caveats

* **Dates are file modification times** of the JARs; the git history is a *reconstruction*, one commit per build in chronological order, not the real development history.
* beta.1, 2, 5 and 6 do not exist on disk. Sources of the core bundle (`com.akashic.thepatcher.*`) are not available; beta.35's changed class is only available as a decompilation.
* The white/translucent world rendering bug that drove betas 9-28 has **no recorded fix**.
* Third-party binaries (Angelica, Forestry, GTNHLib, the Technic pack, a Minecraft client derivative) and anything private (launcher settings, server addresses, credentials) are **not** included - see [docs/PROVENANCE.md](docs/PROVENANCE.md) and `data/excluded.tsv`.
* **No license has been chosen yet**, so by default all rights are reserved by the author; reading and cloning is possible, reuse needs the author's permission.

## Verify integrity

```
sha256sum -c releases/SHA256SUMS.txt      # run from the repository root; on Windows: certutil -hashfile <jar> SHA256
```
Every other file's expected hash is in `data/manifest.tsv` (`python3 tools/ci/check_manifest.py` checks them all).

**CI (GitHub Actions, `.github/workflows/ci.yml`):** every push checks both, downloads the hash-pinned third-party inputs (`tools/ci/fetch_build_libs.sh`) and rebuilds beta.38 to beta.49 from scratch, comparing them byte for byte with the committed JARs. Each run also uploads the newest build as four separate, un-zipped Actions artifacts: the JAR, a `-dev` JAR (Minecraft members mapped to MCP names), a `-sources` JAR (with `SOURCES.md` saying, per class, whether the source is the one stored with that build, an older recorded one, or missing) and `SHA256SUMS.txt` (`tools/ci/make_artifacts.py`). On `main` it then publishes a GitHub Release for every `releases/<tag>/` folder that has none (`tools/ci/publish_releases.py`): tag on the commit that added the JAR, notes from the CHANGELOG. Two files under `versions/...beta.9-MPS-Angelica/` were moved to a shorter directory so the repository clones on Windows; see `data/relocated.tsv`.
