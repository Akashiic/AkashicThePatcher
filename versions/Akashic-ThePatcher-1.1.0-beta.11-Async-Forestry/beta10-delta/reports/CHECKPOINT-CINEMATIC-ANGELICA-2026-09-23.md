# Akashic ThePatcher beta.10 — Cinematic / Angelica graphics checkpoint

Date: 2026-09-23
Status: technical candidate PASS; real GPU/client acceptance pending.

## Why beta.9 was abandoned

The beta.9 Numina/MPS hypothesis did not survive the live test.
The beta.9 transformer registered successfully, but the target class
`net.machinemuse.numina.render.RenderState` was not loaded during the boot
that still produced white world blocks. Therefore beta.9 did not participate
in the failing render path and is not used as beta.10's base.

beta.10 is rebuilt directly from the verified beta.8 artifact:
`010c3b9f4fdd20528a772904f13238aa836c8af13759aebfe7184ed07beed44b`.

## New evidence

At 17:14:50 the real client loaded:
`br.com.redeakashic.cinematic.render.RawScope`.

LaunchWrapper recorded RawScope at 2559 bytes immediately before
`AngelicaRedirectorTransformer`, and 2646 bytes immediately after it.
Thus Angelica 2.2.18 is definitely rewriting the Cinematic Menu's raw GL calls.

RawScope uses `glPushAttrib(GL_ALL_ATTRIB_BITS)`, disables texture targets
across all available texture units, modifies depth/alpha/lighting/fog/cull/
scissor/blend state, then relies on `glPopAttrib()` for restoration.
Angelica 2.2.18's global GLSM redirector maps glPushAttrib/glPopAttrib,
glEnable/glDisable, glActiveTexture and the other RawScope calls into
GLStateManager. The Cinematic package is not in Angelica's core exclusion list.

This is a materially stronger link to the white-block symptom than Numina:
the RawScope executes during startup before the world, and GL_TEXTURE_2D state
leak/desynchronization can render otherwise valid geometry as white.

This remains a hypothesis until live beta.10 acceptance.

## Crash evidence

The video-options crash is separate and concrete:
Angelica 2.2.18 -> Lwjgl2GLRenderBackend.applyVSyncMode ->
LWJGL2 Display.setVSyncEnabled -> OpenGLException GL_INVALID_ENUM (1280).

LWJGL 2.9.1 performs Util.checkGLError after the native swap interval request,
so the exception may represent either the swap-interval path or a pending GL
error exposed there. beta.10 drains/logs pending errors before the request and
fails soft rather than terminating the client.

## beta.10 fixes

### 1. Cinematic RawScope state fence

After the exact alpha.4 RawScope.close() return path, beta.10 invokes a
reflection-based Angelica fence.
The fence:
1. calls GLStateManager.replayStateToBackend();
2. disables lighting;
3. selects texture unit 1 and disables GL_TEXTURE_2D;
4. selects texture unit 0 and enables GL_TEXTURE_2D;
5. calls replayStateToBackend() again.

The texture-unit baseline intentionally mirrors the state reset Angelica itself
introduced in PR #1571 while fixing GLSM state leaks.

On first use it compares GLSM's cached GL_TEXTURE_2D state with the actual
LWJGL driver state and logs cache/driver before -> after.

Kill switch:
`-Dakashic.cinematicAngelicaStateFence.disable=true`

### 2. Angelica VSync fail-soft

Exact Angelica 2.2.18 Lwjgl2GLRenderBackend.applyVSyncMode has its single
Display.setVSyncEnabled(boolean) invocation replaced with AngelicaVSyncGuard.

The guard drains/logs pending GL errors, invokes LWJGL2 through reflection,
and catches/logs the OpenGLException instead of crashing the client.

Kill switch:
`-Dakashic.angelicaVSyncGuard.disable=true`

## Targeted gates

PASS:
- RawScope exact transform: one fence
- RawScope kill-switch: byte-identical
- RawScope method-shape mismatch: fail-closed byte-identical
- VSync exact transform: one guarded call
- VSync kill-switch: byte-identical
- VSync method-shape mismatch: fail-closed byte-identical
## Inherited regression

All 16 beta.8 functional regression gates passed against beta.10, including:
- Hodgepodge RGB wrapping
- Hodgepodge extended wrapping
- format continuation
- extended format
- exact legacy parity
- bridge compatibility
- transformed format
- RGB trim (327169 checks)
and all associated compile/transform gates.

## Binary audit

beta.8 entries: 108
beta.10 entries: 111
unchanged entries: 104
changed existing entries: 4
added entries: 3
removed entries: 0

Changed:
- META-INF/MANIFEST.MF
- mcmod.info
- AkashicThePatcherMod.class
- AkashicThePatcherLoadingPlugin.class

Added:
- AngelicaVSyncGuard.class
- CinematicAngelicaCompatTransformer.class
- CinematicAngelicaStateFence.class

All changed/added classes remain Java 8 compatible.

## Candidate

`!Akashic-ThePatcher-1.1.0-beta.10-Cinematic-Angelica.jar`
SHA-256:
`3182e7e3d6b51854eae008ff8eb8f346a1d0a8b291740ceff8dab9c5fabc9dab`

The artifact rebuilt identically in separate executions.
No production installation was performed.
