# beta.45 - AgriCraftIrrigationGuard - 2026-10-29

## Incident
A player cannot join the pack server. The lobby works (vanilla/Bukkit side of the proxy), then the switch to the Forge backend ends with **Connection Lost - Fatally missing blocks and items**, every time (three attempts in one session). The player's `latest.log` (beta.43, Angelica 2.2.40, `AgriCraft-1.5.2-evernife-v1.jar`):

```
[20:22:04] [Netty Client IO #0/INFO]: Detected handshake reset packet, enforcing HELLO state...
[20:22:05] [Netty Client IO #0/INFO]: Injecting existing block and item data into this client instance
[20:22:05] [Netty Client IO #0/INFO]: Found a missing id from the world AgriCraft:waterChannelFull
[20:22:05] [Netty Client IO #0/INFO]: Found a missing id from the world AgriCraft:waterChannel
[20:22:05] [Netty Client IO #0/INFO]: Found a missing id from the world AgriCraft:channelValve
[20:22:05] [Netty Client IO #0/INFO]: Found a missing id from the world AgriCraft:waterTank
[20:22:05] [Netty Client IO #0/INFO]: Found a missing id from the world AgriCraft:sprinkler
[20:22:05] [Netty Client IO #0/ERROR]: Failed to connect to server: there are 10 missing blocks and items
```

## Root cause
The client never registered AgriCraft's irrigation blocks. During pre-init the same log registers `agricraft:crops`, `seedAnalyzer`, `waterPad`, `waterPadFull`, `seedStorage`, `peripheral`, `fence`, `fenceGate`, `grate` - and none of `waterTank`, `waterChannel`, `waterChannelFull`, `channelValve`, `sprinkler`. In AgriCraft 1.7.10 those five are exactly the ones behind one switch:

```java
// ConfigurationHandler.loadConfiguration
disableIrrigation = config.getBoolean("Disable Irrigation", "irrigation", false, "set to true if you want to disable irrigation systems");
// Blocks.init
if (!ConfigurationHandler.disableIrrigation) { blockWaterTank = ...; blockWaterChannel = ...; blockWaterChannelFull = ...; blockChannelValve = ...; blockSprinkler = ...; }
```

So that player's `config/agricraft/Configuration.cfg` has `B:"Disable Irrigation"=true`. The server registers the blocks (5 blocks + 5 ItemBlocks = the "10 missing"), and FML refuses a client that lacks ids the server has. Other players with the default `false` connect fine, which is why only one person sees it.

## Fix
`AgriCraftIrrigationTransformer` (client only, registered last, after beta.43's hologram fast path): the single `PUTSTATIC disableIrrigation` of `com.InfinityRaider.AgriCraft.handler.ConfigurationHandler` gets `AgriCraftIrrigationHooks.clientIrrigationDisabled(Z)Z` in front of it, which stores `false` and logs once when the file said `true`:

```
[Akashic-ThePatcher] WARN: AgriCraft irrigation guard: config/agricraft/Configuration.cfg has "Disable Irrigation"=true; ignored on the client, ...
```

The client therefore always registers the irrigation blocks. That matches a server either way: FML 1.7.10 only refuses ids the *client* is missing; extra client blocks are ignored. The config file is not rewritten. A dedicated server is not touched (the transformer is not even registered there). The edit is one instruction, no branch, no new stack-map frame; it fails closed (exactly one write of a static boolean `disableIrrigation`, else the class is left alone and a warning names the reason) and a second pass is a no-op.

Kill-switch: `-Dakashic.agricraftIrrigation.disable=true` (keeps the value from the config file).

Immediate workaround without updating the JAR: set `B:"Disable Irrigation"=false` in `config/agricraft/Configuration.cfg` (or delete the file so AgriCraft recreates it).

## Gates (reports/final-build-audit.json) - all PASS
* Deterministic double build, SHA-256 `d2b5e3076881cba1c8f5d1179c75b126574a16c269dce17768bc5104a93fe035`, 380277 B, 179 entries. Delta vs beta.44: plugin, mod class, manifest, mcmod.info changed; `AgriCraftIrrigationTransformer` and `AgriCraftIrrigationHooks` added; the other 173 byte-identical; class major <= 52. In the plugin only `getASMTransformerClass` differs (`MethodDiff`).
* Registration on the JVM verifier (Zulu 8u432, `-Xverify:all`): client list = beta.44's 23 entries + `AgriCraftIrrigationTransformer` last; server list unchanged.
* `RealAgriCraftGate`, real `AgriCraft-1.7.10-1.5.0.jar` (CurseForge file 2284130, SHA-256 `b1d847e0...`): only `loadConfiguration()V` changes, by exactly one instruction, `Configuration.getBoolean -> clientIrrigationDisabled -> PUTSTATIC disableIrrigation`; every method passes the ASM verifier; a second pass returns the same bytes. beta.44, the kill-switch and the server side leave the class unchanged.
* `IrrigationGate` (shape of `ConfigurationHandler`, `-Xverify:all`, config load + config-changed reload): config `true` -> stored `false` with beta.45, warning logged once; config `false` -> `false`, no warning; beta.44 / kill-switch / server -> stored `true`; the neighbouring fields (`disableSeedWarehouse`, `channelCapacity`) keep their values.

Run locally with JDK 21 (`javac --release 8`) and the gates on Zulu 8u432. CI (`ci.yml`) rebuilds beta.45 and runs the same gates.

Not exercised: the pack's own `AgriCraft-1.5.2-evernife-v1.jar` (not downloadable); its block registration in the player's log is identical to upstream 1.5.0's, and the transformer refuses to edit anything that does not have the one-write shape. Not exercised either: a real client joining the server.

## Not explained by this log
The first screenshot sent by the same player ("A fatal error has occurred during the network handshake ... java.lang.NullPointerException") is not in either log file (`latest_18` never connects; `latest_19` only shows the missing-blocks refusal). It happens in the same handshake step, so a registry mismatch like this one is the likely cause, but that needs the log of a session that ends with that message.

## Live acceptance gate
1. Exactly one `!Akashic-ThePatcher-*.jar` in `mods/` (beta.45).
2. The log shows `AgriCraft irrigation guard applied: com.InfinityRaider.AgriCraft.handler.ConfigurationHandler`, and during pre-init `registering tile.agricraft:waterTank` (and the other four irrigation blocks).
3. With `"Disable Irrigation"=true` in the client config, joining the server passes the Forge handshake (no `Found a missing id from the world AgriCraft:...`, no "Fatally missing blocks and items"); the log has the one WARN above.
4. Rollback: beta.44 (and set `"Disable Irrigation"=false` by hand).
