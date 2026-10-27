# Open issues and unknowns

Everything here is something the repository **cannot prove**. Each item says what is known and what would close it.

## A. Product / behaviour

1. **White / translucent world rendering (Angelica 2.2.18 + Celeritas).**
   *Ruled out by evidence:* Numina `RenderState` push/pop on the failing path (beta.9), Cinematic `RawScope` GL_TEXTURE_2D leak + VSync error as the cause (beta.10), generic GLSM cache-vs-driver divergence (beta.22 probe), VAO and texture-filter-mode settings (beta.23 A/B).
   *Tried without a recorded verdict:* mapped-staging fallback (beta.24, not installed), force opaque-terrain blend off + vertex alpha probe (beta.25), draw-submission telemetry (beta.26), proxy-aware chunk tracking (beta.27), non-finite FOV guard (beta.28).
   *Not in the repository:* any statement that the symptom is fixed. Treat as **unresolved/unconfirmed**. beta.26's telemetry fields and the beta.24 checkpoint list what to read next.
2. **beta.43 acceptance in the pack** (Windows, real GPU, the real HolographicDisplays server). The hologram fast path was measured in a headless real client with HD's packets replayed through the client net handler (Angelica 2.2.37 + EntityCulling, and the full 210-JAR pack, see its checkpoint; Linux + Mesa llvmpipe only), not against a live Thermos/Crucible server nor on Windows with a GPU; holograms of other 1.7 plugins that use a different carrier (e.g. a plain invisible armor-stand-less nametag mob) are not detected and keep the vanilla path. **beta.42 acceptance in the pack** (Windows, real GPU, the full pack). The shader bridge passed a headless real client (Linux, Mesa llvmpipe, Java 8u504: Forge 1614 + UniMixins 0.3.1 + GTNHLib 0.11.51 + Angelica 2.2.37 + Mirai 0.1.19 + BSL 8.2.09 .zip), not the 212-mod pack; Complementary's DH path and the elytra fold-in were checked only as described in its checkpoint. Angelica's `.zip` folder bug (Java 8) and the class-name probe should be reported upstream (Angelica; Mirai should adopt upstream's `DistantHorizonsTweaker` + deferred mixin, which turns the bridge off by itself). **beta.41 acceptance in the pack** (offline gates only: the DragonAPI fix was checked against a reconstruction of the two DragonAPI methods, not the real V33b-ForbiddenFix1 bytecode, which is not redistributable; the recovery bridge against the real CoreTweaks `CrashHandler` and the real Angelica 2.2.37 `GbufferPrograms`, but not in a running client). **beta.40 acceptance in the pack** (items visible in NEI with Angelica 2.2.37; the 4096x4096 items-atlas case was only reproduced offline). **beta.39 acceptance in the pack** (Windows, Intel driver, all mods). A headless real client (Linux, Mesa) already passed with Angelica 2.2.37 + NecroTempus 2.0.0/2.1.0 + CinematicMenu; NecroTempus glyphs were not drawn there. beta.38 is not to be installed (inert under Mixingasm). **beta.37 acceptance** (SKLauncher boot; the diagnosis is inferred because no `hs_err` was attached, and the C2 crash did not reproduce on Linux 25.51, see `investigations/forestry-c2-8u51-linux-repro/`). beta.36 was never booted live. **beta.35 acceptance.** Installed, but no acceptance record; reason for the name "LifecycleFix" is undocumented.
3. **beta.34 acceptance.** Checkpoint says pending. (It is the base of beta.35, and beta.35 is the one in use.)
4. **beta.33 window-lifecycle guard.** Incident was observed once; no document proves the guard prevents it.
5. **beta.30.2 batcher live gates** (expected: `stagedCalls` ~19336, armed for 4096x4096): listed, outcome not recorded.
6. **`NullPointerException` in `RenderGlobal.renderSky` (crash report 2026-10-25 10:07, beta.40, Angelica 2.2.37, overworld of the survival server).** Thrown inside `renderSky` itself (no deeper frame); the crash report alone does not say which reference was null (the method carries Forge and several mixins). CoreTweaks recovered it and the game ran on. Not targeted by beta.41 (whose Angelica reset after a recovered crash may help with follow-ups). Needs `logs/fml-client-latest.log` of a session where it happens and what the player was doing (server switch through the proxy, dimension change, world load).

## B. Provenance / archaeology

7. **beta.1, beta.2, beta.5, beta.6 do not exist on disk.** beta.3 is the oldest; beta.7's delta against beta.3 includes work that presumably spans 5 and 6.
8. **beta.3, beta.7, beta.8: no source and no checkpoint.** Only binaries and their content diffs.
9. **Core `com.akashic.thepatcher` sources are missing** (30 of 104 top-level classes of beta.34 have source on disk).
10. **beta.35 source is decompiled** (CFR), hash-verified against the JAR. No original `.java` exists.
11. **Two rejected beta.28 builds** (`720a14f2...`, `b3171038...`): the second one's rejection reason ("stale") is the author's file name only.
12. **beta.16 canonical-vs-transformer reversal** is undocumented (see CHANGELOG, beta.16).
13. **Non-linear declared bases.** 12 discrepancies between declared base and nearest-by-content are tabulated in LINEAGE.md; e.g. beta.30, 30.1 and 30.2 all declare beta.28 while 30.2 is the shipped lineage.
14. **Dates are file mtimes**, which can change on copy. The ordering is consistent with every checkpoint's internal dates, but the minute values are not authoritative.

## C. Engineering debt

15. Diagnostics left in the shipped JAR (beta.31 FML phase audit, beta.32 hotspot audit).
16. ~~The pack depends on JVM arguments outside the JAR (six CompileCommands).~~ Since beta.36 the protection is carried in the JAR (compile barrier); the arguments are optional defense in depth.
17. **RGB bridge never installs on `FontRenderer`.** The log says `RGB bridge NOT installed: no recognized boundary in net.minecraft.client.gui.FontRenderer`. ThePatcher's transformers are registered during the MixinTweaker coremod injection, before `FMLDeobfTweaker` adds the deobfuscation transformer, so `@SortingIndex(100300)` does not apply and `RgbTextTransformer` sees Notch member names while it matches MCP/SRG names. Not caused by Angelica; found during the beta.38 diagnosis, not fixed (changes text rendering everywhere, needs its own build).
18. **beta.27 proxy-aware chunk tracking is inactive on Angelica 2.2.22+.** `MixinAngelicaProxyVanillaChunkTracking` targets `AngelicaChunkTracker`, removed in Angelica 2.2.22 (PR #2151); the optional mixin is skipped with `Error loading class`. Porting it means re-reading the beta.27 policy against `CeleritasWorldRenderer` / `CubeStatusTracker`.
19. **NecroTempus upstream.** beta.38 patches NecroTempus 2.0.0/2.1.0 at load time; the source fix in `versions/*beta.38*/necrotempus-source-fix/` has not been proposed to CrucibleMC yet.
20. **CI covers only beta.38+.** `.github/workflows/ci.yml` checks all release/manifest hashes but rebuilds only beta.38 to beta.43; earlier build scripts need machine paths, the real Forestry jar or Java 8u51. The `projects/` Gradle builds are not in CI.
21. No LICENSE chosen.
22. Third-party build inputs (Angelica, GTNHLib jars) are not vendored.
