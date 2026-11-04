# beta.51 - GeckoAnimationCopy - 2026-11-04

`!Akashic-ThePatcher-1.1.0-beta.51-GeckoAnimationCopy.jar`, SHA-256 `5e2fce966fa780f54a5405980636cb35027ea9c7b1f536482e65441d85e871f1`,
412200 B, 191 entries. Base: beta.50 (`2b746b0f...`). Built and gated by `tools/beta51-build.py` (report: `reports/final-build-audit.json`).

## Problem

Frame spikes with GeckoLib models whose looping animations are long. Reported with the AkashicFishing 3D fishing rods
(drawn by Kunftig through GeckoLib Unofficial 1.7.10 1.0.4): the most animated rod ("Akáshica Irídio Ascensão", an aura
loop of 2,952 keyframes) stutters every few seconds and the FPS drops.

Cause, read in GeckoLib 1.0.4's bytecode and measured in the client:

* `AnimationController.setAnimation` takes a copy of every `Animation` it starts, so the copy can get its own loop
  type: `Animation.copy(Animation)`.
* `copy` is a **deep copy by serialization**: `ObjectOutputStream.writeObject` of the whole animation (every bone, every
  keyframe, every `Double`), the bytes turned into a Base64 string, decoded and read back with
  `ObjectInputStream.readObject`.
* The port restarts a looping animation through `setAnimation` at the end of every loop (the `///ADDED` block in
  `AnimationController`), so a model pays that copy **at every turn of every looping animation**, on the client thread.

| Animation (AkashicFishing rods) | Keyframes | Copy (TEOF 4.8 client) | Allocated per copy | How often |
|---|---:|---:|---:|---|
| aura, "Ascensão" (m6) | 2,952 | 74-118 ms (81 ms median) | 17-25 MB | every 4 s |
| fight loop (`briga`) | 239 | 36 ms | not measured | every 2 s |

The 74-118 ms is a missed frame or more every loop, plus the garbage it leaves; every rod pays it, the Ascensão most.

## Fix

`GeckoAnimationCopyTransformer` (both sides; the class also loads on the server; registered last, after beta.50's
`RecipeIndexTransformer`):

* Target: `software.bernie.geckolib3.core.builder.Animation`.
* `copy`'s body becomes a field copy: `null` stays `null`, otherwise a new `Animation` (its public no-argument
  constructor) with every instance field of the original assigned (7 fields in 1.0.4).
* The copy keeps its own fields, so the loop type `setAnimation` sets on it does not touch the original. It shares the
  bone/keyframe lists, which GeckoLib only reads at run time; `AnimationController.process` already swaps the copy for
  the model's shared cached `Animation` on the next frame, so sharing is what happens anyway one frame later.
* Kill-switch: `-Dakashic.geckoAnimationCopy.disable=true`.

Fails closed: `copy` must exist once with the exact descriptor `(LAnimation;)LAnimation;`, be static and call both
`ObjectOutputStream.writeObject` and `ObjectInputStream.readObject` (the serialization copy); the class must have a
public no-argument constructor and instance fields; the new body must pass the ASM `BasicVerifier`. Otherwise the class
is kept as it is and the log says why (`GeckoLib animation copy fix skipped for ...`).

## Offline gates (all PASS, `reports/final-build-audit.json`)

* Deterministic double build. 4 entries changed (plugin, mod class, manifest, `mcmod.info`), 1 added
  (`GeckoAnimationCopyTransformer`), 186 byte-identical to beta.50. Class major <= 52.
* Plugin: only `getASMTransformerClass` differs.
* Registration on the JVM verifier: client and server = beta.50's list + `GeckoAnimationCopyTransformer` last.
* `GeckoAnimationCopyGate` (`-Xverify:all`, through the shipped transformer as `LaunchClassLoader` would): a shape of
  GeckoLib 1.0.4's `Animation`/`ILoopType`/`BoneAnimation` (same fields, same serialization `copy`), an animation of
  3,000 keyframes.
  * beta.51: transformed, same values in every field, the copy's loop type is its own, `copy(null)` is `null`, lists
    shared; **copy 48,973 µs -> 18 µs**.
  * beta.50 and beta.51 with the kill-switch: untransformed, new lists (serialization copy).
  * Another shape (a `copy` that is not the serialization copy): skipped with the warning, untouched.

## Real client (TEOF 4.8, headless, Xvfb + Mesa)

AkashicFishing 1.4.0 rods + Kunftig (3D rods) + GeckoLib Unofficial 1.0.4, harness measuring `Animation.copy` on the
real cached animations of the rods:

* `latest.log`: `GeckoLib animation copy fix applied: software.bernie.geckolib3.core.builder.Animation (field copy of 7 field(s)).`
* Aura of the Ascensão: **81 ms and 18 MB per copy -> 0.00 ms and 0.00 MB** (beta.50 -> beta.51, same instance).

Not fixed here: the first time a rod with the m6 aura is shown, its 8 animated texture frames are uploaded (1.7.10's
`TextureUtil` allocates 16 MB per upload). That is a one-time hitch, not a periodic one.

## Acceptance gate in the pack

`logs/fml-client-latest.log` / `latest.log`:
* `GeckoLib animation copy fix applied: software.bernie.geckolib3.core.builder.Animation (field copy of 7 field(s)).`
* never `GeckoLib animation copy fix skipped` / `rejected`.

In game: hold the Akáshica Irídio Ascensão (or any animated rod) for a minute; the spike every ~4 s is gone. Rollback:
beta.50, or `-Dakashic.geckoAnimationCopy.disable=true`.
