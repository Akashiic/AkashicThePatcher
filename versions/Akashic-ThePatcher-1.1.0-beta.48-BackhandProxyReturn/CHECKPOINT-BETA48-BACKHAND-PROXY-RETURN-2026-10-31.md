# beta.48 - BackhandProxyReturn - 2026-10-31

`!Akashic-ThePatcher-1.1.0-beta.48-BackhandProxyReturn.jar`, SHA-256 `b750f8109dff722e2f91195573d5c80668555553645ee621fed0e95c6f4253e5`,
392702 B, 184 entries. Base: beta.47 (`e1378555...`). Built and gated by `tools/beta48-build.py` (report: `reports/final-build-audit.json`).

## Problem

Player report (2026-10-31, `latest.log`, client on beta.43; the Backhand class is byte-identical from beta.35 to beta.47, so beta.47 has the same defect): Velocity proxy, Forge 1.7.10 backend, vanilla lobby (Paper 1.21.1 + ViaVersion).
Backend -> lobby -> back to the backend on the same connection; after the return the screen stayed black. The black screen
itself is **not explained** by this build (see "What this does not fix"). Reading the log for it showed a definite defect in the
bundled Backhand module (`forbiddenbackhand`, `BackhandGuardHooks`, unchanged since beta.35): after the return the offhand is
blocked for the rest of the session.

```
00:29:43 join game/backend switch: type=TRANSITION ... offhandAllowed=false, proof=5/5, joins=11      (to the lobby)
00:33:13 remote FML mod list captured: type=MODDED ... offhandAllowed=true, proof=6/6                (backend handshake)
00:33:13 connection established: type=MODDED ... offhandAllowed=true, proof=6/6
00:33:13 join game/backend switch: type=TRANSITION ... offhandAllowed=false, proof=6/6, joins=12      (backend JoinGame)
```

On every fresh connection in the same log the last line is instead `join game consumed fresh FML capability proof: type=MODDED`.

## Root cause (bytecode of `BackhandGuardHooks`, SHA-256 `af5980de...` in every build from beta.35 to beta.47)

* `onJoinGamePacket` accepts the backend only with a fresh proof: `remoteModListSeen && proofSerial > consumedProofSerial`.
  Otherwise, unless the type is VANILLA/BUKKIT, it calls `beginTransitionLocked` (TRANSITION, capability cleared).
* `captureRemoteModMap` increments `proofSerial`, and when the state is `TRANSITION` with `awaitingJoinGame=false` (the state
  after the lobby JoinGame) it promotes to MODDED **and sets `consumedProofSerial = proofSerial`**.
* So on the return the mod list spends its own proof, and the backend's JoinGame that follows (Velocity forwards it after
  the FML handshake) finds nothing fresh and drops back to TRANSITION.
* `awaitingJoinGame` would have avoided the promotion, but it is only set by the HandshakeReset hook in
  `FMLHandshakeClientState`, which did not fire in this log (`FML handshake reset` appears 0 times, while FML logged
  `Detected handshake reset packet, enforcing HELLO state...`). The log starts mid-session, so whether that hook was
  installed is not visible; the fix does not depend on it.

## Fix

`tools/PatchBeta48Backhand.java` (ASM, fail-closed on the exact class above, SHA-256 `af5980de...`):

| Change | What |
|---|---|
| `+ private static synthetic long akashic$proofJoinSerial` | `joinSerial` at the moment the last mod list was captured |
| `captureRemoteModMap` | after `proofSerial++`: `akashic$proofJoinSerial = joinSerial` |
| `onJoinGamePacket` | the inline freshness test is replaced by `akashic$freshProofLocked()` |
| `+ akashic$freshProofLocked()` | the beta.47 test, **or** (`connectionType == MODDED` and `akashic$proofJoinSerial == joinSerial - 1`: the mod list was captured after the previous JoinGame, so it belongs to this one) |

Kill-switch `-Dakashic.backhandProxyReturn.disable=true`: `akashic$freshProofLocked()` is exactly the beta.47 test.
A JoinGame without a new mod list still never reuses a proof (the condition needs a capture since the previous JoinGame).
Assumption: a mod list that arrives after a JoinGame belongs to the next JoinGame. That is the Forge order (the FML handshake
completes before the play state) and the order Velocity uses when it switches to a Forge backend; beta.35's own promotion branch
already made the same assumption.

## Offline gates (all PASS, `reports/final-build-audit.json`)

* Deterministic double build; 4 entries changed (`BackhandGuardHooks`, mod class, manifest, `mcmod.info`), none added or
  removed, 180 byte-identical to beta.47; class major <= 52.
* `HooksDiff`: one field added; `captureRemoteModMap` and `onJoinGamePacket` changed; `akashic$freshProofLocked` added; the other
  31 methods identical.
* `BackhandGate` under `-Xverify:all`: the shipped `BackhandGuardHooks`, `PatchConfig`, `PatchLog` and `Reflect` in an isolated
  class loader, driven through the log's event order (fresh connect via a vanilla first server, backend, lobby, return; then two
  more JoinGames without a mod list, the HandshakeReset path, a Forge backend without Backhand, disconnect):
  * beta.47: return -> `TRANSITION`, offhand off (the defect, reproduced);
  * beta.48: return -> `MODDED`, offhand on; the next JoinGames without a mod list -> `TRANSITION`, offhand off; a backend
    without Backhand -> MODDED, offhand off; HandshakeReset path and disconnect unchanged;
  * beta.48 with the kill-switch: identical to beta.47.
* Gates ran on JDK 21 in the session (no JDK 8 there); CI runs them on Zulu 8.

## What this does not fix

The black screen after the return. The log shows no crash, the FML handshake succeeded, the world (DIM2) was created, chat,
players, holograms and the gamemode packet arrived and the terrain pass rendered; the player was at y ~ 32, where an empty or
unrendered world shows a black sky. Whether chunks did not arrive (proxy) or were not drawn (renderer after the world swap) is
not in the log. A blocked offhand does not blank the screen, so the Backhand fix is not expected to change it. To tell
the two apart next time: F3 (`C: x/y` chunk count) and F3+A.

## Numbering

A first cut of this fix was built as "beta.44" on beta.43 from `main` in the same session, before it was clear that the line
had moved on to beta.47 on another branch (and that a different beta.44, Angelica240Decoration, already exists). That JAR was
never released and is not recorded; this build is the same patch on beta.47.

## Acceptance (pending, in the pack)

1. One `!Akashic-ThePatcher-*.jar` in `mods/`: this one. Rollback: beta.47.
2. Join, go to the lobby, come back: the last Backhand line of the return must be
   `join game consumed fresh FML capability proof: type=MODDED ... offhandAllowed=true`, and the offhand must work.
3. Fresh joins still end with the same line; on the lobby `offhandAllowed=false`.
