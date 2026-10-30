# beta.47 - BaublesApiAngelicaLookup - 2026-10-30

Two more players, both on the current pack (ThePatcher beta.43, Angelica 2.2.40, Baubles Expanded 2.2.26, Hodgepodge 2.7.219):

| Player | Platform | Files | Problem |
|---|---|---|---|
| `UnknownMaker` | PC (Linux, PolyMC, Java 8u502) | `crash-2026-10-30_02.06.12-client.txt` (with the full log), 2 screenshots | crash when Et Futurum Requiem's elytra goes into a bauble slot |
| (phone) | Forbidden mobile 0.31.1, Xiaomi 2410FPCC5G, Android 16 | `forbidden-diagnostico-20261030-0245.zip` | crash when opening the inventory, then the game froze and Android killed it (ANR) |

## 1. Elytra in a bauble slot: stale Baubles Expanded API copy (fixed by beta.47)

```
java.lang.NoSuchMethodError: baubles.api.expanded.IBaubleExpanded.onSlotContentsChanged(Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/EntityLivingBase;)V
    at baubles.common.container.SlotBauble.func_75218_e(SlotBauble.java:85)
    at net.minecraft.inventory.Slot.func_75215_d(Slot.java:78)
    ... Container.slotClick (click in the bauble GUI)  /  S30PacketWindowItems (server resends the inventory)
```

Both screenshots are this error: "NoSuchMethodError ... in class SlotBauble" (slot refreshed by the server's `S30PacketWindowItems`) and "ReportedException: Updating screen events" (the player's click in `GuiPlayerExpanded`, same `NoSuchMethodError` underneath). Seven occurrences in the log; CoreTweaks caught them.

### Root cause
Baubles Expanded **2.2.26** added one method to its API and calls it from its own slot (checked on the real jars from the GTNH Maven; the whole `baubles/api` package of 2.2.21, 2.2.22 and 2.2.26 differs only in this method):

```java
// baubles.api.expanded.IBaubleExpanded (2.2.26)
default void onSlotContentsChanged(ItemStack itemstack, EntityLivingBase player) {}
// baubles.common.container.SlotBauble.onSlotChanged (2.2.26)
if (item instanceof IBaubleExpanded && inventory instanceof InventoryBaubles) ((IBaubleExpanded) item).onSlotContentsChanged(stack, player);
```

`NoSuchMethodError` means the `IBaubleExpanded` the JVM actually loaded has no such method: it is an **older copy** of the API class from another jar. The log shows several jars embedding `baubles.api` (`Found API baubles.api ... embedded in libsandstone / xreliquary / runicdungeons / ThermalFoundation / OreSpawn`, plus ExtraUtilities). LaunchClassLoader takes a class from the first class-path entry that has it, and coremod/tweaker jars enter the class path first, in name order; `BaublesExpanded-2.2.26-GTNH.jar` comes after `Akashic-*`, `akashicbaubleslayout-1.0.0.jar` (a MixinTweaker jar), `angelica`, `AppleCore`, `archaicfix`, `Avaritia`, `backhand`... The log does not say which jar the copy came from; `akashicbaubleslayout-1.0.0.jar` ("Akashic Baubles Layout Sync", compiled against Baubles) is the first suspect, not verified (the jar is not available here).

Et Futurum Requiem 2.6.64's `ItemArmorElytra` (and `ItemBlockLantern`) implement `IBaubleExpanded` without overriding the method (checked on the real jar), so they are the items that reach the call; bauble items that only implement the old `IBauble` never do, which is why only the elytra seemed broken.

### Fix
`BaublesExpandedApiTransformer` (**both sides**: `SlotBauble` runs on the server too; registered after beta.46's projector guard): when the loaded `baubles.api.expanded.IBaubleExpanded` is an interface with the abstract `getBaubleTypes(ItemStack)` and **no** `onSlotContentsChanged`, it gets exactly 2.2.26's method, `public default void onSlotContentsChanged(ItemStack, EntityLivingBase) { return; }` (class version raised to 52 only if older; the class has only abstract methods). The real 2.2.26 copy is left byte-identical. The log names the culprit:

```
[Akashic-ThePatcher] WARN: Baubles Expanded API guard applied: the loaded baubles.api.expanded.IBaubleExpanded is an older copy without onSlotContentsChanged ... Jars carrying this class, the first one is in use: [<jar>, ..., BaublesExpanded-2.2.26-GTNH.jar]. Remove the embedded baubles/api/expanded classes from the jar(s) other than BaublesExpanded.
```

Kill-switch: `-Dakashic.baublesExpandedApi.disable=true`. Proper fix in the pack: the jar named first in that line must not ship `baubles/api/expanded/*` (use the API as compile-only), after which this guard does nothing.

## 2. Inventory crash with an Adventure Backpack: Angelica 2.2.40 moved the GL methods (fixed by beta.47)

`crash-2026-10-30_05.17.03` and `05.18.31` (phone), and 25 lines in the PC player's log too:

```
java.lang.ExceptionInInitializerError
    at br.com.akashic.backpackcompat.client.RenderHooks.renderPart(RenderHooks.java:19)
    at com.darkona.adventurebackpack.client.models.ModelBackpackArmor.renderBackpack(ModelBackpackArmor.java:267)
    ... RenderPlayer ... GuiInventory.func_147046_a (the player model drawn in the inventory)
Caused by: java.lang.IllegalStateException: Missing GL API glPushMatrix in class com.gtnewhorizons.angelica.glsm.GLStateManager
    at br.com.akashic.backpackcompat.client.GlApi.find(GlApi.java:31)
    at br.com.akashic.backpackcompat.client.GlApi.<clinit>(GlApi.java:12)
Caused by: java.lang.NoSuchMethodException: no such method: ...GLStateManager.glPushMatrix()void/invokeStatic
then: java.lang.NoClassDefFoundError: Could not initialize class br.com.akashic.backpackcompat.client.GlApi
```

### Root cause
Angelica **2.2.40** split `GLStateManager`: 918 public static methods in 2.2.37, 86 in 2.2.40. `glPushMatrix`, `glTranslatef`, `glRotatef`... moved to `GLSMMatrices`, `glEnable`/`glDisable` to `GLSMEnable`, and so on, all listed in Angelica's own table `com.gtnewhorizons.angelica.glsm.redirect.GLSMOwners.METHODS` (18 classes, 396 `gl*` names). Angelica rewrites calls compiled against the old `GLStateManager`, but not reflective lookups. `AkashicBackpackCompat-1.0.0-rc1` resolves its GL entry points in `GlApi`'s static initializer with `MethodHandles.Lookup.findStatic(GLStateManager, name, type)`, so with 2.2.40 `GlApi` never initialises and every frame that draws a player wearing an Adventure Backpack (inventory screen, third person, other players) throws. It affects every player of the current pack who wears a backpack, PC included.

### Fix
`AngelicaGlLookupTransformer` (client, registered last): every `MethodHandles$Lookup.findStatic(Class, String, MethodType)` call of `br.com.akashic.backpackcompat.client.GlApi` becomes `AngelicaGlLookupHooks.findStatic(lookup, owner, name, type)` (same stack shape, no branch, no frame). The hook does the original lookup first; only when the owner is Angelica's `GLStateManager` and the method is missing, it looks the name up in `GLSMOwners.METHODS` and resolves the same signature in that class (logs once). On Angelica 2.2.37 nothing changes (the original lookup succeeds). Fails closed: GlApi must have at least one such call and every edited method must pass the ASM verifier.

Kill-switch: `-Dakashic.angelicaGlLookup.disable=true`. Proper fix in AkashicBackpackCompat: resolve the GL methods through `GLSMOwners` (or call `GL11.*` directly and let Angelica redirect them) instead of `GLStateManager` by name.

The real `GlApi` class is not available; the gate uses a shape built from the crash (static initializer -> `find` -> `Lookup.findStatic` -> `IllegalStateException("Missing GL API ...")`). The bridge depends only on the `findStatic` call, which the stack trace shows (`MethodHandles$Lookup.findStatic` at `GlApi.find(GlApi.java:30)`).

## 3. The phone: why it then froze (ANR) - memory, not a separate bug

`resumo.txt`: Android ended the game with **ANR** ("Input dispatching timed out ... Waited 5000ms") after 46 min 59 s. Sequence in `latest.log` / `minecraft-game.log`:

1. 05:17 and 05:18: the inventory crash above, twice; CoreTweaks recovers and the game reloads resources (`Reloading ResourceManager` at 05:19:08 and 05:21:55).
2. Then `java.lang.OutOfMemoryError` in many threads (`AkashicFM-RelayFeed`, `DH-...` (Mirai), `Angelica-Worker-Scheduler`, `Snooper Timer`...).
3. GC: heap **2432 MiB** (launcher's automatic value), **2292 MiB still alive after a full GC**, 808 collections, 263 s in GC, longest pause **13.3 s** -> the UI thread stops answering -> ANR.

So the inventory crash is the trigger, and the heap the launcher picked automatically has almost no headroom for this pack (the launcher's own data: manual cap 3712 MiB, device cap 6016 MiB, 7.5 GB RAM). With beta.47 the trigger is gone; for that phone, setting the memory manually to ~3.5 GB in the launcher (and a lower render distance / Mirai LOD distance) is advised.

Older files in the same zip, not caused by ThePatcher and not changed by beta.47: `crash-2026-10-29_12.51.11` (`OutOfMemoryError` while starting, heap 1920 MiB), `crash-2026-10-29_02.20.34` (`IndexOutOfBoundsException: Invalid stack pointer` in `glDeleteTextures` while drawing the out-of-memory screen), `crash-2026-10-29_00.26.18` (`StackOverflowError`: Celeritas' `LWJGL3Service.glFenceSync` -> Angelica `GLStateManager.glFenceSync` -> `Lwjgl2GLRenderBackend.fenceSync` -> `LWJGL3Service.glFenceSync` ... endless loop between the mobile GL bridge and Angelica's redirect; a matter for the Forbidden mobile launcher's GL stack).

## Gates (reports/final-build-audit.json) - all PASS
* Deterministic double build, SHA-256 `e137855579a9950377ac24a538f744c6df9879a7a042e61e7f97860b60563c0f`, 392607 B, 184 entries. Delta vs beta.46: plugin, mod class, manifest, mcmod.info changed; `BaublesExpandedApiTransformer`, `AngelicaGlLookupTransformer`, `AngelicaGlLookupHooks` added; the other 177 byte-identical; class major <= 52. In the plugin only `getASMTransformerClass` differs (`MethodDiff`).
* Registration on the JVM verifier (Zulu 8u432, `-Xverify:all`): client = beta.46's 25 entries + `BaublesExpandedApiTransformer` + `AngelicaGlLookupTransformer`; server = beta.46's 6 entries + `BaublesExpandedApiTransformer`.
* `RealBaublesGate`, real `BaublesExpanded-2.2.22-GTNH.jar`: the methods are the original ones plus `public onSlotContentsChanged(ItemStack, EntityLivingBase)V { return; }`, version 52, a second pass is a no-op (client and server); with the jars on the class path the warning lists them in class-path order; real 2.2.26, beta.46 and the kill-switch: bytes unchanged.
* `BaublesGate` (`-Xverify:all`; the real `baubles/api` classes of 2.2.22 or 2.2.26 + a `SlotBauble.onSlotChanged` shape, an elytra shape and an overriding bauble compiled against 2.2.26): with the 2.2.22 API, beta.46 and the kill-switch reproduce `NoSuchMethodError ... onSlotContentsChanged`, beta.47 (client and server) passes and the overriding bauble's method is still called once; with the 2.2.26 API nothing is transformed and everything passes.
* `GlApiGate` (`-Xverify:all`; real Angelica 2.2.40 / 2.2.37 + GTNHLib 0.11.54, LWJGL 2.9.1, JOML 1.10.5, fastutil-core; GlApi shape resolving glPushMatrix, glPopMatrix, glTranslatef, glRotatef, glScalef, glEnable, glDisable, glColor4f, glBindTexture; nothing invoked): on 2.2.40, beta.46 / kill-switch / server side reproduce `Missing GL API glPushMatrix in class ...GLStateManager` and beta.47 resolves the matrix calls in `GLSMMatrices`, `glEnable` in `GLSMEnable`, none in `GLStateManager`; on 2.2.37 everything resolves in `GLStateManager` with beta.46 and beta.47.

Run locally with JDK 21 (`javac --release 8`) and the gates on Zulu 8u432; CI rebuilds beta.47 and runs the same gates. `fetch_build_libs.sh` pins BaublesExpanded 2.2.22 / 2.2.26, GTNHLib 0.11.54 and JOML 1.10.5.

Not exercised: a real client with the pack (the real `akashicbaubleslayout`/`AkashicBackpackCompat` jars are not available here), and the Forbidden mobile launcher.

## Live acceptance gate
1. Exactly one `!Akashic-ThePatcher-*.jar` in `mods/` (beta.47).
2. Baubles: the log shows `Baubles Expanded API guard applied ... Jars carrying this class, the first one is in use: [...]` (write down the first jar: it is the one to clean); putting the elytra in a bauble slot no longer crashes. If instead no such line appears and the elytra still crashes, send the log.
3. Backpack: the log shows `Angelica GL lookup bridge applied: br.com.akashic.backpackcompat.client.GlApi` and, the first time a backpack is drawn, `... glPushMatrix()void is no longer in GLStateManager ...; resolved in com.gtnewhorizons.angelica.glsm.GLSMMatrices`; opening the inventory with a backpack on no longer crashes and the backpack is drawn.
4. Rollback: beta.46 (and keep the elytra out of the bauble slots / take the backpack off).
