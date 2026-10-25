# AkashicThePatcher

Client-side bug-patch coremod bundle for the **Forbidden Series** Minecraft **1.7.10** modpack: Forge coremod + Mixins that fix crashes, startup time and compatibility problems in other mods at class-load time.

This repository is the **complete recorded history** of the project, reconstructed from the author's files: **43 builds** from `1.1.0-beta.3` (2026-09-08) to `1.1.0-beta.40-ItemsAtlasAngelicaGuard` (2026-10-25, current), with each build's checkpoints, audits, tools, source (where it exists), the investigations behind them, and the provenance of every file.

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

The newest, `!Akashic-ThePatcher-1.1.0-beta.40-ItemsAtlasAngelicaGuard.jar` (SHA-256 `242f07422c1a8013ca72755b8cc3765636a2cc1a3f04b98075475707b78676f2`). beta.40 = beta.39 plus a fix for **invisible items (NEI, inventory, hand) with Angelica 2.2.24+**: the items-atlas upload batcher no longer overwrites the atlas Angelica already uploaded. It keeps everything in beta.37 (the Forestry C2 protection inside the JAR, works with Technic, SKLauncher and others) and makes the pack work with **Angelica 2.2.24+** (tested against 2.2.37): it fixes NecroTempus' Angelica font integration at load time (the `NoClassDefFoundError: BatchingFontRenderer` start-up crash) and restores the RGB whitespace underline fix on Angelica 2.2.37. **Do not install beta.38**: its NecroTempus fix is skipped in a real client (UniMixins' Mixingasm does not trust it); beta.39 is the working version, verified in a headless real client. Do **not** install the two builds marked `REJECTED` (beta.28) or the diagnostic/probe builds unless you are reproducing an investigation. Other pack-side requirements are in [docs/HANDOFF.md](docs/HANDOFF.md).

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

**CI (GitHub Actions, `.github/workflows/ci.yml`):** every push checks both, downloads the hash-pinned third-party inputs (`tools/ci/fetch_build_libs.sh`) and rebuilds beta.38, beta.39 and beta.40 from scratch, comparing them byte for byte with the committed JARs. Each run also uploads the newest build as four separate, un-zipped Actions artifacts: the JAR, a `-dev` JAR (Minecraft members mapped to MCP names), a `-sources` JAR (with `SOURCES.md` saying, per class, whether the source is the one stored with that build, an older recorded one, or missing) and `SHA256SUMS.txt` (`tools/ci/make_artifacts.py`). On `main` it then publishes a GitHub Release for every `releases/<tag>/` folder that has none (`tools/ci/publish_releases.py`): tag on the commit that added the JAR, notes from the CHANGELOG. Two files under `versions/...beta.9-MPS-Angelica/` were moved to a shorter directory so the repository clones on Windows; see `data/relocated.tsv`.
