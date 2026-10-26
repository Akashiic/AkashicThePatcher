# Mirai x Angelica shader probe (beta.42 real-client A/B)

`DhShaderProbe.java` is a tiny client mod (modid `dhshaderprobe`, not shipped) for the beta.39 headless client
(`versions/*beta.39*/tools/headless-client/`). From the main menu it creates a default creative world with commands
(seed 20261026, MC render distance 4 chunks), teleports to y=120 at noon, looks at the horizon and every 100 ticks logs:

* `shaderPackInUse` (Iris API), `irisDhPresent` / `irisDhRendering` (Angelica's `DHCompat`),
* `deferTransparent` (DH renderProxy, set by Angelica's DH support while it is active), `dhRenderingEnabled`,
* the DH render passes seen through `DhApiBeforeRenderPassEvent` (`OPAQUE` + `TRANSPARENT` = shader path,
  `OPAQUE_AND_TRANSPARENT` = DH's own shader-less path) and the LOD buffers drawn (`DhApiBeforeBufferRenderEvent`),
* at ticks 200 and 1200 a `diag` of the loaded pack: which `ProgramSet` sources exist (`dh_terrain`, `dh_water`,
  `gbuffers_terrain`), the dimension folders found, and the pipeline's `DHCompatInternal` (`shouldOverride`, `incompatible`).

Compile against Forge 10.13.4.1614 universal and DH's API (Mirai's `coreSubProjects/api`, or the Mirai jar), Java 8
target, and put `mcmod.info` next to the classes. `mkinst.sh` builds the instances; results are in `../../reports/real-client/`.
