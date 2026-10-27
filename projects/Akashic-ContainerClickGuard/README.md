# Akashic-ContainerClickGuard

Small **server-only** coremod that stops players from **taking items out of plugin GUIs (menus)** on the pack's
Crucible server (TEOF 4.1, Forge 1.7.10 + Bukkit).

> Status: built and tested in a harness that runs the real InventoryTweaks / Avaritiaddons handlers (below).
> **Not yet live-accepted on the real server**; the gate is in [Live acceptance](#live-acceptance).

## Symptom

A plugin opens a chest-style GUI (a menu) and cancels every `InventoryClickEvent` in it so its icons stay put.
Players still get the icons into their inventory, typically by Shift/Ctrl-clicking them and closing the menu
quickly.

## Cause

Crucible only fires `InventoryClickEvent` in one place: `NetHandlerPlayServer.processClickWindow`, the handler of
the vanilla click packet (`C0EPacketClickWindow`). Its Bukkit path is correct (checked in Crucible's
`patches/net/minecraft/network/NetHandlerPlayServer.java.patch`: event, `DENY` resends the slots and returns;
drags fire `InventoryDragEvent` inside `Container.slotClick`). Anything that moves items **without** that packet
is invisible to every plugin.

**InventoryTweaks 1.59-unofficial-asie-1 is installed on the server** (`mods/InventoryTweaks-1.59-unofficial-asie-1.jar`,
SHA-256 `2bec8c68...a9abb`, same file as the client's). When it is on both sides, the client gets an `ITPacketLogin`
and switches every InvTweaks click to its own packet (`enableServerItemSwap=true` is the default and is set in the
pack's `config/InvTweaks.cfg`):

```java
// invtweaks.forge.ClientProxy (client)
public void slotClick(PlayerControllerMP pc, int windowId, int slot, int data, int action, EntityPlayer player) {
    if (this.serverSupportEnabled) {
        player.openContainer.slotClick(slot, data, action, player);            // local prediction
        invtweaksChannel.get(Side.CLIENT).writeOutbound(new ITPacketClick(slot, data, action, windowId));
    } else {
        pc.windowClick(windowId, slot, data, action, player);                  // vanilla C0EPacketClickWindow
    }
}

// invtweaks.network.handlers.ClickMessageHandler (server)
if (player.openContainer.windowId == msg.window) {
    player.openContainer.slotClick(msg.slot, msg.data, msg.action, player);    // no InventoryClickEvent
}
```

The server handler calls `Container.slotClick` directly: no `InventoryClickEvent`, so the plugin cannot cancel it,
no slot bounds check, no confirm-transaction. The item really moves on the server (it is not a ghost item: it
survives a relog).

What sends `ITPacketClick` (all from the pack's `config/InvTweaks.cfg`): sorting (`R`) and every click shortcut -
`Ctrl+Shift+click` (all items of that kind), `Ctrl+click` (one item), `Space+click` (everything), `Alt+click`
(drop), `W`/`S`/`Up`/`Down`+click (move to another section) and `1`-`9`+click. A plain vanilla Shift+click goes
through the vanilla packet and *is* cancelled - that is why it looks random to players: it works "when you
shift-click fast" because people hold Ctrl/Space/W at the same time. Closing the menu right away finishes the job
when the shortcut left the icon on the cursor: closing a container puts the cursor stack into the inventory.

### A second hole found on the way: Avaritiaddons 1.9.5-GTNH

Avaritiaddons registers its Infinity Chest messages on **both** sides:

* `InfinityChestClick` (client -> server) runs `openContainer.slotClick(...)` with only a window id check, so with a
  modified client it is the same bypass for **any** open container, plugin menus included.
* `InfinityChestSlotSync` and `InfinityChestSyncAllSlots` are server -> client messages, but their handlers also run
  on the server and **put the client-supplied ItemStacks into the open Infinity Chest**: item creation with a
  modified client. The real client never sends them (only `ContainerInfinityChest` and the click handler send them,
  with `sendTo`).

Everything else in `mods/` that touches the open container from a packet was checked and has a container-type
check (NEIAddons/Forestry `SetRecipeCommandHandler`, AE2, NotEnoughEnergistics, thaumicenergistics) or only acts on
its own containers.

## Fix

A coremod (`FMLCorePlugin`, sorting index 1001, dedicated server only) patches three classes at load time:

| Class | Change | Effect |
|---|---|---|
| `invtweaks.network.handlers.ClickMessageHandler` | its one `Container.slotClick` call -> `ClickGuardHooks.invTweaksSlotClick` | the click is handed to `processClickWindow` as a `C0EPacketClickWindow`, so it takes the vanilla path: dead-player check, `InventoryClickEvent` (cancellable), resync on cancel, Backhand's / Et Futurum's mixins. Slot numbers outside the container are dropped. InvTweaks keeps working everywhere else (sorting chests, shortcuts). |
| `wanion.avaritiaddons.network.InfinityChestClick$Handler` | `if (blockInfinityChestClick(ctx)) return null;` at the head of `onMessage` | accepted only while an Infinity Chest is open |
| `...InfinityChestSlotSync$Handler`, `...InfinityChestSyncAllSlots$Handler` | `if (blockServerBoundSync(ctx)) return null;` | dropped when they arrive on the server; untouched on the client |

The click packet is filled by reflection because the server only has `C0EPacketClickWindow`'s no-argument
constructor (the other one is client-only); field names are the 1.7.10 SRG names (`field_149554_a` ... `_149549_f`,
checked against MCP's `joined.srg` and the vanilla 1.7.10 server JAR). If they cannot be resolved the guard fails
closed: the InvTweaks click is dropped and the container resent. `clickedItem` is the slot's stack before the click,
as a vanilla client sends it; on a mismatch `processClickWindow` resends the container, and the guard keeps the
player "present" in that window so the rest of an InvTweaks sort is still accepted (what InvTweaks always relied
on). The action numbers it uses are negative, so they never meet the client's own.

Each patch checks the method it changes; another InvTweaks / Avaritiaddons version with a different shape is left
unpatched and logged as a warning.

## Install

Put `dist/Akashic-ContainerClickGuard-1.0.0-serveronly.jar` in the **server's** `mods/`. Nothing on the client.
SHA-256 `53e3b83c7c3441d8ffc078dbbe60689c95bae7dd6ffbb1562749ea51daf28806`.

**Immediate alternative without this JAR:** remove `InventoryTweaks-1.59-unofficial-asie-1.jar` from the server's
`mods/` only (its `@Mod` has `acceptableRemoteVersions="*"`, clients still connect). Clients then send every
InvTweaks click as a vanilla click, which Crucible checks. This does not close the Avaritiaddons hole.

### Logs (server log)

```
[AkashicContainerClickGuard]: armed (InventoryTweaks click routing, Avaritiaddons Infinity Chest packets)
[AkashicContainerClickGuard]: InventoryTweaks click routing applied: ITPacketClick now goes through processClickWindow (InventoryClickEvent)
[AkashicContainerClickGuard]: Avaritiaddons guard applied: wanion.avaritiaddons.network.InfinityChestClick$Handler (blockInfinityChestClick)
[AkashicContainerClickGuard]: Avaritiaddons guard applied: ...InfinityChestSlotSync$Handler (blockServerBoundSync)
[AkashicContainerClickGuard]: Avaritiaddons guard applied: ...InfinityChestSyncAllSlots$Handler (blockServerBoundSync)
```

The classes are patched when they are first loaded (InvTweaks' at start-up, Avaritiaddons' handlers at its init).
Dropped packets are logged as `WARN <player>: ...; dropped` (first 50 per run): a player who shows up there with
`InfinityChestClick while ... is open` or `server-to-client Infinity Chest sync message` is using a modified client.

### Kill-switches (JVM arguments)

* `-Dakashic.containerClickGuard.disable=true` - everything
* `-Dakashic.containerClickGuard.invTweaks.disable=true` - only the InvTweaks routing
* `-Dakashic.containerClickGuard.avaritiaddons.disable=true` - only the Avaritiaddons guard

## Verification

* **Build** (deterministic, byte-identical on rebuild; CI rebuilds it and compares):

  ```
  tools/ci/fetch_build_libs.sh <libs>
  python3 projects/Akashic-ContainerClickGuard/build.py --javac javac \
    --cp <libs>/forge-universal.jar:<libs>/launchwrapper-1.12.jar:<libs>/asm-all-5.0.3.jar:<libs>/log4j-api.jar \
    --out Akashic-ContainerClickGuard-1.0.0-serveronly.jar
  ```

  Classes are Java 8 (major 52). Minecraft classes come from `stubs/` (SRG names, compile-only, not packaged).

* **Harness** (`test/`): loads the **real** handlers from the server's InventoryTweaks and Avaritiaddons JARs,
  unpatched and patched, and runs them (JVM-verified bytecode) against test doubles of the server in
  `test/harness-stubs/`: a Crucible-like `processClickWindow` (dead check, window/presence check, a "plugin" that
  cancels clicks on the menu's slots, clickedItem mismatch) and a container with modes 0/1.

  ```
  python3 projects/Akashic-ContainerClickGuard/test/run.py --guard <jar> --libs <libs> \
    --invtweaks <server>/mods/InventoryTweaks-1.59-unofficial-asie-1.jar \
    --avaritiaddons <server>/mods/Avaritiaddons-1.9.5-GTNH.jar
  ```

  Result (2026-10-27): `HARNESS PASS`, 13 checks - unpatched InvTweaks takes the menu icon with shift (mode 1) and
  with pick-up-then-close (mode 0) and fires **0** events; patched, the same clicks fire **1** event each, the
  plugin cancels them and the icon stays; on a plain chest 4 InvTweaks clicks all go through and the player stays
  "present"; slots 9999 / -5 are dropped without an exception; a dead player's click is ignored; the three
  Avaritiaddons messages are dropped on the server, allowed with an Infinity Chest open and on the client.

  The harness does not run Crucible itself; the live gate below does.

## Live acceptance

On the real server, with a menu plugin:

1. Boot: the five log lines above are present, no `NOT patched` warning.
2. With InvTweaks on the client, open the menu and try `Ctrl+Shift+click`, `Ctrl+click`, `Space+click`, `W+click`,
   `1+click` on an icon, then close the menu at once. The icon must not stay in the inventory after a relog.
3. Ordinary chest: `R` still sorts it and the shortcuts still move items.
4. Infinity Chest (Avaritiaddons): taking and putting items still works.

Rollback: remove the JAR (and, if the menus must stay safe, remove InventoryTweaks from the server instead).
