# AkashicThePatcher

Client-side bug-patch coremod bundle for the **Forbidden Series** Minecraft **1.7.10** modpack: Forge coremod + Mixins that fix crashes, startup time and compatibility problems in other mods at class-load time.

This repository is the **complete recorded history** of the project, reconstructed from the author's files: **45 builds** from `1.1.0-beta.3` (2026-09-08) to `1.1.0-beta.42-MiraiShaderBridge` (2026-10-26, current), with each build's checkpoints, audits, tools, source (where it exists), the investigations behind them, and the provenance of every file.

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

The newest, `!Akashic-ThePatcher-1.1.0-beta.42-MiraiShaderBridge.jar` (SHA-256 `245e67cbf8f82e4395a9706c21bbee2ebd94911774600aa1e06f188adc0415df`). beta.42 = beta.41 plus **Angelica shaders for Akashic: Horizons of The Mirai** (the pack's Distant Horizons fork): Angelica's Iris now detects Mirai and draws its distant terrain (LODs) with the shader pack's DH programs, Mirai draws the deferred translucent LODs (distant water), and on Java 8 Angelica finds the `world0/` programs of `.zip` shader packs such as BSL (before, BSL ran none of its programs on Java 8). It also **includes the Et Futurum elytra fix** (Akashic-ChromaPlayerApiPreloadFix 1.0.0, a separate JAR before; remove that JAR). One JAR carries everything from beta.38 to beta.42 (checked entry by entry in its build audit). beta.41 = beta.40 plus two crash fixes: DragonAPI no longer crashes (`ClassCastException ... WaystonesAlternateResourcePack`) on ChromatiCraft/RotaryCraft block entities when Waystones' "Modernity Textures" pack is selected, and after a crash that CoreTweaks' crash handler recovers, Angelica/Iris render state is reset, so the game no longer crashes again with `GbufferPrograms in weird state` at the next block outline. beta.40 = beta.39 plus a fix for **invisible items (NEI, inventory, hand) with Angelica 2.2.24+**: the items-atlas upload batcher no longer overwrites the atlas Angelica already uploaded. It keeps everything in beta.37 (the Forestry C2 protection inside the JAR, works with Technic, SKLauncher and others) and makes the pack work with **Angelica 2.2.24+** (tested against 2.2.37): it fixes NecroTempus' Angelica font integration at load time (the `NoClassDefFoundError: BatchingFontRenderer` start-up crash) and restores the RGB whitespace underline fix on Angelica 2.2.37. **Do not install beta.38**: its NecroTempus fix is skipped in a real client (UniMixins' Mixingasm does not trust it); beta.39 is the working version, verified in a headless real client. Do **not** install the two builds marked `REJECTED` (beta.28) or the diagnostic/probe builds unless you are reproducing an investigation. Other pack-side requirements are in [docs/HANDOFF.md](docs/HANDOFF.md).

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

**CI (GitHub Actions, `.github/workflows/ci.yml`):** every push checks both, downloads the hash-pinned third-party inputs (`tools/ci/fetch_build_libs.sh`) and rebuilds beta.38, beta.39, beta.40, beta.41 and beta.42 from scratch, comparing them byte for byte with the committed JARs. Each run also uploads the newest build as four separate, un-zipped Actions artifacts: the JAR, a `-dev` JAR (Minecraft members mapped to MCP names), a `-sources` JAR (with `SOURCES.md` saying, per class, whether the source is the one stored with that build, an older recorded one, or missing) and `SHA256SUMS.txt` (`tools/ci/make_artifacts.py`). On `main` it then publishes a GitHub Release for every `releases/<tag>/` folder that has none (`tools/ci/publish_releases.py`): tag on the commit that added the JAR, notes from the CHANGELOG. Two files under `versions/...beta.9-MPS-Angelica/` were moved to a shorter directory so the repository clones on Windows; see `data/relocated.tsv`.
