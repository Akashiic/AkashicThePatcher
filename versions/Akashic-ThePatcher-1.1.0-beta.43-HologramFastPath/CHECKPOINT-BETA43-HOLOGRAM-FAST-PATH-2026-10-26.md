# beta.43 - HologramFastPath - 2026-10-26

`!Akashic-ThePatcher-1.1.0-beta.43-HologramFastPath.jar`, SHA-256 `38f97b919e59011c9ee4fdf0829e958c3b5a3828e68aa25177dbad5b685da59a`,
376566 B, 177 entries. Base: beta.42 (`245e67cb...`). Built and gated by `tools/beta43-build.py` (report: `reports/final-build-audit.json`).

## Problem

With HolographicDisplays 2.2.6 on the 1.7.10 server, FPS drops hard as soon as there are many holograms (spawn/hub areas).

## How a hologram exists on a 1.7 client

A 1.7 server cannot send armor stands or text displays, so HolographicDisplays 2.x (`nms/v1_7_R4`, and every 1.7 hologram plugin
that copied it) builds each line out of real mobs (`CraftTextLine.spawn`, `util/Offsets`):

| Line | Entities the client receives | Notes |
|---|---|---|
| text | `EntityHorse` with growing age **-1700000**, custom name always shown, riding an `EntityWitherSkull` spawned **54.56** blocks above the line | `EntityHorse.getHorseSize()` = 0.5 + (-24000 - age) / -24000 * 0.5 = **-34.4**: the client scales the model, width and height by it; the height (-55.1) brings the name tag (horse y + height + 0.5) back down to the line |
| item | `EntityItem` (stack size 0, anti-stack lore) riding a wither skull 0.21 below the line | |
| touch | invisible `EntitySlime` riding a wither skull | |

So for the client each text line is a mob and a projectile:

* **every tick**, each skull runs `EntityFireball.onUpdate`: a block ray trace, an entity AABB query, rotation maths and a `smoke`
  particle (300 lines keep the particle engine at its 4000 cap, above the player's head);
* each horse runs the living-entity update (`EntityAgeable` re-scales it every tick);
* **every frame**, each horse draws a 34x inverted horse model plus its name tag (two `drawString`, a background quad and ~15 GL state
  changes), and with Angelica shaders the shadow pass draws the horses again;
* the horse's bounding box is inverted (min > max) and 50 blocks wide. Angelica (Celeritas) and EntityCulling cull it, which is why the
  bundled **hologram module** (`forbiddenhologram`, since beta.3) forces every carrier visible (`ignoreFrustumCheck=true`, EntityCulling
  and Celeritas bypasses, a reflective scan of all loaded entities every 5 ticks). Without it the holograms vanish.

## Diagnosis (real client, numbers below)

`tools/holobench/` replays HolographicDisplays' exact packets (`S0E` wither skull type 66 with data 0, `S0F` horse with its data
watcher, `S1B` attach, `S0E`+`S1C` item) through the client's own `NetHandlerPlayClient`, in rows of 6-line holograms in front of the
player, and records frame times. Same headless client as beta.39/42 (Linux, Xvfb, Mesa llvmpipe, Java 8u504, Forge 10.13.4.1614,
UniMixins 0.3.1, GTNHLib 0.11.51, Angelica 2.2.37, EntityCulling 1.6.4, the pack's configs).

* beta.42: 82 FPS with no hologram, **37 FPS with 300 lines, 21 FPS with 900**.
* beta.42 with `module.hologram=false`: 82 FPS with 900 lines - **because nothing is drawn** (the holograms are invisible).
* The cost is therefore the drawing of the carriers the module keeps visible (models, name tags, shadow pass), plus the skull ticks
  and particles; the module's own reflective scan adds ~1.5 ms at 900 lines.

## Fix: the 1.21 model, on the 1.7 carriers

A 1.21 client shows a hologram as a marker armor stand / text display: an entity that does not tick physics, does not collide, and
whose text is a billboard drawn with the other text. beta.43 gives the 1.7 carriers that treatment:

| Class (client only) | What | Kill-switch |
|---|---|---|
| `module.hologram.HologramFastPathTransformer` | Head hooks (fail-closed: each method must exist once with the vanilla descriptor; names matched through FML's own notch->SRG remapper, because ThePatcher's transformers see the classes before deobfuscation): `EntityFireball/EntityHorse/EntityItem/EntitySlime.onUpdate`, `Entity.isInRangeToRender3d`, `RenderManager.doRenderEntity` (`func_147939_a`), `RendererLivingEntity.passSpecialRender`, and `RenderGlobal.renderEntities` right before its `"blockentities"` profiler section (the end of the entity loop, where Angelica flushes its entity batch). Adds `EntityHorse.akashic$hologram` (cached detection). Registered last on the client (after the hologram module's transformer). | `-Dakashic.hologramFastPath.disable=true` |
| `com.akashic.hologramfastpath.HologramFastPath` | **Detection:** horse with age <= -1000000 in a client world (HD uses -1700000, a vanilla foal at most -24000); skull carrying such a line; item/slime riding a wither skull. **Tick:** carriers keep their position (server teleports still apply) and nothing else; item lines keep spinning; no smoke, no ray traces, no AI. **Render:** horses, skulls and touch slimes leave the entity pipeline (range check false, `RenderManager` returns at once, so neither the world pass, the shader shadow pass nor EntityCulling draws them, and the second name tag EntityCulling draws for culled entities is suppressed); every visible text line is drawn in **one pass**: GL state set once, all backgrounds in one Tessellator draw, then the see-through text and the normal text of all lines, far to near. Culled by the camera frustum (a box around the line, not the inverted horse box) and by the vanilla 1.7 name tag rule (viewer within 64 blocks of the horse), so exactly the lines a 1.7 client showed are shown. Item lines keep the vanilla item renderer. Lives outside `com.akashic.thepatcher.*` on purpose: ThePatcher's `TransformerExclusions` would stop Angelica from redirecting its GL11 calls to `GLStateManager` (in Angelica's core profile a raw `glAlphaFunc` throws "Function is not supported", seen in the first real-client run). | `-Dakashic.hologramFastPath.range=<blocks>`: measure the range from the line itself (1.8+/1.21 style; more lines visible) |

`AkashicThePatcherLoadingPlugin` (binary-only core bundle, edited by `tools/PatchBeta43Classes.java`): `getASMTransformerClass` calls
`HologramFastPathTransformer.register(list)` right before its "Registering N bundled transformer(s)" log (the side check is inside
`register`, so no branch or frame is added). Server registration unchanged.

The Minecraft references of `HologramFastPath` are compiled against MCP-named shape stubs (`stubs/`) and renamed to SRG by
`tools/ReobfHooks.java` from the exact table `tools/srg-members.txt` (59 references, generated from Forge 1614's deobfuscation data +
mcp_stable-12; an unmapped or unused entry fails the build).

## Offline gates (all PASS, `reports/final-build-audit.json`)

* Deterministic double build; 4 entries changed (plugin, mod class, manifest, `mcmod.info`), 4 added, 169 byte-identical to beta.42;
  class major <= 52; only `getASMTransformerClass` differs in the plugin (MethodDiff).
* `lineage_composition`: every entry beta.38..beta.43 added or changed is in beta.43 as its newest version.
* `ReobfHooks`: the shipped hook class is the SRG rename of the MCP-compiled one, all 59 references from the table.
* Registration under `-Xverify:all`: client = beta.42's list + `HologramFastPathTransformer` last; server identical.
* `TransformGate` (`-Xverify:all`): the transformer runs on gate shapes of the eight vanilla classes (`test/mc-shape`, vanilla
  descriptors, Java 8 class files with stack map frames - the first run of this gate caught a missing frame at the hook's branch
  target, now added as `F_SAME`) inside an isolated class loader, and the result is executed with the hooks: a hologram horse and its
  skull and an item line stop ticking (position kept, item still spins, detection cached), leave the range check and `RenderManager`,
  no second name tag; the tag offset equals vanilla's float maths (line + 0.74); a normal foal, a lone skull, a dropped item and
  server-side carriers tick and render as before; `renderEntities` calls `renderLines` once and keeps its profiler sections; a second
  transformation changes nothing. Untouched with the kill-switch and on the server.
* CI (`.github/workflows/ci.yml`) rebuilds beta.43 and compares it byte for byte (`tools/ci/fetch_build_libs.sh` now also pins
  `lwjgl-2.9.1.jar`, for the GL11 calls).

## Real client (headless, `reports/real-client/`)

beta.39's headless client (Linux, Xvfb + openbox, Mesa llvmpipe GL 4.5 core, Java 8u504, 4 CPUs) with Forge 10.13.4.1614,
UniMixins 0.3.1, GTNHLib 0.11.51, Angelica 2.2.37 and EntityCulling 1.6.4 copied from TEOF-4.1-MANUAL, the pack's Angelica,
EntityCulling and ThePatcher configs (`module.hologram=true`), and `tools/holobench/` (HolographicDisplays' packets, 6-line holograms
whose first line is an item, in front of the player). Runs one after the other; mod hashes and every result in `runs.json`.

| Build | 0 lines | 300 lines (204 visible) | 900 lines (408 visible) | smoke particles at 900 |
|---|---|---|---|---|
| beta.42 | 102.2 FPS | 42.8 FPS (p99 34.4 ms) | 24.0 FPS (p99 53.0 ms) | 4000 (cap) |
| **beta.43** | 95.0 FPS | **63.8 FPS** (p99 23.4 ms) | **47.7 FPS** (p99 26.4 ms) | 0 |
| beta.42 + BSL 8.2.09 | 6.8 FPS | 5.1 FPS | - | |
| **beta.43** + BSL 8.2.09 | 7.2 FPS | **6.8 FPS** | - | 0 |

Frame time added by the holograms: 300 lines +13.6 ms -> +5.2 ms, 900 lines +31.9 ms -> +10.4 ms. llvmpipe rasterises on the CPU, so
absolute numbers are low and text fill weighs more than on a GPU; the ratios are the point. Also measured (candidate JAR with the same
classes): beta.43 with `module.hologram=false` 85.1 / 57.1 / 44.9 FPS with text and items still visible; beta.42 with
`module.hologram=false` stays at ~82 FPS only because the holograms are culled and not drawn.

Pictures: `no-shader-900-lines-beta42-vs-beta43.jpg` (same holograms, same positions, colours and backgrounds),
`bsl-300-lines-beta42-vs-beta43.jpg` (BSL: the text goes through the shader pipeline as before). All eight hooks logged
`applied`; no crash report.

Two problems the real client found that no offline gate could: ThePatcher's transformers see Minecraft classes **before** FML's
deobfuscation (notch names; the first JAR matched MCP/SRG names only and skipped every class, safely), and Angelica's core-profile GL
needs the GL11 calls redirected (the first JAR crashed with "Function is not supported" in `glAlphaFunc` - the class is now outside
the excluded package). A first version measured the 64-block range from the line itself and drew ~690 lines at 900 (9 FPS); the
default is now the 1.7 rule, so exactly the lines a 1.7 client showed are drawn.

**Full pack (TEOF-4.1, 210 mods):** with beta.43 the eight hooks were `applied` during start-up next to Hodgepodge, ArchaicFix,
EntityCulling, DynamicSurroundings and the rest (no `skipped`/`rejected`); the run did not reach the world within this environment's
memory (the container was restarted during mod loading), so the full pack is not measured here.

## Live acceptance gate (pack)

1. Install beta.43 as the only ThePatcher JAR.
2. `fml-client-latest.log`: `Hologram fast path armed`, then eight `Hologram fast path applied: ...` lines (`Entity`, `EntityItem`,
   `EntityFireball`, `EntitySlime`, `EntityHorse`, `RenderManager`, `RendererLivingEntity`, `RenderGlobal`), and after reaching the hub
   `Hologram fast path: first frame with N hologram text line(s) drawn in one pass (vanilla 1.7 name tag range)`. A
   `Hologram fast path skipped ...` line means that class kept its vanilla code (report it).
3. Visual: the hub holograms look as before (same text, colours, background, position, item icons spinning); no smoke above them.
4. FPS at the hub with F3 open, compared with beta.42 at the same spot.
5. Optional: `module.hologram=false` in `config/akashic-thepatcher.properties` turns off the old module's 5-tick reflective scan (the
   fast path no longer needs its visibility bypasses for text lines; item lines render with or without it in the headless client).
Rollback: beta.42.
