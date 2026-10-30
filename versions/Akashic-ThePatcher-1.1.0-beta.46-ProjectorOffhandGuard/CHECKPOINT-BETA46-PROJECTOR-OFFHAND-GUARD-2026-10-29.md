# beta.46 - ProjectorOffhandGuard - 2026-10-29

## Incident
The same player as beta.45 (`claudin_br`) opened a support ticket: crashes and disconnects. Three files: a screenshot ("Conexão Perdida - Internal Exception: io.netty.handler.codec.DecoderException: java.io.EOFException"), a crash report (`crash-2026-10-29_21.09.16-client.txt`) and a log (`latest_20.log`). They are two different problems from two different installs of the pack:

| File | ThePatcher | Hodgepodge | Angelica | Where it ends |
|---|---|---|---|---|
| `crash-2026-10-29_21.09.16-client.txt` | beta.43 | 2.7.219 | 2.2.40 | in the Thermos server (through the proxy), client crash |
| `latest_20.log` (12:00-14:37) | beta.42 (`...MiraiShaderBridge (1).jar`) | 2.7.189 | 2.2.37 | the login lobby drops the connection 4 times out of 4 |

## 1. Crash: Immersive Petroleum projector + Backhand (fixed by beta.46)

```
java.lang.ArrayIndexOutOfBoundsException: 36
    at flaxbeard.immersivepetroleum.client.projector.ProjectorClient.selectedView(ProjectorClient.java:50)
    at flaxbeard.immersivepetroleum.client.projector.ProjectorClient.overlay(ProjectorClient.java:89)
    at net.minecraftforge.client.GuiIngameForge.renderHUDText(GuiIngameForge.java:699)
    at com.gtnewhorizons.angelica.hudcaching.HUDCaching.renderCachedHud(HUDCaching.java:169)
```

### Root cause
The pack's Immersive Petroleum backport (`immersivepetroleum-0.1.0-alpha.10-feature-catalyst-refinery-20260915T1300.24+1ec598dd1c-dirty.jar`, SHA-256 `d5065d03...`, published as the pre-release `1.7.10-0.1.0-alpha.10-catalyst-wip-1ec598d` of `Akashiic/ImmersivePetroleum`) keeps one projection per hotbar slot. Its bytecode:

```
static ProjectorView[] VIEWS = new ProjectorView[9];          // <clinit>: bipush 9, anewarray
static ProjectorView selectedView() {                         // line 50
    Minecraft mc = Minecraft.getMinecraft();
    return mc.thePlayer == null ? null : VIEWS[mc.thePlayer.inventory.currentItem];
}
```

`overlay` (the `RenderGameOverlayEvent.Text` handler) calls it every frame and already handles `null`. Every other `VIEWS[...]` access in the class runs over `0..8` or `% 9`; only this one trusts `currentItem`.

Backhand 1.8.13 (in the pack) appends the offhand as one more entry of `InventoryPlayer.mainInventory` (`backhand$offhandSlot = mainInventory.length`, i.e. **36**) and points `currentItem` at it while it runs an offhand action (`BackhandUtils.useOffhandItem` / `swapToOffhand`: offhand use, offhand attack, offhand block breaking, first-person offhand render, offhand GUI clicks...). The index in the crash is exactly 36: the HUD text was drawn while `currentItem` pointed at Backhand's offhand slot, and `VIEWS[36]` threw. The crash report does not show which Backhand action left the slot there at that frame; the fix does not depend on it.

### Fix
`ProjectorSlotTransformer` (client only, registered last, after beta.45's AgriCraft irrigation guard) replaces the single `AALOAD` of `ProjectorClient.selectedView` with `ProjectorSlotHooks.view(Object[], int)` + `CHECKCAST ProjectorView`. The hook returns `VIEWS[slot]` for `0 <= slot < VIEWS.length` and `null` otherwise ("no projection held"), logging once:

```
[Akashic-ThePatcher] WARN: Immersive Petroleum projector slot guard: selected slot 36 is outside the 9 hotbar slots (Backhand's offhand slot while it runs an offhand action); no projection is shown for it instead of the ArrayIndexOutOfBoundsException crash ...
```

Same stack shape, no branch, no new stack-map frame (the existing frame at the `areturn` already says `ProjectorView`). Fails closed: `VIEWS` must be a static `ProjectorView[]`, `selectedView()` must exist once, read `VIEWS` once and do one `AALOAD` indexed by `GETFIELD InventoryPlayer.currentItem` (`field_70461_c`, or `currentItem` in a dev workspace), and the edited method must pass the ASM verifier; otherwise the class is kept and a warning names the reason. A second pass is a no-op. A dedicated server is not touched (not registered there).

Kill-switch: `-Dakashic.projectorSlotGuard.disable=true`.

Proper upstream fix (in the backport's source, which is not on GitHub): bound-check the slot in `selectedView` (`int slot = ...currentItem; return slot >= 0 && slot < VIEWS.length ? VIEWS[slot] : null;`).

## 2. Disconnect in the lobby: Hodgepodge versions do not match (not a ThePatcher bug; pack-side)

`latest_20.log`, every connection to `br2.xmxcloud.net:10511` (12:12:25, 14:35:10, 14:35:32, 14:36:41), right after `Utilize o comando /logar <senha>` and the lobby holograms spawn:

```
[Netty Client IO #1/ERROR]: NetworkDispatcher exception
io.netty.handler.codec.DecoderException: java.io.EOFException
Caused by: java.io.EOFException
    at com.mitchej123.hodgepodge.util.PooledGzipInputStream.readUByte(PooledGzipInputStream.java:82)
    at com.mitchej123.hodgepodge.util.PooledGzipInputStream.readHeader(PooledGzipInputStream.java:51)
    at com.mitchej123.hodgepodge.util.PooledGzipInputStream.readNBT(PooledGzipInputStream.java:36)
    at net.minecraft.network.PacketBuffer.func_150793_b(SourceFile:1468)
    at net.minecraft.network.play.server.S35PacketUpdateTileEntity.func_148837_a(SourceFile:41)
[Netty Client IO #1/ERROR]: NetworkDispatcher exception
io.netty.handler.codec.DecoderException: java.io.IOException: Bad packet id 91
```

### Root cause
Hodgepodge **2.7.194** changed the wire format of `S35PacketUpdateTileEntity` (mixin `MixinS35PacketUpdateTileEntity_ByteArray`, config `directTileEntityArraySerialization`, default `true`, on both sides): the NBT field now starts with one extra byte, `0` followed by the normal NBT, or a one-character key followed by a raw byte array. 2.7.193 and older (the log's 2.7.189) read the normal format. Checked by downloading every Hodgepodge 2.7.189-2.7.219 from the GTNH Maven: the mixin first appears in 2.7.194.

The bytes in the log match exactly: the server wrote `00 | 00 5B | <91 bytes of gzip>`; the old client read the NBT length as the short `00 00` = 0, Hodgepodge's gzip reader got 0 bytes and threw `EOFException` while reading the gzip magic, and Netty kept the rest of the frame; when the channel closed it decoded that leftover, whose first byte is `0x5B` - the low byte of the real length - as a packet id: **`Bad packet id 91`**. So whatever sends that tile entity update runs Hodgepodge >= 2.7.194 with the default config, and the player's install has 2.7.189. (Without Hodgepodge the vanilla reader fails the same way, on the same zero-length gzip.)

The crash report's install (Hodgepodge 2.7.219, beta.43) gets past the lobby, as did the two logs of beta.45's report (beta.43): that install is up to date; `latest_20` came from an old one.

### What to do
* The player: use one up-to-date install of the pack (Hodgepodge 2.7.219, ThePatcher beta.46) and delete/stop using the old one (it is still on beta.42, Angelica 2.2.37, Hodgepodge 2.7.189, Baubles 2.2.22, GTNHLib 0.11.51). Updating ThePatcher alone does not fix this one.
* Pack/server: every client and server that exchange `S35` must be on the same side of Hodgepodge 2.7.194 (or all set `directTileEntityArraySerialization=false` in `config/hodgepodge` - it is `@RequiresMcRestart` and must match on both ends).

Not patched in ThePatcher on purpose: a client-side reader for the new format would have to guess the format from the bytes, and the fix (matching Hodgepodge) is already in the current pack.

## Other errors in `latest_20.log` (harmless, not the cause)
* 84 `NullPointerException` at `com.Zoko061602.SuperTic.TooltipEventHandler.addPartTooltips` and 1 at `ItemDataCrystal.getDurabilityForDisplay` (ChromatiCraft): NEI building its search/tooltip cache on a `ForkJoinPool` worker; NEI catches them and moves on.
* `Failed to load 'untranslator.cfg'`, `Trait already registered ...`, `FML appears to be missing any signature data`: start-up noise present in every log of the pack.
* E.27 of OPEN-ISSUES (the `NullPointerException` handshake screen) is not in this log either.

## Gates (reports/final-build-audit.json) - all PASS
* Deterministic double build, SHA-256 `e813946b3b4ead550dc38ae147ae60a7454ddaa338e81fee332e6a74ad7306d5`, 384631 B, 181 entries. Delta vs beta.45: plugin, mod class, manifest, mcmod.info changed; `ProjectorSlotTransformer` and `ProjectorSlotHooks` added; the other 175 byte-identical; class major <= 52. In the plugin only `getASMTransformerClass` differs (`MethodDiff`).
* Registration on the JVM verifier (Zulu 8u432, `-Xverify:all`): client list = beta.45's 24 entries + `ProjectorSlotTransformer` last; server list unchanged.
* `RealProjectorGate`, the real `ProjectorClient` of the pack's Immersive Petroleum (SHA-256 `d5065d03...`): only `selectedView()` changes, its one `AALOAD` replaced by `ProjectorSlotHooks.view` + `CHECKCAST ProjectorView`, fed by `GETFIELD field_70461_c` and followed by `ARETURN`; every method passes the ASM verifier; a second pass returns the same bytes. beta.45, the kill-switch and the server side leave the class unchanged.
* `ProjectorGate` (shape of `ProjectorClient` that javac compiles to the same instructions and frames, `-Xverify:all`): slots 0..8 return their own projection and no player returns `null` with every jar; slots 36 / -1 / 9 return `null` with beta.46 (one warning logged) and throw `ArrayIndexOutOfBoundsException` with beta.45 / kill-switch / server - the original crash.

Run locally with JDK 21 (`javac --release 8`) and the gates on Zulu 8u432; the local toolchain first rebuilt beta.45 byte for byte. CI (`ci.yml`) rebuilds beta.46 and runs the same gates; `fetch_build_libs.sh` pins the Immersive Petroleum jar.

Not exercised: a real client (Backhand + Immersive Petroleum + Angelica HUD caching) holding the projector while using the offhand.

## Live acceptance gate
1. Exactly one `!Akashic-ThePatcher-*.jar` in `mods/` (beta.46), in the up-to-date install (Hodgepodge 2.7.219).
2. The log shows `Immersive Petroleum projector slot guard applied: flaxbeard.immersivepetroleum.client.projector.ProjectorClient` when the projector client class loads.
3. Holding Immersive Petroleum's projector in the hotbar and using the offhand (Backhand) no longer crashes; at most one `... selected slot 36 is outside the 9 hotbar slots ...` warning per session.
4. Joining the server: the lobby no longer drops with `DecoderException: java.io.EOFException` (that part depends on the install's Hodgepodge, see section 2).
5. Rollback: beta.45 (and do not hold the projector while using the offhand).
