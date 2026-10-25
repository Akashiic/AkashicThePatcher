# beta.38 - AngelicaFontCompat - 2026-10-25

## Incident
Updating Angelica to **2.2.37** made the client crash during start-up:

```
java.lang.NoClassDefFoundError: com/gtnewhorizons/angelica/client/font/BatchingFontRenderer
    at net.minecraft.client.gui.FontRenderer.handler$zgo000$angelica$injectBatcher(FontRenderer.java:1532)
    at net.minecraft.client.gui.FontRenderer.<init>(FontRenderer.java:92)
```

The real error is earlier in `latest.log` (22:38:07, splash thread):

```
Mixin apply for mod necrotempus failed mixins.necrotempus.json:angelica.BatchingFontRendererMixin
@Shadow method pushDrawCmd ... was not located in the target class BatchingFontRenderer
```

## Root cause
Angelica **2.2.24** (PR #2165, "Various font improvements and fixes") changed its font API from `ResourceLocation` to the GL texture id:

| Angelica | `BatchingFontRenderer.pushDrawCmd` | `FontProvider.getTexture` |
|---|---|---|
| <= 2.2.23 | `(int, int, ResourceLocation, boolean)` | `ResourceLocation getTexture(char)` |
| >= 2.2.24 (pack: 2.2.37) | `(int, int, int, boolean)` | `int getTexture(char)` |

NecroTempus 2.0.0 (installed) and 2.1.0 (latest release) still target the old API:

* `BatchingFontRendererMixin` shadows and redirects `pushDrawCmd(IILnet/minecraft/util/ResourceLocation;Z)V`. The shadow is not found. `mixins.necrotempus.json` is `"required": true`, so the whole class fails to transform, `BatchingFontRenderer` never loads, and every later `new FontRenderer()` throws `NoClassDefFoundError`.
* `AngelicaGlyphProvider` and `AngelicaModernFontProvider` implement `FontProvider` with `ResourceLocation getTexture(char)`. Even with the mixin fixed, Angelica would call `getTexture(C)I` and get `AbstractMethodError`.

A scan of every reference from both NecroTempus jars to Angelica 2.2.37 (`GLStateManager`, `FontStrategist`, annotations, interfaces) found exactly these three breaks and nothing else. In Angelica 2.2.37 the value of `getTexture` has a single consumer: it is passed to `pushDrawCmd(idx, n, texture, unicode)`, which NecroTempus redirects.

## Fix 1 - NecroTempus x Angelica font API (new `NecroTempusAngelicaFontTransformer`)
Mixin reads mixin classes through the LaunchWrapper transformer chain (`MixinServiceLaunchWrapper.getClassBytes(name, true)` -> `applyTransformers`; the delegation list is rebuilt at the start of each phase, and NecroTempus' config is `@env(DEFAULT)`). So ThePatcher can rewrite NecroTempus' classes before Mixin parses them, and the official NecroTempus jar keeps working:

* **Mixin** - the `pushDrawCmd` shadow and the redirect handler take an `int` texture; the handler's `texture != null` checks become `texture != 0` (`IFNULL/IFNONNULL` -> `IFEQ/IFNE`); the shadow call and the `@At(target=...)` name `pushDrawCmd(IIIZ)V`; LVT and frame entries follow.
* **Providers** - `int getTexture(char)` is **added** next to the original method (the original is kept for any other caller) and returns `NecroTempusAngelicaFontHooks.textureId(getTexture(chr))`: the GL id of the texture if `TextureManager` already has it, otherwise `-1`. The value only reaches NecroTempus' own redirect, which takes over glyph and modern-font draws; NecroTempus binds (and so loads) the texture itself when it renders. The hook never loads a texture: it only asks `TextureManager` for one it already holds (those already have a GL id).
* **Fail closed** - nothing changes unless (a) the installed Angelica has `pushDrawCmd(IIIZ)V`, no `pushDrawCmd(IILResourceLocation;Z)V`, and `FontProvider.getTexture(C)I` (read from the class files through `Launch.classLoader.getClassBytes`, nothing is loaded); (b) the NecroTempus class has the exact old shape (instance `@Shadow`, exactly one redirect of that target with the expected handler descriptor, texture parameter never reassigned); and (c) the rewritten methods pass ASM's `BasicVerifier`. A NecroTempus that already uses the int API is left alone. Client only.
* **Kill-switch:** `-Dakashic.necroTempusAngelicaFont.disable=true`.
* **Registration:** `AkashicThePatcherLoadingPlugin.getASMTransformerClass` adds it first in the client block (the class source is not available, so the method was edited at bytecode level by `tools/PatchBeta38Classes.java`).
* `NecroTempusAngelicaFontHooks` is compiled against SRG-named stubs (`func_71410_x`, `func_110434_K`, `func_110581_b`, `func_110552_b`); NecroTempus 2.1.0 calls the first three with these names, Angelica 2.2.37 the fourth.

## Fix 2 - RGB whitespace decoration profile for Angelica 2.2.37
`AngelicaDecorationPostProcessor` (RGB module) only patches the `drawString` versions it has a profile for. The pack's log showed `Angelica whitespace decoration fix skipped: unsupported drawString fingerprint e79b0611...`. The bug it fixes is still present in Angelica 2.2.37 (the whitespace branch advances `curX` and `continue`s without moving `underlineEndX`/`strikethroughEndX`). Running ThePatcher's own fingerprint code on the official `Angelica-2.2.37.jar` gives exactly `e79b0611...`, with locals `curX=11`, `underlineEndX=42`, `strikethroughEndX=45`, and a unique whitespace anchor. beta.38 adds that profile to `PROFILES` and the same layout to `hasEndpointFix(MethodNode)` (idempotency). Bytecode-level edit, same tool.

## Not changed (found during the diagnosis, recorded in OPEN-ISSUES)
`RGB bridge NOT installed: no recognized boundary in net.minecraft.client.gui.FontRenderer` is **not** caused by Angelica. ThePatcher's transformers are registered when the MixinTweaker injects the coremod (log 22:37:24), **before** `FMLDeobfTweaker` registers the deobfuscation transformer (22:37:25), so `@SortingIndex(100300)` is not honoured on this path and `RgbTextTransformer` sees `FontRenderer` with Notch member names, while it looks for MCP/SRG names. Fixing that changes text rendering in every GUI and needs its own build.

`Error loading class: com/gtnewhorizons/angelica/rendering/celeritas/AngelicaChunkTracker` (log 22:37:32) comes from ThePatcher's own `MixinAngelicaProxyVanillaChunkTracking` (beta.27, AsyncCompat). Angelica removed that class in **2.2.22** (Celeritas 2.5.12 update, PR #2151); vanilla chunk tracking now lives in `CeleritasWorldRenderer` / `CubeStatusTracker`. `mixins.akashicasynccompat.json` is `required: false`, so the mixin is skipped and nothing crashes, but the beta.27 proxy-aware chunk tracking is inactive on Angelica 2.2.22+. The other three Angelica-facing AsyncCompat mixins still resolve on 2.2.37 (checked: every `@Mixin` target, `method=` selector and `@At` target).

## Source fix for NecroTempus (for upstream)
`necrotempus-source-fix/` holds the same fix as a source patch against NecroTempus `master` (= v2.1.0 + a FUNDING commit). See its README.

## Gates (reports/final-build-audit.json) - all PASS
Run with JDK 21 (`javac --release 8`) and Zulu 8u512 (`-Xverify:all`). Inputs are the official jars (hashes in the report): Angelica 2.2.37 / 2.2.23 / 2.2.18 (GTNH Maven; 2.2.18 = `563d907f...`, the fingerprint in ARCHITECTURE), NecroTempus 2.0.0 / 2.1.0 (GitHub releases), launchwrapper 1.12, ASM 5.0.3.

* Deterministic double build, SHA-256 `bef913778167a2fb540b638bf8276981073eb39b014b30ca07df1d94bf2ff701`, 335934 B, 160 entries. Delta vs beta.37: 5 changed (loading plugin, decoration post-processor, mod class, manifest, mcmod.info), 2 added, the other 153 byte-identical; class major <= 52; new sources compile with `-Xlint:all -Werror` against the real ASM 5.0.3.
* Instruction-level diff of the two edited core classes: only `getASMTransformerClass` (plugin) and `<clinit>` + `hasEndpointFix(MethodNode)` (decoration) differ.
* Registration, real JVM verifier: client list = beta.37's 17 + `NecroTempusAngelicaFontTransformer`; server list unchanged (6).
* NecroTempus 2.0.0 and 2.1.0 x Angelica 2.2.37, bundled transformer under the real launchwrapper `LaunchClassLoader`: mixin and both providers patched; every method verifies; transform is idempotent; every `@Shadow` (2 methods + 1 field), `@Redirect` (3: getFontProvider 1 call site, pushTexRect 4, pushDrawCmd 7) and `@Inject` (3) resolves against the real `BatchingFontRenderer`, with the handler descriptors Mixin requires; the providers implement every `FontProvider` method and keep all their original methods; kill-switch honoured.
* Angelica 2.2.23 and 2.2.18, and the dedicated server: NecroTempus classes returned byte-identical.
* NecroTempus built from the source fix: left alone by the transformer, and its mixin resolves against Angelica 2.2.37.
* `textureId`: null -> 0, loaded -> its id, no GL id or not loaded -> -1.
* Decoration: beta.37 skips Angelica 2.2.37 (the pack's warning); beta.38 installs the endpoint fix there (all 70 methods verify; a second run is a no-op); on Angelica 2.2.18 beta.38 produces exactly the bytes beta.37 produces.

Not covered offline: Mixin itself applying the rewritten mixin inside a running client (the gate re-implements Mixin's resolution checks instead), and Java 8u51 specifically (no 8u51 binary was available; nothing here depends on the JIT).

## Live acceptance gate
1. Exactly one `!Akashic-ThePatcher-*.jar` in `mods/` (beta.38), Angelica 2.2.37, NecroTempus 2.0.0 or 2.1.0 (official jar). NecroTempus config unchanged (Angelica integration on).
2. The client reaches the main menu. The log shows:
   * `NecroTempus Angelica font fix armed: Angelica font API uses GL texture ids.`
   * `NecroTempus Angelica font fix applied: ...BatchingFontRendererMixin (pushDrawCmd shadow/redirect -> (IIIZ)V).`
   * `NecroTempus Angelica font fix applied: ...AngelicaGlyphProvider (getTexture(C)I added).` and the same for `AngelicaModernFontProvider`. The providers load lazily, so these two lines appear the first time NecroTempus draws a custom glyph / modern-font character.
   * `Angelica whitespace decoration endpoint fix installed: BatchingFontRenderer.drawString.`
   * no `Mixin apply for mod necrotempus failed`.
3. On the server: NecroTempus boss bars and any custom glyphs in chat/tab render; underlined/struck text keeps the line under trailing spaces.
4. Rollback: beta.37, plus `angelicaGlyphsIntegration=false` / `angelicaModernFontsIntegration=false` in NecroTempus' config (category `Angelica`) if Angelica 2.2.37 is kept.
