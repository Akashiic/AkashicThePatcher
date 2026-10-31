# beta.44 - Angelica240Decoration - 2026-10-29

## Incident
After updating Angelica from 2.2.37 to **2.2.40**, the yellow struck-through separators of the NecroTempus tab list header (`The End of Forbidden`) are cut short: the run on the left of each separator is full length, the run on the right stops at the last glyph. The pack's `latest.log` (beta.43, Angelica 2.2.40, NecroTempus 2.1.0):

```
[18:08:25] [Thread-12/INFO]: [Akashic-ThePatcher] WARN: Angelica whitespace decoration fix skipped: unsupported drawString fingerprint dba827d81374f97b0de687e6fdc9e11b45b6a9ff79d2255cb73c12a74b1489a2.
```

## Root cause
Angelica's `BatchingFontRenderer.drawString` still has the bug beta.38 fixed for 2.2.37. In 2.2.40 (source at tag `2.2.40`):

```java
if (whitespace) {
    curX += whitespaceStep(curBold);
    continue;                        // underlineEndX / strikethroughEndX not moved
}
...
curX = glyphPen(curX, glyphAdvance(...));
underlineEndX = curX;
strikethroughEndX = curX;            // only glyphs move the decoration end
```

`§m`/`§n` over spaces therefore ends at the last glyph of the run. A separator `§e§m<spaces>⊏` is drawn whole (the glyph at the end moves the end point over the spaces), `⊐§e§m<spaces>` only as far as `⊐`. That is the picture in the report.

`AngelicaDecorationPostProcessor` (RGB module) fixes this by inserting `underlineEndX = curX; strikethroughEndX = curX;` after the whitespace advance, but it fails closed: it only patches `drawString` versions it has an audited profile for (fingerprint + local slots of `curX`, `underlineEndX`, `strikethroughEndX`). 2.2.40 rewrote `drawString` (`FormatCodes`, `whitespaceStep`, glyph scaling, new locals), so the fingerprint and the slots changed and the fix was skipped.

| Angelica | fingerprint | curX | underlineEndX | strikethroughEndX |
|---|---|---|---|---|
| 2.2.37 | `e79b0611...` | 11 | 42 | 45 |
| 2.2.40 | `dba827d8...` | 12 | 46 | 49 |

The fingerprint of the official `Angelica-2.2.40.jar` (GTNH Maven, SHA-256 `c91adf27...`) is exactly the one in the pack's log, and its whitespace anchor (`fstore curX; goto`) is unique, as in 2.2.37.

## Fix
`AngelicaDecorationPostProcessor`: `PROFILES` gains `dba827d8... -> {12, 46, 49}`, and `hasEndpointFix(MethodNode)` recognises that layout (so a second pass is a no-op). The class has no source, so the edit is made at bytecode level by `tools/PatchBeta44Classes.java`, the same edit beta.38 made for 2.2.37. Nothing else changes: no new transformer, registration unchanged, same kill-switch as the RGB module.

## Gates (reports/final-build-audit.json) - all PASS
* Deterministic double build, SHA-256 `de1822940a65eef3a83297822a0fca143562dcda1afedae84cc2ce13d1064027`, 376644 B, 177 entries. Delta vs beta.43: decoration post-processor, mod class, manifest, mcmod.info; the other 173 byte-identical; class major <= 52. In the post-processor only `<clinit>` and `hasEndpointFix(MethodNode)` differ (`MethodDiff`).
* Angelica 2.2.40: beta.43 prints the pack's warning with the same fingerprint and leaves the class alone; beta.44 installs the endpoint fix, all 96 methods verify (`BasicVerifier`), `PlacementGate` finds exactly one `fstore 12; fload 12; fstore 46; fload 12; fstore 49; goto` (the whitespace branch), and running it again on the result is a no-op.
* Angelica 2.2.37 / 2.2.23 / 2.2.18: beta.44 produces exactly the bytes beta.43 produces (2.2.37: `9cfa19aa...`, the value recorded by beta.38).

Run locally with JDK 21 only (`javac --release 8`; gates on the JDK 21 JVM): no JDK 8 could be fetched in this session. CI (`ci.yml`) rebuilds beta.44 and runs the same gates on Zulu 8.

Not exercised: a real client on Angelica 2.2.40. The change is the profile table only; the code that rewrites `drawString` is beta.38's, which ran in the pack on 2.2.37.

## Live acceptance gate
1. Exactly one `!Akashic-ThePatcher-*.jar` in `mods/` (beta.44), Angelica 2.2.40.
2. The log shows `Angelica whitespace decoration endpoint fix installed: BatchingFontRenderer.drawString.` and no `Angelica whitespace decoration fix skipped`.
3. On the server, press TAB: both yellow separators of the header run full length on both sides of the bracket glyphs. Underlined/struck text in chat keeps the line under trailing spaces.
4. Rollback: beta.43 (the separators go back to the 2.2.40 look; nothing else changes).

## Note for the next Angelica update
Every Angelica release that touches `drawString` will skip this fix again until a profile is added (the log line above names the new fingerprint). To add one: run `Probe`-style reads of the new jar (fingerprint via `targetFingerprint`, slots from the local variable table), check the `fstore curX; goto` anchor is still unique, and repeat this build with the new values. The real fix belongs upstream: two assignments in the whitespace branch of `BatchingFontRenderer.drawString`. Once Angelica ships it, the "skipped" warning becomes harmless.
