# Akashic-ThePatcher 1.1.0-beta.24 - TerrainStagingFallback

Date: 2026-09-24
Status: BUILT + GATED; installation pending only because Minecraft process is still running.

## Candidate
!Akashic-ThePatcher-1.1.0-beta.24-TerrainStagingFallback.jar
SHA-256:
41c66f3567a66544118e71ed0ab60343fa352c56b89836f8668af959376f5922

## Base
Live beta.23 VertexCompatIsolation:
SHA-256:
5a3c563b61f26a24d84b3b4fe89e626afa82e87f35e1ccd51bd9e39acd6097c3

beta.23 isolated only two Angelica config changes:
- advanced.use_vertex_array_objects: true -> false
- quality.texture_filter_mode: RGSS_ANISOTROPIC -> NONE
World translucency remained, so VAO and texture filtering did not resolve the issue.

## Numina / ModularPowersuits audit
Exact JARs:
Numina-0.4.1.105.jar
SHA-256 576b577093d77536ff403f8e166c6a7b49aacd14571b3d0739faf894bbc7e3b0

ModularPowersuits-1.7.10-0.11.1.118-Akashic-QMX.2.jar
SHA-256 546fafff354c8bf28ea9e66237ac01124e3468e8a46637aa1f24a82b0c965c71

Findings:
- neither JAR is a coremod/LaunchWrapper transformer
- manifests only expose FMLAT numina_at.cfg
- AT touches NetHandlerPlayServer floatingTickCount and KeyBinding fields only
- MPS RenderWorldLast handler is effectively empty
- RenderPlayer handlers only alter flight capability flags, not GL state
- Numina RenderState uses GL push/pop for GUI/HUD paths
- powersuits-keybinds.cfg is absent; runtime logs say "No powersuits keybind file found"
- TransparentArmorModule.isAllowed() is hard-coded false
- config "Use Pixel/Vertex Shaders=true" is effectively legacy/dead in this exact JAR:
  no GL20/ARB shader/glUseProgram classfile references; Config.useShaders is consumed then discarded during init
- beta.22 terrain state probe showed no persistent mismatch for blend/depth/cull/colorMask/texture binding that would indicate a Numina/MPS state leak
Conclusion: Numina/MPS are secondary/unlikely root causes for the global translucent terrain.

## WorldGLState result
beta.22 repeatedly saw only:
- texture2D cache=true, raw GL=false
- alphaFunc cache=GL_GREATER refs 0.5/0.1, raw GL=GL_ALWAYS@0.0

Exact Angelica 2.2.18 bytecode proves both are FFP-emulated states:
- glAlphaFunc updates AlphaStateStack + fragmentGeneration; no raw backend alpha func
- GL_TEXTURE_2D routes through TextureUnitBooleanStateStack / FFP state
Raw backend repair of texture2D did not alter the visual.
Therefore generic GLSM state divergence is not the cause.

## Strong next candidate: terrain persistent mapped staging
Celeritas RenderRegionManager.createStagingBuffer():
- chooses MappedStagingBuffer if supported
- otherwise chooses official FallbackStagingBuffer

MappedStagingBuffer support criteria include:
- OpenGL >= 4.4 or ARB_buffer_storage
- non-PIXEL_PACK buffer copy functions
- CORE map-range functions

Current runtime logs:
- OpenGL 4.6
- Buffer Storage: true
Therefore mapped staging is expected to be supported; beta.24 logs the live result instead of assuming.

Mapped path:
- 16 MiB immutable persistent mapped buffer
- storage flags: PERSISTENT, CLIENT_STORAGE, MAP_WRITE
- map flags: PERSISTENT, INVALIDATE_BUFFER, WRITE, EXPLICIT_FLUSH
- fences + copyBufferSubData into terrain arenas

Official fallback path:
- mutable STREAM_COPY staging buffer
- uploadData(ByteBuffer)
- copyBufferSubData into the same destination terrain buffer
- same StagingBuffer interface

## beta.24 behavior
New Mixin:
MixinRenderRegionManagerStagingFallback

At RenderRegionManager.createStagingBuffer(CommandList):
- calls MappedStagingBuffer.isSupported(RenderDevice.INSTANCE)
- logs:
  TerrainStaging mappedSupported=<true/false> forceFallback=<true/false> selected=<...>
- if mappedSupported=true and property enabled:
  returns new FallbackStagingBuffer(commandList)
- otherwise preserves Angelica default

Default:
-Dakashic.forceTerrainFallbackStaging=true

Disable:
-Dakashic.forceTerrainFallbackStaging=false

No WorldGLState probe/repair is present.

## Gates
- alpha.2.13 clean build/reobf/checkstyle PASS
- JUnit 9/9 PASS
- beta.24 deterministic build PASS
- inherited regressions 16/16 PASS
- Java class major <=52
- exactly one new behavioral class
- accepted Forestry / ItemsAtlasBatch / StitchLayoutCache / hybrid async components bit-identical to beta.23
- Angelica/GTNHLib/Embeddium classes not bundled

## Installation status
NOT installed because Java/Minecraft PID 17828 is still running at the main menu.
Safe replacement gate refused to mutate the loaded JAR.
CloseMainWindow and directed UI close were attempted non-destructively; the LWJGL window did not accept them.
No force-kill was performed.

## Next live gate
After Minecraft exits:
1. replace beta.23 with beta.24
2. preserve angelica-options.json exactly
3. boot and confirm:
   TerrainStaging mappedSupported=true forceFallback=true selected=FallbackStagingBuffer
4. enter same world/server
5. visual result:
   - fixed => persistent mapped staging isolated
   - still translucent => staging eliminated; next target is terrain shader vertex attribute/stride binding / DirectMultiDraw path