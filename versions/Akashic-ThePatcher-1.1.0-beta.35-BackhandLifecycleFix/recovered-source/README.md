# Akashic-ThePatcher beta.35 — recovered source fragment

## What this package is

This is a **source recovery of the only changed code class found when comparing the locally installed** `!Akashic-ThePatcher-1.1.0-beta.35-BackhandLifecycleFix.jar` **against the available beta.34 JAR**. The Java file was decompiled from the exact beta.35 class with CFR 0.152. It is not the original human-authored source and is not represented as a full source tree/build kit.

## Provenance and comparison

- Local artifact: `!Akashic-ThePatcher-1.1.0-beta.35-BackhandLifecycleFix.jar`
- Artifact SHA-256: `C48D3EC3B1EF35F907BACA47CD78EAD48730B4CD556592E529C0140D7A869C17`
- Artifact size: `330348` bytes
- Local file timestamp: `2026-09-25 23:54:00 -03:00`
- Comparison baseline: `!Akashic-ThePatcher-1.1.0-beta.34-ItemIconMainThread.jar`
- Baseline SHA-256: `FD13DEA51AD36B04C2357083B136F1174B4882C572F86C0492875E9B356570A8`
- Both JARs had 159 entries. Exactly three entries differed: `com/akashic/forbiddenbackhand/core/BackhandBackendTransformer.class`, `META-INF/MANIFEST.MF`, and `mcmod.info`.

## Decompiled class differences observed

The beta.35 bytecode contains obfuscated runtime identifiers in the Backhand packet transformer:

- `NET_HANDLER`: `bjb`
- packet descriptor: `(Lft;)V`
- `syncCurrentPlayItem` obfuscated alias: `k`
- JoinGame packet descriptor prefix: `(Lhd;`

The baseline beta.34 class used MCP-style names/descriptors for those references. This identifies the bytecode-level differences; it does **not** prove why the release was named `BackhandLifecycleFix` or establish live runtime acceptance.

## Limits

- This archive contains one recovered `.java` class and this report only.
- Dependencies and support classes (`PatchConfig`, `TransformerState`, `PatchLog`, LaunchWrapper, ASM, Minecraft/Forge) are referenced but not bundled here.
- No claim of byte-identical rebuild or full Minecraft/Forge acceptance is made.
- To obtain the original complete beta.35 source/build kit, the original source checkout/export must still be located; it was not found in the searched local project roots or Library artifacts.
