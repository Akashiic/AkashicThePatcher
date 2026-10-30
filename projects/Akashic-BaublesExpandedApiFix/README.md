# Akashic-BaublesExpandedApiFix

Small coremod that stops the **server from crashing when an Et Futurum Requiem elytra is put into a Baubles slot**.
It is meant for the server. Installing it on the client as well is safe but not needed: it has no mod container, so
the FML handshake never sees it.

## Symptom

`crash-reports/crash-*-server.txt`, `Exception in server tick loop`:

```
java.lang.NoSuchMethodError: 'void baubles.api.expanded.IBaubleExpanded.onSlotContentsChanged(net.minecraft.item.ItemStack, net.minecraft.entity.EntityLivingBase)'
	at baubles.common.container.SlotBauble.func_75218_e(SlotBauble.java:85)
	at net.minecraft.inventory.Slot.func_75215_d(Slot.java:78)
	at net.minecraft.inventory.Container.func_75144_a(...)
	at net.minecraft.network.NetHandlerPlayServer.func_147351_a(...)    <- C0EPacketClickWindow
```

Any player who clicks an elytra into the Baubles GUI takes the whole server down.

## Cause (checked against the published jars)

1. `BaublesExpanded-2.2.26-GTNH` declares `IBaubleExpanded.onSlotContentsChanged(ItemStack, EntityLivingBase)` as an
   **empty default method**. `SlotBauble.onSlotChanged()` calls it through `invokeinterface` every time an
   `IBaubleExpanded` item lands in a slot.
2. `etfuturum-2.6.64-GTNH`'s `ItemArmorElytra implements IBaubleExpanded` and does not override it (it relies on the
   default). Et Futurum ships **no** copy of the Baubles API.
3. The error is `NoSuchMethodError`, not `AbstractMethodError`. The JVM did not fail to find an implementation. It
   failed to find the method on the **interface itself**. So the `IBaubleExpanded` class the server loaded is an
   **older copy, bundled in another mod's jar**, from before that method existed. Mods in this pack already do this
   kind of bundling: the crash report lists `Baubles|API (1.0.1.4) from Reliquary-...-Forbidden...jar` and
   `ExtraUtilities containing declared API package baubles.api`.

The client-side fix in ThePatcher does not run on the server, so the server crash remains.

## Fix

`InterfaceRepairTransformer` handles exactly one class, `baubles.api.expanded.IBaubleExpanded`:

* the loaded copy **has** the method (the correct 2.2.26 copy wins): bytes are returned **unchanged** (same array);
* the loaded copy **lacks** it: adds `public default void onSlotContentsChanged(ItemStack, EntityLivingBase) {}`.
  That is the same empty body that 2.2.26 ships. A pre-Java-8 copy is raised to class version 52, which default
  methods need. This is only done when the copy has no method bodies, so no stack map frames are needed;
* any other class: untouched.

Result: the behaviour is identical to a pack without the stale copy. Items that override the method keep their
override. Items like the elytra get the same no-op the real API gives them. No Baubles, Et Futurum or SlotBauble
code is changed, and nothing is caught or swallowed.

The log line also lists **every jar that carries the interface** (the first one wins), so you can find the culprit:

```
[AkashicBaublesExpandedApiFix]: loaded copy of baubles.api.expanded.IBaubleExpanded lacks onSlotContentsChanged; added the empty default from BaublesExpanded 2.2.26 (copies on the class path, first one wins: [jar:file:.../mods/<CULPRIT>.jar!/..., jar:file:.../mods/BaublesExpanded-2.2.26-GTNH.jar!/...])
```

You can also find it without starting the server:

```bash
for j in mods/*.jar; do unzip -l "$j" 2>/dev/null | grep -q 'baubles/api/expanded/IBaubleExpanded.class' && echo "$j"; done
```

Kill switch: `-Dakashic.baublesExpandedApiFix.disable=true`.

## Verification

`tools/repair-test/RepairTest.java` runs everything in an isolated class loader with full JVM verification
(Java 21):

```
PASS stale copy reproduces the crash -> NoSuchMethodError
PASS repaired copy: elytra slot change -> ok
PASS repaired copy: override still runs -> override
PASS real 2.2.26 interface untouched -> same
PASS other classes untouched -> same
ALL PASS
```

The real interface used in the test is `baubles/api/expanded/IBaubleExpanded.class` from
`Baubles-Expanded-2.2.26-GTNH.jar` (GTNH maven).

## Build

```bash
python3 build.py --javac javac --cp forge-1.7.10-10.13.4.1614-1.7.10-universal.jar:launchwrapper-1.12.jar:asm-all-5.0.3.jar:log4j-api-2.0-beta9.jar --out dist/Akashic-BaublesExpandedApiFix-1.0.0.jar
```

```bash
java -cp dist/Akashic-BaublesExpandedApiFix-1.0.0.jar:asm-all-5.0.3.jar:launchwrapper-1.12.jar:log4j-api-2.0-beta9.jar:log4j-core-2.0-beta9.jar tools/repair-test/RepairTest.java <path to IBaubleExpanded.class from BaublesExpanded 2.2.26>
```

## Install

Put `dist/Akashic-BaublesExpandedApiFix-1.0.0.jar` in the server's `mods/` folder and restart.
