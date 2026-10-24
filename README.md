# AkashicThePatcher

Client-side bug-patch coremod bundle for the **Forbidden Series** Minecraft **1.7.10** modpack: Forge coremod + Mixins that fix crashes, startup time and compatibility problems in other mods at class-load time.

This repository is the **complete recorded history** of the project, reconstructed from the author's files: **38 builds** from `1.1.0-beta.3` (2026-09-08) to `1.1.0-beta.35-BackhandLifecycleFix` (2026-09-25, currently in use), with each build's checkpoints, audits, tools, source (where it exists), the investigations behind them, and the provenance of every file.

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
releases/<tag>/        the JARs (38) + releases/SHA256SUMS.txt
versions/<folder>/     per-build checkpoint docs, audits, build tools, recovered source
projects/              AsyncCompat-family Gradle projects (source of the dev.akashic.asynciconcompat component)
investigations/        experiments, A/B configs and test harnesses (world-render hunt, Forestry C2 crash)
analysis-tools/        scripts used to diff and compare builds
data/                  machine-readable: manifest, excluded files, lineage, commit plan
docs/                  everything above
```

Git tags `v1.1.0-beta.*` correspond to `releases/` folders; every tag also has a GitHub Release with the JAR attached and its SHA-256.

## Which JAR do I install?

The newest, `!Akashic-ThePatcher-1.1.0-beta.35-BackhandLifecycleFix.jar` (SHA-256 `c48d3ec3b1ef35f907baca47cd78ead48730b4cd556592e529c0140d7a869c17`), is the one in use in the pack. Do **not** install the two builds marked `REJECTED` (beta.28) or the diagnostic/probe builds unless you are reproducing an investigation. The pack also needs JVM arguments outside the JAR - see [docs/HANDOFF.md](docs/HANDOFF.md).

## Honest caveats

* **Dates are file modification times** of the JARs; the git history is a *reconstruction*, one commit per build in chronological order, not the real development history.
* beta.1, 2, 5 and 6 do not exist on disk. Sources of the core bundle (`com.akashic.thepatcher.*`) are not available; beta.35's changed class is only available as a decompilation.
* The white/translucent world rendering bug that drove betas 9-28 has **no recorded fix**.
* Third-party binaries (Angelica, Forestry, GTNHLib, the Technic pack, a Minecraft client derivative) and anything private (launcher settings, server addresses, credentials) are **not** included - see [docs/PROVENANCE.md](docs/PROVENANCE.md) and `data/excluded.tsv`.
* **No license has been chosen yet**, so by default all rights are reserved by the author; reading and cloning is possible, reuse needs the author's permission.

## Verify integrity

```
cd releases && sha256sum -c SHA256SUMS.txt      # on Windows: certutil -hashfile <jar> SHA256
```
Every other file's expected hash is in `data/manifest.tsv`.
