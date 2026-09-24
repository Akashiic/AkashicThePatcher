# Akashic-ThePatcher 1.1.0-beta.21 - WorldRenderStateRepair

Date: 2026-09-24
Status: installed for live translucent/white-world diagnosis and conditional repair.

## Candidate
!Akashic-ThePatcher-1.1.0-beta.21-WorldRenderStateRepair.jar
SHA-256:
79dcb3f15653dc764c7075a06eafd0fb494261c8db35d3af3b5a2d830e1720f7

## Scope
Primary target:
CeleritasWorldRenderer.drawChunkLayer(BlockRenderLayer, double, double, double)

The probe executes at the exact HEAD of the Celeritas terrain layer, after RenderGlobal has:
- selected GL_TEXTURE0
- bound the blocks atlas through GLSM
- enabled GL_TEXTURE_2D
- enabled alpha test
- enabled fog

This makes cache-vs-driver comparisons relevant to the actual terrain draw.

## Compared cache vs real RenderBackend state
- active texture unit
- GL_TEXTURE_2D enable
- GL_TEXTURE_BINDING_2D
- GL_BLEND enable
- GL_DEPTH_TEST enable
- GL_ALPHA_TEST enable
- GL_CULL_FACE enable
- depth write mask
- depth func
- color write mask
- blend src/dst RGB+alpha
- blend equations
- blend color
- current color (diagnostic-only)
- alpha func/ref (diagnostic-only)

## Conditional repair
When cache and real backend disagree, beta.21 makes the backend match the GLSM cache for states with safe RenderBackend setters.

Repairs:
- active texture
- texture enable
- texture binding
- blend enable
- depth test
- alpha test
- cull face
- depth mask
- depth func
- color mask
- blend func
- blend equation
- blend color

Diagnostic-only:
- current color
- alpha func/ref

## Sampling
First 12 observations for vanilla pass 0 and pass 1 after each new world.

Markers:
WorldGLState new-world ...
WorldGLState pass=... sample=... mismatches=... repaired=... diff=[...]

Switches:
-Dakashic.worldRenderStateProbe=false
-Dakashic.worldRenderStateRepair=false

## Preserved
- Forestry pack-side CompilerOracle 6/6: unchanged
- Forestry Genome transformer/helper: byte-identical to beta.20
- ItemsAtlasBatch: byte-identical
- StitchLayoutCache: byte-identical
- hybrid async icon loader: byte-identical
- texture profilers: byte-identical
- High Texture Attemption.zip: untouched
- angelica-options.json: untouched
- options.txt: untouched
- hodgepodge.cfg: untouched
- bin/modpack.jar: untouched during beta.21 install

## Removed
Failed beta.20 StartupLifecycleProfiler Mixin and all 7 associated classes.
The previous "LoadController was loaded too early" error should disappear.

## Build gates
- exact Angelica 2.2.18 SHA:
  563d907f3035aedc7761be0f882674d6e736b63c5860e126e7af7df0d9f15c50
- exact GTNHLib 0.11.51 SHA:
  9d31e3a5011b39a111536a43f1c2bf74e83a037f5789cf66da3d482e2686adf7
- alpha.2.11 build/reobf/checkstyle PASS
- JUnit 9/9 PASS
- beta.21 deterministic build PASS
- inherited regressions 16/16 PASS
- Java class major <=52
- compile-only Angelica/GTNHLib not bundled
- existing startup/Forestry/batching classes preserved byte-identically

## Live test
1. Boot normally.
2. Do NOT toggle VSync or graphics settings before entering.
3. Enter the same server/lobby/world where translucency is reproducible.
4. Observe whether blocks are normal or translucent/white.
5. Stay in world for at least ~20 seconds so pass 0/1 samples complete.
6. Then exit normally if possible.

Interpretation:
- mismatch + repair + visual fixed => cache/driver divergence isolated.
- mismatch persists / diagnostic-only currentColor or alphaFunc => next targeted setter patch.
- zero mismatches + visual broken => rule out GLSM state cache; next target is Celeritas vertex/culling/translucency pipeline.