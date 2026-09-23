# Akashic ThePatcher beta.9 — MPS / Angelica 2.2.18 compatibility checkpoint

Date: 2026-09-23
Status: candidate technically validated; live Minecraft/GPU acceptance still required.

## Artifact

Source base: `!Akashic-ThePatcher-1.1.0-beta.8.jar`
Base SHA-256: `010c3b9f4fdd20528a772904f13238aa836c8af13759aebfe7184ed07beed44b`

Candidate:
`!Akashic-ThePatcher-1.1.0-beta.9-FINAL-CANDIDATE.jar`

Candidate SHA-256:
`cefc4f7b13e9c9bdba5f3c2c3c4a534d75a7d22096c6ea012737ef671e885e70`

Size: 233345 bytes.
Internal version: `1.1.0-beta.9`.

## Scope

The beta.9 preserves beta.8 and adds one client-only compatibility transformer:
`com.akashic.thepatcher.module.mps.MpsAngelicaCompatTransformer`.

Target combination is deliberately narrow:
- Angelica 2.2.18, exact GLStateManager fingerprint.
- Numina 0.4.1.105, exact raw RenderState fingerprint.
- Java 8/17/21 Angelica multi-release GLStateManager variants are explicitly fingerprinted.
The transformer redirects only six Numina RenderState calls:
- on2D -> compatibility push
- off2D -> compatibility pop
- blendingOn -> compatibility push
- blendingOff -> compatibility pop
- glowOn -> compatibility push
- glowOff -> compatibility pop

The compatibility stack explicitly saves/restores the state families Numina mutates
through Angelica GLStateManager instead of relying on Angelica 2.2.18 COW
glPushAttrib/glPopAttrib behavior.

Kill-switch:
`-Dakashic.mpsAngelica218.disable=true`

Any Angelica fingerprint mismatch, Numina fingerprint mismatch, or unexpected
method shape fails closed and returns the original class bytes unchanged.

## Targeted gates

All seven targeted transformer gates passed:
1. exact Angelica 2.2.18 + Numina 0.4.1.105
2. forced structural test
3. Angelica-pretransformed Numina call owners
4. kill-switch
5. Angelica fingerprint mismatch
6. Numina fingerprint mismatch
7. target method shape mismatch

The pretransformed gate proves the patch does not rely on transformer ordering.
## Inherited regression

The frozen beta.9 candidate passed all 16 inherited beta.8 functional gates:
- RGB transformer compilation/application
- Hodgepodge font access
- Hodgepodge RGB wrapping: 768 cases
- Hodgepodge extended wrapping: 10028 cases
- format continuation: 3072 cases
- extended format: 10028 cases
- exact legacy parity: 20256 cases
- format bridge: 5182 legacy + 3003 extended cases
- transformed format: 4004 cases
- RGB trim: 16128 cases / 327169 checks
- all associated compile gates

## Binary audit

beta.8 entries: 108
beta.9 entries: 113
unchanged beta.8 entries: 104
changed existing entries: 4
added entries: 5
removed entries: 0

Changed existing entries:
- META-INF/MANIFEST.MF
- mcmod.info
- com/akashic/thepatcher/AkashicThePatcherMod.class
- com/akashic/thepatcher/core/AkashicThePatcherLoadingPlugin.class
Added entries are exclusively:
- MpsAngelica218StateCompat.class
- MpsAngelica218StateCompat$1.class
- MpsAngelica218StateCompat$Snapshot.class
- MpsAngelica218StateCompat$StateStack.class
- MpsAngelicaCompatTransformer.class

All changed/added class files are Java 8 compatible (major <= 52).

## Determinism

The frozen candidate was rebuilt in separate processes twice after the final
multi-release fingerprint change. Both builds produced exactly:

`cefc4f7b13e9c9bdba5f3c2c3c4a534d75a7d22096c6ea012737ef671e885e70`

## Runtime acceptance still open

This checkpoint does not claim the translucent/invisible-block symptom is fixed
until the exact candidate is run in the real Forbidden client.

For the first live test change only ThePatcher beta.8 -> beta.9. Keep Angelica
2.2.18, GTNHLib 0.11.51, MPS, Numina, resource packs and current Angelica
settings unchanged. Keep Forge splash disabled during this graphics A/B.

After graphics acceptance, restore `config/splash.properties enabled=true` in
a separate test to restore the Akashic Cinematic Menu startup route.
