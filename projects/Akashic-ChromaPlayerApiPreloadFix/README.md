# Akashic-ChromaPlayerApiPreloadFix

Small client/server coremod that makes the **Et Futurum Requiem elytra take off again** in the TEOF 4.0 pack.
It is a separate JAR (like the other `Akashic-*-Fix` mods in the pack), not part of ThePatcher.

## Symptom

With an elytra equipped, jumping in mid-air does nothing: the wings never open. `logs/fml-client-latest.log` shows

```
[mixin]: Error loading class: net/minecraft/client/entity/EntityPlayerSP (Reika.DragonAPI.Exception.ASMException$NoSuchASMMethodException: Error ASMing net/minecraft/client/entity/EntityPlayerSP:
Method localPushOutOfBlocks (DDD)Z not found in class net/minecraft/client/entity/EntityPlayerSP.
[mixin]: @Mixin target net.minecraft.client.entity.EntityPlayerSP was not found mixins.etfuturum.early.json:backlytra.client.MixinEntityPlayerSP from mod etfuturum
[mixin]: @Mixin target net.minecraft.client.entity.EntityPlayerSP was not found coretweaks.mixin.json:tweak.extendsprint.MixinEntityPlayerSP from mod coretweaks
```

## Cause

1. Before applying mixins, Mixin pre-reads each target class through the transformer chain. For
   `EntityPlayerSP` that pre-read runs ChromatiCraft's transformer (`ChromaASMHandler$ASMExecutor`) but not
   PlayerAPI's.
2. ChromatiCraft's `ENTITYPUSHOUT` patch looks for PlayerAPI's renamed method `localPushOutOfBlocks` whenever
   PlayerAPI is installed. In the pre-read bytes it does not exist yet, so ChromatiCraft throws.
3. Mixin treats the class as missing and drops every `EntityPlayerSP` mixin. One of them is Et Futurum's
   `backlytra.client.MixinEntityPlayerSP`, which sends `StartElytraFlyingMessage` when you press jump in
   mid-air. Without it the server is never asked to open the elytra. CoreTweaks' `extendsprint` is lost the
   same way.
4. The real class load later succeeds (PlayerAPI runs first there, and ChromatiCraft logs
   `Successfully applied ENTITYPUSHOUT`), so the game starts normally and nothing looks broken.

## Fix

At `injectData` (sorting index 1002, right after ChromatiCraft's 1001 and before Mixin's DEFAULT phase), two
transformers are inserted around ChromatiCraft's, which stays registered unchanged (DragonAPI refuses to start
if it is missing):

* `Hide` (before ChromatiCraft) acts only on `EntityPlayerSP` bytes **without** `localPushOutOfBlocks` while
  PlayerAPI is installed. It takes the `EntityPlayerSP` entry out of ChromatiCraft's pending-patch map, so
  ChromatiCraft does nothing and does not throw.
* `Restore` (after ChromatiCraft) puts the entry back, so the real class load still gets `ENTITYPUSHOUT`,
  exactly as it does today.

Nothing else changes: every other class goes through ChromatiCraft exactly as before. Both transformers are
nested classes on purpose (see the comment in the source): Mixingasm's exclusion list uses canonical names, so
nested transformers keep running during Mixin's pre-reads, like ChromatiCraft's own. A first top-level version
was really excluded and moved ChromatiCraft's other patches (consumed today by those pre-reads) onto the real
classes, where `COLLISIONBOXES` broke Hodgepodge's `MixinWorld_FastItemPhysics` at start-up.

Log lines: `armed around Reika.ChromatiCraft.Auxiliary.ChromaASMHandler$ASMExecutor (index N)` and
`held back ChromatiCraft ENTITYPUSHOUT from EntityPlayerSP bytes without PlayerAPI's localPushOutOfBlocks ...`.
The two `@Mixin target net.minecraft.client.entity.EntityPlayerSP was not found` warnings are gone.

Kill-switch: `-Dakashic.chromaPlayerApiPreload.disable=true`. Without ChromatiCraft it logs
`ChromatiCraft transformer not registered; nothing to do`.

## Install

Put `dist/Akashic-ChromaPlayerApiPreloadFix-1.0.0.jar` (SHA-256 in `dist/SHA256SUMS.txt`) in `mods/` (client). The elytra take-off is client-side
logic. Installing it on the server too does no harm.

## Build

```
python3 build.py --javac <javac> --cp <forge-universal.jar>:<launchwrapper-1.12.jar>:<asm-all-5.0.3.jar>:<log4j-api-2.0-beta9.jar> --out Akashic-ChromaPlayerApiPreloadFix-1.0.0.jar
```

The build is deterministic (fixed entry order and timestamps) and targets Java 8 (class major 52).
`versions/*beta.39*/tools/headless-client/setup.py` downloads all four compile-time inputs.

## How it was verified

The full TEOF-4.0-MANUAL pack (212 mods; the pack's `bin/minecraft.jar` and `modpack.jar` are byte-identical to
vanilla 1.7.10 and Forge 10.13.4.1614) was booted in the beta.39 headless client with
`tools/elytra-probe/ElytraTest.java`. The probe creates a flat survival world, equips `etfuturum:elytra` on the
server, teleports the player to y=150, presses jump in mid-air and logs client and server state every two ticks.
Two test-only changes were made to that instance: `Spawn with CC Lexicon=false` and the "Waystones Modernity
Textures" resource pack disabled. With either of them, rendering a Reika item in the hotbar crashes in
`ReikaImageLoader.getImageFromResourcePack` (`WaystonesAlternateResourcePack cannot be cast to
AbstractResourcePack`), which is unrelated to this fix.

Results (2026-10-26, fresh copies of the pack, Java 8u504, software GL):

| Run | Fix | Mixin warnings | `EntityPlayerSP` mixin members | Jump in mid-air with elytra |
|---|---|---|---|---|
| fresh, no fix | no | both `EntityPlayerSP was not found` | none | nothing happens, player falls (t=121, t=161) |
| fresh, with fix | yes | none | `etfu$lastIsJumping`, `etfu$lastIsFlying`, `handler$...$startElytraFlying` | elytra opens 4 ticks later; glide at motionY -0.168, horizontal speed up to 1.5, durability used |
| same instance, 2nd launch (warm CoreTweaks cache) | yes | none | same | elytra opens 4 ticks later, same glide |

A start-up crash check is part of the result: the first top-level wrapper version failed with
`Critical injection failure: noEntityCollisionForItems` and a later version that replaced ChromatiCraft's
transformer failed with DragonAPI's `MissingASMException`. The shipped version starts cleanly.
