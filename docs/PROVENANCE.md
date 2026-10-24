# Provenance - where every byte came from, and what was left out

This repository was **reconstructed on 2026-10-24** from two folders on the author's PC. It was not developed in git. Nothing here was invented to fill gaps; gaps are listed in [OPEN-ISSUES.md](OPEN-ISSUES.md).

## Sources

| Source folder (on the author's PC) | Contents used |
|---|---|
| `Desktop\Codex` | releases, version folders with checkpoints/audits/tools, AsyncCompat Gradle projects, investigations, analysis scripts, the beta.35 recovery material |
| `AkashicDev\ThePatcher-1.1.0-beta.4` | the beta.4 workspace (base beta.3 JAR, overlay sources, evidence dumps) |
| `Technic modpacks` folder (read-only lookup) | the exact beta.35 JAR, located and hash-verified (SHA-256 `c48d3ec3...`) |

## How trust was established

* Every committed file has a row in `data/manifest.tsv` (`path, source, bytes, sha256`): **1,190 files**.
* Every released JAR is in `releases/SHA256SUMS.txt` (38 lines) and in `data/commit_plan.json`.
* Parentage was computed, not copied: SHA-256 of whole JARs, then per-ZIP-entry CRC-32 diffs between every pair. `data/lineage.json` keeps both the **declared base** (what the build's own audit/checkpoint says) and the **nearest by content**; where they differ the docs say so.
* beta.35's recovered source (`README.md`, `BETA34-TO-BETA35-DIFF.md`, the decompiled `BackhandBackendTransformer.java`) was transferred as text and re-verified by hash after transfer; the 3-entry diff against beta.34 is hash-proven.

## Git history is a reconstruction

Commits are in chronological order of the **JAR modification time**, one per recorded build, with `GIT_AUTHOR_DATE` / `GIT_COMMITTER_DATE` set to that time. Commit bodies repeat the SHA-256, size, declared base and note. Documentation, data tables, projects, investigations and tools were added afterwards in commits dated **2026-10-24** (the real date). Tags `v1.1.0-beta.*` mark each build. So: `git log` reads like a development history, but the *dates are archaeological*.

## What was deliberately NOT committed

342 candidate files were excluded; the full list with reason, size and SHA-256 is `data/excluded.tsv`. Categories:

| Reason (column 4 of `excluded.tsv`) | Notes |
|---|---|
| Duplicate of a released artifact | the same JAR existed in many folders; one copy is under `releases/`, hash recorded |
| Third-party binaries | Angelica/GTNHLib jars, Forestry jars, Technic `modpack.jar`, a Minecraft client derivative used as test fixture (~234 MB), Dimensional Doors disassembly, decompiled third-party classes |
| Privacy | launcher/game settings with server address and client id, `options.txt`, files naming the game server host, one script containing a plaintext credential |
| Generated | build/run output, bytecode caches, rotated logs, pruned directories |
| Unrelated / unconfirmed | other projects or tools that happened to sit beside ThePatcher |

A **masked scan** of the whole staged tree against every sensitive token found in the excluded files returned 0 hits before commit, and the same scan is repeated on the final clone.

## Things from the same PC that are not part of this project

The Codex folder contains other, separate projects and later experiments (for example disconnect/splash/backpack/logical-side compatibility work created after beta.35). They were **not mixed in**; if they should become part of this history, add them as their own repositories or as new versions with the same procedure.

## Reproducing the staging

`analysis-tools/` holds the version-diff and lineage scripts. The staging script itself lives outside the repository; its rules are fully described by `data/manifest.tsv` and `data/excluded.tsv`.

## Related tables

[PROVENANCE-TABLES.md](PROVENANCE-TABLES.md) (generated): per-build artifact table, folders merged into each commit, discrepancies. [LINEAGE.md](LINEAGE.md): graph and chain.
