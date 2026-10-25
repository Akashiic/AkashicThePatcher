# NecroTempus source fix (for upstream)

The same fix as beta.38's runtime patch, as a change to NecroTempus itself, so it can be proposed to [CrucibleMC/NecroTempus](https://github.com/CrucibleMC/NecroTempus) and the runtime patch can eventually go away.

* Base: NecroTempus `master` at `3bc8f2d` (v2.1.0 plus a FUNDING commit).
* Patch: `0001-Support-Angelica-2.2.24-font-API-GL-texture-ids.patch` (`git am` it on that commit).
* NecroTempus is LGPL-2.1; this patch is offered under the same licence.

What it changes:

* `BatchingFontRendererMixin`: shadow and redirect `pushDrawCmd(IIIZ)V`; the handler checks `texture != 0`.
* `AngelicaGlyphProvider`, `AngelicaModernFontProvider`: `int getTexture(char)` via the new `AngelicaFontCompat.textureId` (GL id if loaded, `-1` otherwise; nothing is loaded).
* `AngelicaFontApiProbe` + `NecroTempusMixins`: the Angelica mixin is applied only when the installed Angelica has the int API (class file read, nothing loaded). With an older Angelica the integration is skipped instead of crashing.
* `dependencies.gradle`: compile against `com.github.GTNewHorizons:Angelica:2.2.37:dev`.

Verified on 2026-10-25: `./gradlew build` (compile, spotless, unit tests) passes; the built jar has no unresolved reference to Angelica 2.2.37 (API scan); its mixin passes the beta.38 resolution gate (`NecroTempusFontGate ... fixed`); the probe returns true for Angelica 2.2.37 and false for 2.2.23, 2.2.18 and no Angelica. The build needs the GTNH toolchains (JDK 25 for Gradle, Azul Zulu 17 and 21).

Once a NecroTempus release with this fix is installed, beta.38's transformer sees the new API and leaves NecroTempus alone.
