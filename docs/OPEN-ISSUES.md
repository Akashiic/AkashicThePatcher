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
20. **CI covers only beta.38+.** `.github/workflows/ci.yml` checks all release/manifest hashes but rebuilds only beta.38 to beta.49; earlier build scripts need machine paths, the real Forestry jar or Java 8u51. The `projects/` Gradle builds are not in CI.
21. No LICENSE chosen.
22. Third-party build inputs (Angelica, GTNHLib jars) are not vendored.

## D. Seen in the pack log of 2026-10-29 (beta.43, Angelica 2.2.40) - not fixed by beta.44

23. **The whitespace decoration fix needs a profile per Angelica release.** beta.44 adds 2.2.40; the next `drawString` change will log `Angelica whitespace decoration fix skipped: unsupported drawString fingerprint ...` again and the tab separators will shrink again. How to add a profile: beta.44's checkpoint. Upstream fix (two assignments in Angelica's whitespace branch) not reported yet.
24. **Handoff boundaries not installed.** `[Akashic-ThePatcher/Handoff] WARN: Minecraft displayGuiScreen boundary differed from the validated target: hooks=0` and the same for `NetHandlerPlayClient safe-fallback`; the original bytes are kept. Not checked whether this is new or the same Notch-names ordering as item 17.
25. **Legacy hologram module (beta.3) misses two targets.** `Entity.isInRangeToRender3d was not found` and `EntityItem.getEntityItem was not found` (the beta.43 fast path itself applied all its hooks). Likely the same naming issue as item 17.
26. **Mirai (Distant Horizons fork) stops LOD networking.** `DH-Network Client Handler Thread: Failed to handle the message. New messages will be ignored.` after `IllegalArgumentException: Duplicate or excessive LOD transfer` (`FullDataPayloadReceiver.receiveChunk`, Mirai 0.1.19-dev): after it the client receives no more server LODs in that session. Mirai's own code, not ThePatcher's.

## E. Seen in a player's report of 2026-10-29 (beta.43) - not explained by beta.45

27. **"A fatal error has occurred during the network handshake ... java.lang.NullPointerException".** Same player as beta.45's incident, same server, a screenshot only: neither of the two logs sent (`latest_18`, `latest_19`) contains it (`latest_18` never connects, `latest_19` ends three attempts with the missing AgriCraft blocks). It is raised in the same FML handshake step, so a registry mismatch is the likely cause, but this needs the `latest.log` / `fml-client-latest.log` of a session that ends with that screen.

## F. Seen in the same player's report of 2026-10-29 (beta.42 and beta.43) - after beta.46

28. **Which Backhand action left the selected slot at 36 when the HUD was drawn.** beta.46 makes Immersive Petroleum's projector HUD tolerate it; the crash report alone does not say which offhand action was running at that frame (Backhand's `useOffhandItem`/`swapToOffhand` restore the slot in `finally`, so it is either a window inside one of them or a path that does not restore it). Other mods that index a 9-slot array with `currentItem` would crash the same way; none seen yet. Needs a log of a session with the beta.46 warning `selected slot 36 is outside the 9 hotbar slots` and what the player was doing.
29. **The lobby sender of Hodgepodge-format `S35` packets.** The disconnect in `latest_20` is fully explained by the Hodgepodge mismatch (beta.46's checkpoint), but the lobby is described elsewhere as a Paper lobby behind Velocity, which would not write Hodgepodge's format; which server (or proxy component) sends those tile entity updates to 1.7.10 clients was not checked. Only matters if a client with an older Hodgepodge must be supported.

## G. Seen in two players' reports of 2026-10-30 (beta.43) - after beta.47

30. **Which jar embeds the stale `IBaubleExpanded`.** beta.47 adds the missing method and its WARN lists every jar carrying the class (the first is in use); the crash log alone does not say. `akashicbaubleslayout-1.0.0.jar` is the first suspect by class-path order (not verified: the jar is not in the repository). Clean that jar and the guard goes quiet.
31. **The real `GlApi` of AkashicBackpackCompat 1.0.0-rc1 was not available.** The bridge was gated on a shape built from the crash; if the real class resolves its methods without `Lookup.findStatic` the bridge logs `skipped ... no MethodHandles.Lookup.findStatic call` and changes nothing. Other reflective users of `GLStateManager` method names would fail the same way on Angelica 2.2.40.
32. **Forbidden mobile: tight automatic heap and a GL fence recursion.** The phone's ANR came from an `OutOfMemoryError` spiral (2432 MiB automatic heap, 2292 MiB alive after full GC, 13 s GC pauses) set off by the backpack crash; an older crash in the same zip is a `StackOverflowError` between Celeritas' `LWJGL3Service.glFenceSync` and Angelica's `GLStateManager.glFenceSync`. Both belong to the mobile launcher / GL bridge, not to ThePatcher.

## H. Seen in a player's report of 2026-10-31 (beta.43) - after beta.48

33. **Black screen after backend -> lobby -> backend behind the proxy (unresolved).** Same Velocity connection, Paper 1.21.1 + ViaVersion lobby, Forge backend. No crash; the FML handshake, the world (DIM2, player at y ~ 32), chat, players, holograms and the gamemode packet came through and the terrain pass ran. Whether chunks were missing (proxy forwarding) or not drawn (renderer after the double world swap of the switch) is not in the log. Next occurrence: F3 `C:` chunk count and F3+A. The Backhand defect found in the same log is fixed in beta.48 and is not expected to cause it.
34. **beta.48 acceptance in the pack** (lobby round trip, offhand works after it; gate in its checkpoint). The fix was checked only offline (`BackhandGate`, the log's event order through the shipped classes).

## I. Seen in the TEOF 5.5 headless test client (2026-11-01) - after beta.49

35. **beta.49 acceptance in the pack** (gate in its checkpoint: the `cross-dependent block` lines, `crossDependentSyncBlocks` in the `Finished hybrid async icon loading` line, never `Parallel block icon registration failed`). The race cannot be forced in a real client; the fix was checked offline (`CrossDepsGate`) and by real boots without the error.
36. **The cross-dependent list is static.** It comes from a bytecode scan of TEOF 5.5's mods (`versions/*beta.49*/tools/IconCrossDepsScan.java`); a block that reaches another block's icon through a helper of another class, or a mod added later, is not covered. Re-run the scan when the pack's mods change and add new classes to `CompatHooks.CROSS_DEPENDENT_BLOCKS`.
