# Akashic-ThePatcher 1.1.0-beta.33 — WindowLifecycleFix

## Status
- Installed: YES
- Minecraft process: CLOSED
- Runtime production approval: PENDING one real boot/session
- Base: beta.32 HotspotAudit
- Base SHA-256: EE26B8B61C1C93E7611A7AD898989356CB9E38BF03C68BE09882559A1B107F30
- Candidate SHA-256: 6957C792CC4555C0DE6EECF63D375799942269F976BFCA1A2D396F116F31921D
- Rollback: rollback-installed-beta32.jar

## Incident evidence
- Minecraft PID 17336 stayed alive, responsive and network/audio-active while MainWindowHandle=0.
- Full Win32 enumeration found no LWJGL top-level or child window for the Minecraft process.
- Client thread remained in Minecraft.runGameLoop via Angelica FramePacer.beforePresent.
- No Windows TDR, display-driver reset, DWM crash, javaw crash or normal Minecraft shutdown was recorded.
- FMLPhaseAudit ended at 19:19:26 and BlockAtlasUploadBatcher flushed normally at 19:20:43.
- No evidence found that either startup optimizer remained active at incident time.

## Fix
- Minecraft 1.7.10 normally performs separate Display.setDisplayMode + Display.setFullscreen transitions.
- LWJGL 2.9.1 can destroy/recreate the native window in each operation.
- beta.33 redirects only the two validated runtime mode calls through Display.setDisplayModeAndFullscreen.
- Pre-display startup selection remains vanilla when Display.isCreated() is false.
- Transformer fails open if the exact validated bytecode shape is not present.
- Transition telemetry is emitted by Akashic-ThePatcher/WindowGuard.

## Validation
- Gradle clean/test/reobfJar: PASS.
- Real SRG Minecraft.class smoke: PASS (2 target calls replaced; 1 unrelated startup call preserved).
- Final JAR smoke: PASS.
- beta.32 critical inherited classes: byte-identical.
- Exactly one ThePatcher JAR installed.
