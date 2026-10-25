# beta.39 - AngelicaFontMixinTrust - 2026-10-25

## Why beta.38 is not enough
beta.38 fixed NecroTempus x Angelica 2.2.24+ by rewriting NecroTempus' mixin through the LaunchWrapper transformer chain. Its offline gates passed, but the first **real client** run (Angelica 2.2.37 + official NecroTempus 2.0.0 + beta.38) still crashed with the same `NoClassDefFoundError: BatchingFontRenderer`. The log has the reason:

```
[mixingasm/]:   Not trusting transformer com.akashic.thepatcher.module.necrotempus.NecroTempusAngelicaFontTransformer
```

UniMixins' **Mixingasm** adds every transformer it does not trust to Mixin's transformer exclusion list, so Mixin reads mixin classes without them. beta.38's transformer therefore never touched the mixin in a real client (the providers, loaded normally, would have been patched). The offline gate ran the transformer directly and modelled Mixin's resolution checks, but not Mixingasm. **Do not install beta.38.**

Mixingasm trusts a transformer that matches its config patterns, is registered through its API, implements `IMixinSafeTransformer`, or carries `@MixinSafeTransformer` (UniMixins 0.3.1, `Mixingasm` line 80).

## Fix
`NecroTempusAngelicaFontTransformer` is annotated `@makamys.mixingasm.api.MixinSafeTransformer` (RUNTIME retention). Nothing else changes: the transformer's 18 methods are instruction-identical to beta.38. The annotation is compiled against a stub; at run time the JVM ignores it when UniMixins/Mixingasm is absent. Running it on mixin classes is safe: it touches three NecroTempus classes only and is idempotent.

## Gates (reports/final-build-audit.json) - all PASS
* Deterministic double build, SHA-256 `8054509c0dc5067e2e043b34f6b93480e29aca285eabb1fcb3d7d8ac9ca73cbd`, 335978 B, 160 entries. Delta vs beta.38: transformer, mod class, manifest, mcmod.info; the other 156 byte-identical; the recompiled hooks class is byte-identical to beta.38's.
* Mixingasm's own trust rule with the real `+unimixins-all-1.7.10-0.3.1.jar`: beta.38 `trusted=false`, beta.39 `trusted=true`. Without UniMixins on the classpath the transformer still loads and runs.
* beta.38's NecroTempus x Angelica gates, re-run on the beta.39 jar (2.0.0/2.1.0 x 2.2.37 patched; 2.2.23, 2.2.18 and server untouched; source-fixed NecroTempus left alone; textureId hook).

## Real-client runs (reports/real-client/)
A headless client was built for this: Minecraft 1.7.10 + Forge 10.13.4.1614 + UniMixins 0.3.1 + GTNHLib 0.11.51, Mesa llvmpipe (GL 4.5 core, the context Angelica creates), Xvfb + openbox, offline user `ACMenuQA`. The scripts are in `tools/headless-client/` (`setup.py`, `run.sh`). Mod hashes and the key log lines of every run are in `runs.json`; `*-frames.jpg` are screenshots every 12 s.

| Run | Mods (besides Forge/UniMixins/GTNHLib/CinematicMenu 0.7.0-alpha.4) | Result |
|---|---|---|
| A | Angelica 2.2.18 | splash overlay + cinematic menu |
| B | Angelica 2.2.37 | same as A; menu frame differs from A in 28 of 921,600 pixels (animated particles) |
| C | Angelica 2.2.37, NecroTempus 2.0.0 + OmniConfig, no ThePatcher | the pack's crash reproduced: `Mixin apply for mod necrotempus failed`, `Splash thread Exception`, crash report |
| D | as C + beta.38 | **same crash**; `Not trusting transformer ...NecroTempusAngelicaFontTransformer` |
| E | as C + beta.39 | `Trusting transformer`, `font fix armed/applied` (Mixin accepted the rewritten mixin), whitespace fix installed, splash overlay, cinematic menu, no crash |
| F | as E with NecroTempus 2.1.0 | same as E |

`config/splash.properties` stayed `enabled=true` in all runs. The C/D crash happens before `SplashProgress.finish()`, which is the only place Forge 1614 writes `enabled=false` (`disableSplash()`, when the splash thread has died and the game still reaches the end of loading).

Not exercised in a real client: NecroTempus custom glyphs / modern fonts actually being drawn (no glyph resource pack in the test instance, so the provider classes never loaded); Windows / Intel driver; the full pack.

## Live acceptance gate
Same as beta.38's checkpoint, plus the log must show `Trusting transformer com.akashic.thepatcher.module.necrotempus.NecroTempusAngelicaFontTransformer` (only visible with debug logging) or, at INFO level, `NecroTempus Angelica font fix applied: ...BatchingFontRendererMixin`. If `config/splash.properties` says `enabled=false` after the earlier crashes, set it back to `true` (CinematicMenu's loading screen draws on top of Forge's splash).
