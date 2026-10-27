"""Build and gate 1.1.0-beta.43-HologramFastPath from the beta.42 release JAR.

    python3 beta43-build.py --jdk8 <jdk8 home> --jdk21 <jdk21 home> --libs <dir> --out <output.jar>

--libs: tools/ci/fetch_build_libs.sh (beta.43 adds lwjgl-2.9.1.jar, for the GL11 calls of the line renderer).

Problem (2026-10-26): HolographicDisplays 2.2.6 on the pack's 1.7.10 server. A 1.7 client has no armor stands, so
every text line is an EntityHorse (growing age -1700000, model scale about -34) riding an EntityWitherSkull 54.56
blocks above the line, every item line an EntityItem riding a skull. Measured in a real headless client (Forge
10.13.4.1614, Angelica 2.2.37, EntityCulling 1.6.4, beta.42, HD's packets replayed through the client net handler):
82 FPS with no hologram, 37 FPS with 300 lines, 21 FPS with 900. The bundled hologram module (beta.3) must keep the
carriers visible (Angelica and EntityCulling cull the inverted horse box: without it the holograms vanish and the
FPS stays at 82), so every horse model is drawn (and again in the shader shadow pass, ignoreFrustumCheck=true);
each skull ray-traces, queries entities and spawns smoke every tick (the particle engine sits at its 4000 cap).

beta.43 = beta.42 +
  HologramFastPathTransformer    client; head hooks in EntityFireball/EntityHorse/EntityItem/
                                 EntitySlime.onUpdate, Entity.isInRangeToRender3d, RenderManager.doRenderEntity,
                                 RendererLivingEntity.passSpecialRender, and RenderGlobal.renderEntities (before
                                 "blockentities") -> com.akashic.hologramfastpath.HologramFastPath: carriers stop
                                 ticking, leave the entity pipeline, and every text line is drawn in one pass
                                 (-Dakashic.hologramFastPath.disable=true; -Dakashic.hologramFastPath.range=<n>)
  Result in the same client: 300 lines 55 FPS, 900 lines 42 FPS, no smoke particles (reports/real-client/).

Gates (results in reports/final-build-audit.json):
  * deterministic double build; entries: plugin, mod class, manifest, mcmod.info changed, four classes added, the rest
    byte-identical to beta.42; class major <= 52; plugin: only getASMTransformerClass differs
  * the hooks' Minecraft references are SRG names, every one from tools/srg-members.txt (ReobfHooks)
  * lineage composition: beta.37 -> ... -> 43, every entry each build added or changed is in beta.43
  * registration on the JVM verifier: client list = beta.42's plus HologramFastPathTransformer last; server unchanged
  * TransformGate on the gate shapes of the eight vanilla classes: carriers frozen and out of the entity pipeline,
    normal entities and server-side carriers untouched, renderLines called once before "blockentities", idempotent;
    untouched with the kill-switch and on the server
"""
import argparse
import hashlib
import json
import pathlib
import re
import struct
import subprocess
import tempfile
import zipfile

HERE = pathlib.Path(__file__).resolve().parent
ROOT = HERE.parent
REPO = ROOT.parent.parent
BASE = REPO / "releases/v1.1.0-beta.42-MiraiShaderBridge/!Akashic-ThePatcher-1.1.0-beta.42-MiraiShaderBridge.jar"
BASE_SHA = "245e67cbf8f82e4395a9706c21bbee2ebd94911774600aa1e06f188adc0415df"
OLD = "1.1.0-beta.42"
NEW = "1.1.0-beta.43"
LINEAGE = [
    ("1.1.0-beta.37", "releases/v1.1.0-beta.37-ForestryGenomeBarrier/!Akashic-ThePatcher-1.1.0-beta.37-ForestryGenomeBarrier.jar"),
    ("1.1.0-beta.38", "releases/v1.1.0-beta.38-AngelicaFontCompat/!Akashic-ThePatcher-1.1.0-beta.38-AngelicaFontCompat.jar"),
    ("1.1.0-beta.39", "releases/v1.1.0-beta.39-AngelicaFontMixinTrust/!Akashic-ThePatcher-1.1.0-beta.39-AngelicaFontMixinTrust.jar"),
    ("1.1.0-beta.40", "releases/v1.1.0-beta.40-ItemsAtlasAngelicaGuard/!Akashic-ThePatcher-1.1.0-beta.40-ItemsAtlasAngelicaGuard.jar"),
    ("1.1.0-beta.41", "releases/v1.1.0-beta.41-RenderCrashRecovery/!Akashic-ThePatcher-1.1.0-beta.41-RenderCrashRecovery.jar"),
    ("1.1.0-beta.42", "releases/v1.1.0-beta.42-MiraiShaderBridge/!Akashic-ThePatcher-1.1.0-beta.42-MiraiShaderBridge.jar"),
]

THIRD_PARTY = {
    "launchwrapper-1.12.jar": "57f402b626d16cc2705bf2a37add7adbb074f0ca3b3102fa6e23aa303dae682f",
    "asm-all-5.0.3.jar": "09d44e6adddaab9fd89ea3164c439f71c755769fa8d3a940352ab3d2003b98ff",
    "lwjgl-2.9.1.jar": "c118f8d84313980f79e1e7f7ae4b14b10c2f20c77ea4a1c29f6f0b02bc139077",
}
ASM9 = ("asm-9.7.jar", "asm-tree-9.7.jar", "asm-analysis-9.7.jar", "asm-util-9.7.jar")
RUNTIME = ("launchwrapper-1.12.jar", "asm-all-5.0.3.jar", "log4j-api.jar", "log4j-core.jar", "guava-17.0.jar",
           "jopt-simple-4.5.jar")

SRC = ROOT / "src/com/akashic"
SOURCES = [SRC / "thepatcher/module/hologram/HologramFastPathTransformer.java",
           SRC / "hologramfastpath/HologramFastPath.java"]
EXPECTED_ADDED = sorted([
    "com/akashic/hologramfastpath/HologramFastPath.class",
    "com/akashic/thepatcher/module/hologram/HologramFastPathTransformer$Names.class",
    "com/akashic/thepatcher/module/hologram/HologramFastPathTransformer$Prologue.class",
    "com/akashic/thepatcher/module/hologram/HologramFastPathTransformer.class",
])
PLUGIN = "com/akashic/thepatcher/core/AkashicThePatcherLoadingPlugin.class"
MOD = "com/akashic/thepatcher/AkashicThePatcherMod.class"
MANIFEST = "META-INF/MANIFEST.MF"
MCMOD = "mcmod.info"
EXPECTED_CHANGED = sorted([PLUGIN, MOD, MANIFEST, MCMOD])
NEW_NAME = "com.akashic.thepatcher.module.hologram.HologramFastPathTransformer"


def sha(data):
    return hashlib.sha256(data).hexdigest()


def major(data):
    return struct.unpack_from(">H", data, 6)[0]


def readzip(path):
    with zipfile.ZipFile(str(path)) as z:
        assert z.testzip() is None
        return {n: z.read(n) for n in z.namelist() if not n.endswith("/")}


def make_zip(content, target):
    stamp = (2026, 10, 26, 12, 0, 0)
    with zipfile.ZipFile(str(target), "w", zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        for name in sorted(content):
            info = zipfile.ZipInfo(name, stamp)
            info.create_system = 3
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o644 << 16
            z.writestr(info, content[name], compress_type=zipfile.ZIP_DEFLATED, compresslevel=9)
    with zipfile.ZipFile(str(target)) as z:
        assert z.testzip() is None


def run(cmd, expect=None, cwd=None):
    proc = subprocess.run([str(c) for c in cmd], stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                          timeout=1800, env={"PATH": "/usr/bin:/bin"}, cwd=cwd)
    text = proc.stdout.decode("utf-8", "replace")
    text = "\n".join(l for l in text.splitlines() if not l.startswith("Picked up JAVA_TOOL_OPTIONS"))
    if proc.returncode != 0 or (expect and expect not in text):
        raise SystemExit("FAILED: %s\n%s" % (" ".join(map(str, cmd)), text[-4000:]))
    return text


def cp(*parts):
    return ":".join(str(p) for p in parts)


def javac(args, *rest):
    return run([pathlib.Path(args.jdk21) / "bin/javac", "--release", "8", "-nowarn", "-encoding", "UTF-8"] + list(rest))


def compile_dir(args, source_dir, target, *classpath):
    javac(args, *(["-cp", cp(*classpath)] if classpath else []), "-d", target, *sorted(source_dir.rglob("*.java")))
    return target


def compile_hooks(args, work):
    """MCP-named classes compiled against the stubs (tests run these), and their SRG-named shipping copy."""
    libs = pathlib.Path(args.libs)
    stubs = compile_dir(args, ROOT / "stubs", work / "stubs")
    mcp = work / "mcp-classes"
    javac(args, "-Xlint:all,-options", "-Werror",
          "-cp", cp(stubs, BASE, libs / "launchwrapper-1.12.jar", libs / "asm-all-5.0.3.jar", libs / "lwjgl-2.9.1.jar"),
          "-d", mcp, *SOURCES)
    assert sorted(p.relative_to(mcp).as_posix() for p in mcp.rglob("*.class")) == EXPECTED_ADDED
    asm9 = cp(*(libs / j for j in ASM9))
    tools = work / "tools"
    javac(args, "-cp", asm9, "-d", tools, HERE / "PatchThePatcherVersion.java", HERE / "PatchBeta43Classes.java",
          HERE / "ReobfHooks.java")
    java21 = pathlib.Path(args.jdk21) / "bin/java"
    srg = work / "srg-classes"
    text = run([java21, "-cp", cp(tools, asm9), "com.akashic.compatlab.tools.ReobfHooks", HERE / "srg-members.txt",
                mcp, srg], "REOBF_HOOKS PASS")
    return mcp, srg, tools, text.strip()


def build(args, work):
    libs = pathlib.Path(args.libs)
    _, srg, tools, _ = compile_hooks(args, work)
    asm9 = cp(*(libs / j for j in ASM9))
    java21 = pathlib.Path(args.jdk21) / "bin/java"
    run([java21, "-Xverify:all", "-cp", cp(tools, asm9), "com.akashic.compatlab.tools.PatchThePatcherVersion",
         BASE, OLD, NEW, work / "mod/AkashicThePatcherMod.class"], "THEPATCHER_VERSION_PATCH PASS")
    run([java21, "-Xverify:all", "-cp", cp(tools, asm9), "com.akashic.compatlab.tools.PatchBeta43Classes",
         BASE, work / "plugin/AkashicThePatcherLoadingPlugin.class"], "BETA43_PATCH PASS")

    content = readzip(BASE)
    for name in EXPECTED_ADDED:
        assert name not in content
        content[name] = (srg / name).read_bytes()
    content[PLUGIN] = (work / "plugin/AkashicThePatcherLoadingPlugin.class").read_bytes()
    content[MOD] = (work / "mod/AkashicThePatcherMod.class").read_bytes()
    for name in (MANIFEST, MCMOD):
        text = content[name].decode("utf-8")
        assert text.count(OLD) == 1, name
        content[name] = text.replace(OLD, NEW).encode("utf-8")
    out = work / "out.jar"
    make_zip(content, out)
    return out.read_bytes()


def lines(text, *prefixes):
    return [l.strip() for l in text.splitlines() if l.strip().startswith(prefixes)]


def lineage(new):
    """beta.37 -> ... -> beta.43: what each build added/changed, and that all of it is in beta.43."""
    chain = [(v, readzip(REPO / p)) for v, p in LINEAGE] + [(NEW, new)]
    newest = {}
    table = []
    for (v0, a), (v1, b) in zip(chain, chain[1:]):
        added = sorted(set(b) - set(a))
        changed = sorted(n for n in a if n in b and a[n] != b[n])
        removed = sorted(set(a) - set(b))
        assert not removed, (v1, removed)
        for name in added + changed:
            newest[name] = (v1, b[name])
        table.append({"build": v1, "added": added, "changed": changed})
    for name, (version, data) in sorted(newest.items()):
        assert new[name] == data, "%s from %s is not in beta.43" % (name, version)
    return {"steps": table, "carried_entries": len(newest),
            "result": "every entry added or changed by beta.38..beta.43 is in beta.43 as its newest version"}


def gates(args, jar, work):
    report = {}
    libs = pathlib.Path(args.libs)
    for name, expected in THIRD_PARTY.items():
        assert sha((libs / name).read_bytes()) == expected, "unexpected " + name
    report["third_party_sha256"] = dict(THIRD_PARTY)

    base, new = readzip(BASE), readzip(jar)
    assert not set(base) - set(new)
    added = sorted(set(new) - set(base))
    assert added == EXPECTED_ADDED, added
    changed = sorted(n for n in base if base[n] != new[n])
    assert changed == EXPECTED_CHANGED, changed
    for name in changed + added:
        if name.endswith(".class"):
            assert major(new[name]) <= 52, name
    report.update(entries=len(new), changed=changed, added=added, inherited_identical=len(base) - len(changed))
    report["lineage_composition"] = lineage(new)

    mcp, srg, _, reobf = compile_hooks(args, work)
    for name in EXPECTED_ADDED:
        assert (srg / name).read_bytes() == new[name], name + " differs from its ReobfHooks output"
    report["reobf_hooks"] = reobf

    java8 = pathlib.Path(args.jdk8) / "bin/java"
    asm9 = cp(*(libs / j for j in ASM9))
    runtime = cp(*(libs / j for j in RUNTIME))
    stubs = compile_dir(args, ROOT / "test/stubs", work / "test-stubs", libs / "launchwrapper-1.12.jar")
    shapes = compile_dir(args, ROOT / "test/mc-shape", work / "mc-shape")
    test = work / "test"
    javac(args, "-cp", cp(runtime, jar, stubs), "-d", test,
          *(ROOT / "test" / n for n in ("RegistrationGate.java", "TransformGate.java")))
    javac(args, "-cp", asm9, "-d", test / "asm9", ROOT / "test/MethodDiff.java")

    old_file, new_file = work / "old.class", work / "new.class"
    old_file.write_bytes(base[PLUGIN])
    new_file.write_bytes(new[PLUGIN])
    text = run([java8, "-cp", cp(test / "asm9", asm9), "MethodDiff", old_file, new_file], "METHOD_DIFF")
    differing = re.search(r"differing=\[(.*?)\]", text).group(1)
    assert re.fullmatch(r"\d+ getASMTransformerClass\(\)\[Ljava/lang/String;", differing), text
    report["method_diff." + PLUGIN] = text.strip()

    # Registration, on the real JVM verifier.
    lists = {}
    for label, version_jar in (("beta42", BASE), ("beta43", jar)):
        for side in ("CLIENT", "SERVER"):
            text = run([java8, "-Xverify:all", "-Dstub.side=" + side, "-cp", cp(test, version_jar, stubs, runtime),
                        "RegistrationGate"], "REGISTRATION side=" + side)
            lists[(label, side)] = re.search(r"list=\[(.*)\]", text).group(1).split(", ")
            report["registration.%s.%s" % (label, side)] = re.search(r"REGISTRATION.*", text).group(0)
    assert lists[("beta43", "CLIENT")] == lists[("beta42", "CLIENT")] + [NEW_NAME], lists[("beta43", "CLIENT")]
    assert lists[("beta43", "SERVER")] == lists[("beta42", "SERVER")]

    # The transformer on the gate shapes of the vanilla classes, exercised with the hooks.
    cases = [("beta43", [], "patched"),
             ("beta43.disabled", ["-Dakashic.hologramFastPath.disable=true"], "untouched"),
             ("beta43.server", ["-Dstub.side=SERVER"], "untouched")]
    for label, flags, expect in cases:
        text = run([java8, "-Xverify:all"] + flags + ["-cp", cp(test, jar, stubs, runtime, libs / "lwjgl-2.9.1.jar"),
                    "TransformGate", shapes, mcp, expect], "TRANSFORM_GATE PASS expect=" + expect)
        report["transform." + label] = lines(text, "A ", "TRANSFORM_GATE", "[Akashic-ThePatcher]")
    return report


def main():
    parser = argparse.ArgumentParser()
    for name in ("--jdk8", "--jdk21", "--libs", "--out"):
        parser.add_argument(name, required=True)
    parser.add_argument("--report", default=str(ROOT / "reports/final-build-audit.json"))
    args = parser.parse_args()

    assert sha(BASE.read_bytes()) == BASE_SHA, "unexpected beta.42 base"
    builds = []
    for _ in range(2):
        with tempfile.TemporaryDirectory(prefix="beta43-") as tmp:
            builds.append(build(args, pathlib.Path(tmp)))
    assert builds[0] == builds[1], "non-deterministic build"
    out = pathlib.Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_bytes(builds[0])
    with tempfile.TemporaryDirectory(prefix="beta43-gates-") as tmp:
        report = gates(args, out.resolve(), pathlib.Path(tmp))
    report = dict({
        "version": NEW,
        "base": {"path": str(BASE.relative_to(REPO)), "sha256": BASE_SHA},
        "output": {"name": out.name, "sha256": sha(builds[0]), "bytes": len(builds[0])},
        "deterministic_rebuild": "PASS",
    }, **report)
    pathlib.Path(args.report).parent.mkdir(parents=True, exist_ok=True)
    pathlib.Path(args.report).write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print("BETA43_BUILD PASS sha256=%s bytes=%d" % (sha(builds[0]), len(builds[0])))


if __name__ == "__main__":
    main()
