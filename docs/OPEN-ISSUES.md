# Open issues and unknowns

Everything here is something the repository **cannot prove**. Each item says what is known and what would close it.

## A. Product / behaviour

1. **White / translucent world rendering (Angelica 2.2.18 + Celeritas).**
   *Ruled out by evidence:* Numina `RenderState` push/pop on the failing path (beta.9), Cinematic `RawScope` GL_TEXTURE_2D leak + VSync error as the cause (beta.10), generic GLSM cache-vs-driver divergence (beta.22 probe), VAO and texture-filter-mode settings (beta.23 A/B).
   *Tried without a recorded verdict:* mapped-staging fallback (beta.24, not installed), force opaque-terrain blend off + vertex alpha probe (beta.25), draw-submission telemetry (beta.26), proxy-aware chunk tracking (beta.27), non-finite FOV guard (beta.28).
   *Not in the repository:* any statement that the symptom is fixed. Treat as **unresolved/unconfirmed**. beta.26's telemetry fields and the beta.24 checkpoint list what to read next.
2. **beta.38 acceptance** (boot with Angelica 2.2.37 and the official NecroTempus jar; the gate re-implements Mixin's checks offline, Mixin itself has not applied the rewritten mixin in a running client yet). **beta.37 acceptance** (SKLauncher boot; the diagnosis is inferred because no `hs_err` was attached, and the C2 crash did not reproduce on Linux 25.51, see `investigations/forestry-c2-8u51-linux-repro/`). beta.36 was never booted live. **beta.35 acceptance.** Installed, but no acceptance record; reason for the name "LifecycleFix" is undocumented.
3. **beta.34 acceptance.** Checkpoint says pending. (It is the base of beta.35, and beta.35 is the one in use.)
4. **beta.33 window-lifecycle guard.** Incident was observed once; no document proves the guard prevents it.
5. **beta.30.2 batcher live gates** (expected: `stagedCalls` ~19336, armed for 4096x4096): listed, outcome not recorded.

## B. Provenance / archaeology

6. **beta.1, beta.2, beta.5, beta.6 do not exist on disk.** beta.3 is the oldest; beta.7's delta against beta.3 includes work that presumably spans 5 and 6.
7. **beta.3, beta.7, beta.8: no source and no checkpoint.** Only binaries and their content diffs.
8. **Core `com.akashic.thepatcher` sources are missing** (30 of 104 top-level classes of beta.34 have source on disk).
9. **beta.35 source is decompiled** (CFR), hash-verified against the JAR. No original `.java` exists.
10. **Two rejected beta.28 builds** (`720a14f2...`, `b3171038...`): the second one's rejection reason ("stale") is the author's file name only.
11. **beta.16 canonical-vs-transformer reversal** is undocumented (see CHANGELOG, beta.16).
12. **Non-linear declared bases.** 12 discrepancies between declared base and nearest-by-content are tabulated in LINEAGE.md; e.g. beta.30, 30.1 and 30.2 all declare beta.28 while 30.2 is the shipped lineage.
13. **Dates are file mtimes**, which can change on copy. The ordering is consistent with every checkpoint's internal dates, but the minute values are not authoritative.

## C. Engineering debt

14. Diagnostics left in the shipped JAR (beta.31 FML phase audit, beta.32 hotspot audit).
15. ~~The pack depends on JVM arguments outside the JAR (six CompileCommands).~~ Since beta.36 the protection is carried in the JAR (compile barrier); the arguments are optional defense in depth.
16. **RGB bridge never installs on `FontRenderer`.** The log says `RGB bridge NOT installed: no recognized boundary in net.minecraft.client.gui.FontRenderer`. ThePatcher's transformers are registered during the MixinTweaker coremod injection, before `FMLDeobfTweaker` adds the deobfuscation transformer, so `@SortingIndex(100300)` does not apply and `RgbTextTransformer` sees Notch member names while it matches MCP/SRG names. Not caused by Angelica; found during the beta.38 diagnosis, not fixed (changes text rendering everywhere, needs its own build).
17. **beta.27 proxy-aware chunk tracking is inactive on Angelica 2.2.22+.** `MixinAngelicaProxyVanillaChunkTracking` targets `AngelicaChunkTracker`, removed in Angelica 2.2.22 (PR #2151); the optional mixin is skipped with `Error loading class`. Porting it means re-reading the beta.27 policy against `CeleritasWorldRenderer` / `CubeStatusTracker`.
18. **NecroTempus upstream.** beta.38 patches NecroTempus 2.0.0/2.1.0 at load time; the source fix in `versions/*beta.38*/necrotempus-source-fix/` has not been proposed to CrucibleMC yet.
19. No CI. Tests exist only inside the per-component Gradle projects and the author's local scripts.
20. No LICENSE chosen.
21. Third-party build inputs (Angelica, GTNHLib jars) are not vendored.
