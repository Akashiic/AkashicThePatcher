"""Build and gate 1.1.0-beta.42-MiraiShaderBridge from the beta.41 release JAR.

    python3 beta42-build.py --jdk8 <jdk8 home> --jdk21 <jdk21 home> --libs <dir> --out <output.jar>
                            [--mirai <Akashic-Horizons-of-The-Mirai-forge17-0.1.19-dev-1.7.10.jar>]

--libs: tools/ci/fetch_build_libs.sh (beta.41's inputs; beta.42 also uses angelica-2.2.18/2.2.23/2.2.37 and
+unimixins-all-1.7.10-0.3.1, already in the list). --mirai is optional (the Mirai release is not public): with it
the bridge gate also runs on Mirai's real MixinRenderGlobal and the hooks' references are linked against the real
jar; CI runs without it, on the Mirai shape in test/mirai-shape.

Problem (2026-10-26): with Angelica 2.2.37 shaders on (Java 8, BSL 8.2.09 / Complementary r5.9.3), Akashic: Horizons of The Mirai 0.1.19 draws no distant
terrain. Angelica's Iris DH support (net.coderbot.iris.compat.dh, since Angelica PR #1553) only starts when
Class.forName("com.seibel.distanthorizons.DistantHorizonsTweaker") succeeds; upstream DH 1.7.10 renamed its
coremod to that on 2026-09-10 (dfca6541), after Mirai's pin (b4abe2f), so Angelica thinks DH is absent. Mirai also
lacks upstream's MixinRenderGlobal.renderDeferredLodsDuringTranslucentSetup (ef720c8e), so the translucent LODs
Angelica defers would never be drawn.

beta.42 = beta.41 +
  AngelicaShaderPackFolderTransformer  client; Angelica's ShaderPack finds world0/world-1/world1 in .zip packs on
                                 Java 8 (its Path::toString sees "world0/" there, so BSL 8.2.09 loaded no
                                 program and Iris reported "No DH shader found in this pack")
                                 (-Dakashic.angelicaShaderPackFolders.disable=true)
  MiraiShaderBridgeTransformer   client; @MixinSafeTransformer; Iris/DHCompat probe -> Mirai's coremod class name,
                                 MixinRenderGlobal + upstream's deferred-LOD @Inject (-> MiraiShaderBridgeHooks);
                                 only with Mirai installed and no DistantHorizonsTweaker
                                 (-Dakashic.miraiShaderBridge.disable=true)
  ChromaPlayerApiPreloadFix      Akashic-ChromaPlayerApiPreloadFix 1.0.0 folded in (Et Futurum elytra), armed from
                                 injectData on the client; stays off if the standalone jar is armed
                                 (-Dakashic.chromaPlayerApiPreload.disable=true)

Gates (results in reports/final-build-audit.json):
  * deterministic double build; entries: plugin, mod class, manifest, mcmod.info changed, six classes added, the rest
    byte-identical to beta.41; class major <= 52; plugin: only getASMTransformerClass and injectData differ
  * lineage composition: beta.37 -> 38 -> 39 -> 40 -> 41 -> 42, every entry each build added or changed is in beta.42
    as its newest version (one unit, not separate jars)
  * registration on the JVM verifier: client list = beta.41's plus the bridge after CoreTweaks' entry; server unchanged
  * MiraiBridgeGate on the real Angelica 2.2.37 Iris/DHCompat: exactly the probe LDC changes; MixinRenderGlobal keeps
    its methods and gains the handler; untouched for beta.41, kill-switch, server, no Mirai, upstream tweaker
    present, no Angelica; the same probe is patched on Angelica 2.2.18 and 2.2.23 (DH support since 2.2.18)
  * with --mirai: the hooks and Angelica 2.2.18/2.2.23/2.2.37's DH support link against the real Mirai 0.1.19 jar
  * FolderGate: the real ShaderPack of Angelica 2.2.18/2.2.23/2.2.37 changes in exactly one method reference
    (Path::toString -> AngelicaShaderPackFolderHooks.folderName); the transformed ShaderPack shape run on a .zip pack
    finds world0/world-1/world1 on Java 8, where beta.41 / kill-switch / server find none; Java 21 unaffected
  * HooksGate: Mirai's deferred LOD pass only in pass 1 and only while DH defers transparent rendering
  * ChromaGate: Hide/Restore around ChromatiCraft's shape on a real LaunchClassLoader list; Mixin's pre-read passes,
    the real load applies ENTITYPUSHOUT; beta.41 / kill-switch / server reproduce the throw; standalone jar wins
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
BASE = REPO / "releases/v1.1.0-beta.41-RenderCrashRecovery/!Akashic-ThePatcher-1.1.0-beta.41-RenderCrashRecovery.jar"
BASE_SHA = "8d7fddf3f2b00d3d97e5480dc3538e53a9b969fda745b99ec17edce7f52a4f6e"
OLD = "1.1.0-beta.41"
NEW = "1.1.0-beta.42"
LINEAGE = [
    ("1.1.0-beta.37", "releases/v1.1.0-beta.37-ForestryGenomeBarrier/!Akashic-ThePatcher-1.1.0-beta.37-ForestryGenomeBarrier.jar"),
    ("1.1.0-beta.38", "releases/v1.1.0-beta.38-AngelicaFontCompat/!Akashic-ThePatcher-1.1.0-beta.38-AngelicaFontCompat.jar"),
    ("1.1.0-beta.39", "releases/v1.1.0-beta.39-AngelicaFontMixinTrust/!Akashic-ThePatcher-1.1.0-beta.39-AngelicaFontMixinTrust.jar"),
    ("1.1.0-beta.40", "releases/v1.1.0-beta.40-ItemsAtlasAngelicaGuard/!Akashic-ThePatcher-1.1.0-beta.40-ItemsAtlasAngelicaGuard.jar"),
    ("1.1.0-beta.41", "releases/v1.1.0-beta.41-RenderCrashRecovery/!Akashic-ThePatcher-1.1.0-beta.41-RenderCrashRecovery.jar"),
]
MIRAI_SHA = "481d6f7b0f175465b00b278c37837cf65d7af90b3ba6d065c65819bf647986ea"

THIRD_PARTY = {
    "angelica-2.2.37.jar": "2125183d9f3c11a401be61e6273e1e813a51e41d892ba035b99cdfdb83e2677b",
    "angelica-2.2.23.jar": "a7b82ca3a193f6737ebb36650f7287b883cf6985a30608c8fed0a6a9c1d4a5f0",
    "angelica-2.2.18.jar": "563d907f3035aedc7761be0f882674d6e736b63c5860e126e7af7df0d9f15c50",
    "+unimixins-all-1.7.10-0.3.1.jar": "ad0ea4f92daf7bf7ec5c10e16258425e47929126954aea5d80df69ce26cd7318",
    "launchwrapper-1.12.jar": "57f402b626d16cc2705bf2a37add7adbb074f0ca3b3102fa6e23aa303dae682f",
    "asm-all-5.0.3.jar": "09d44e6adddaab9fd89ea3164c439f71c755769fa8d3a940352ab3d2003b98ff",
}
ASM9 = ("asm-9.7.jar", "asm-tree-9.7.jar", "asm-analysis-9.7.jar", "asm-util-9.7.jar")
RUNTIME = ("launchwrapper-1.12.jar", "asm-all-5.0.3.jar", "log4j-api.jar", "log4j-core.jar", "guava-17.0.jar",
           "jopt-simple-4.5.jar")

SRC = ROOT / "src/com/akashic/thepatcher/module"
SOURCES = [SRC / "mirai/MiraiShaderBridgeTransformer.java", SRC / "mirai/MiraiShaderBridgeHooks.java",
           SRC / "angelica/AngelicaShaderPackFolderTransformer.java", SRC / "angelica/AngelicaShaderPackFolderHooks.java",
           SRC / "chroma/ChromaPlayerApiPreloadFix.java"]
EXPECTED_ADDED = sorted([
    "com/akashic/thepatcher/module/angelica/AngelicaShaderPackFolderHooks.class",
    "com/akashic/thepatcher/module/angelica/AngelicaShaderPackFolderTransformer.class",
    "com/akashic/thepatcher/module/chroma/ChromaPlayerApiPreloadFix$1.class",
    "com/akashic/thepatcher/module/chroma/ChromaPlayerApiPreloadFix$Hide.class",
    "com/akashic/thepatcher/module/chroma/ChromaPlayerApiPreloadFix$Restore.class",
    "com/akashic/thepatcher/module/chroma/ChromaPlayerApiPreloadFix.class",
    "com/akashic/thepatcher/module/mirai/MiraiShaderBridgeHooks.class",
    "com/akashic/thepatcher/module/mirai/MiraiShaderBridgeTransformer.class",
])
PLUGIN = "com/akashic/thepatcher/core/AkashicThePatcherLoadingPlugin.class"
MOD = "com/akashic/thepatcher/AkashicThePatcherMod.class"
MANIFEST = "META-INF/MANIFEST.MF"
MCMOD = "mcmod.info"
EXPECTED_CHANGED = sorted([PLUGIN, MOD, MANIFEST, MCMOD])
ANCHOR_NAME = "com.akashic.thepatcher.module.crashrecovery.CoreTweaksCrashRecoveryTransformer"
NEW_NAMES = ["com.akashic.thepatcher.module.mirai.MiraiShaderBridgeTransformer",
             "com.akashic.thepatcher.module.angelica.AngelicaShaderPackFolderTransformer"]


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


def compile_shapes(args, work):
    libs = pathlib.Path(args.libs)
    stubs = compile_dir(args, ROOT / "test/stubs", work / "stubs", libs / "launchwrapper-1.12.jar")
    mirai = compile_dir(args, ROOT / "test/mirai-shape", work / "mirai-shape", stubs,
                        libs / "+unimixins-all-1.7.10-0.3.1.jar")
    return stubs, mirai


def build(args, work):
    libs = pathlib.Path(args.libs)
    stubs, mirai = compile_shapes(args, work)
    classes = work / "classes"
    javac(args, "-Xlint:all,-options", "-Werror",
          "-cp", cp(BASE, libs / "launchwrapper-1.12.jar", libs / "asm-all-5.0.3.jar", stubs, mirai),
          "-d", classes, *SOURCES)
    assert sorted(p.relative_to(classes).as_posix() for p in classes.rglob("*.class")) == EXPECTED_ADDED

    asm9 = cp(*(libs / j for j in ASM9))
    tools = work / "tools"
    javac(args, "-cp", asm9, "-d", tools, HERE / "PatchThePatcherVersion.java", HERE / "PatchBeta42Classes.java")
    java21 = pathlib.Path(args.jdk21) / "bin/java"
    run([java21, "-Xverify:all", "-cp", cp(tools, asm9), "com.akashic.compatlab.tools.PatchThePatcherVersion",
         BASE, OLD, NEW, work / "mod/AkashicThePatcherMod.class"], "THEPATCHER_VERSION_PATCH PASS")
    run([java21, "-Xverify:all", "-cp", cp(tools, asm9), "com.akashic.compatlab.tools.PatchBeta42Classes",
         BASE, work / "plugin/AkashicThePatcherLoadingPlugin.class"], "BETA42_PATCH PASS")

    content = readzip(BASE)
    for name in EXPECTED_ADDED:
        assert name not in content
        content[name] = (classes / name).read_bytes()
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
    """beta.37 -> ... -> beta.42: what each build added/changed, and that all of it is in beta.42."""
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
        assert new[name] == data, "%s from %s is not in beta.42" % (name, version)
    return {"steps": table, "carried_entries": len(newest),
            "result": "every entry added or changed by beta.38..beta.42 is in beta.42 as its newest version"}


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

    java8 = pathlib.Path(args.jdk8) / "bin/java"
    asm9 = cp(*(libs / j for j in ASM9))
    runtime = cp(*(libs / j for j in RUNTIME))
    stubs, mirai = compile_shapes(args, work)
    upstream = compile_dir(args, ROOT / "test/upstream-shape", work / "upstream-shape")
    chroma = compile_dir(args, ROOT / "test/chroma-shape", work / "chroma-shape", libs / "launchwrapper-1.12.jar")
    standalone = compile_dir(args, ROOT / "test/standalone-shape", work / "standalone-shape", libs / "launchwrapper-1.12.jar")
    test = work / "test"
    angelica_shape = compile_dir(args, ROOT / "test/angelica-shape", work / "angelica-shape")
    javac(args, "-cp", cp(runtime, jar, stubs, mirai), "-d", test,
          *(ROOT / "test" / n for n in ("RegistrationGate.java", "MiraiBridgeGate.java", "HooksGate.java",
                                        "ChromaGate.java", "FolderGate.java")))
    javac(args, "-cp", asm9, "-d", test / "asm9", ROOT / "test/MethodDiff.java", ROOT / "test/LinkGate.java")

    old_file, new_file = work / "old.class", work / "new.class"
    old_file.write_bytes(base[PLUGIN])
    new_file.write_bytes(new[PLUGIN])
    text = run([java8, "-cp", cp(test / "asm9", asm9), "MethodDiff", old_file, new_file], "METHOD_DIFF")
    differing = re.search(r"differing=\[(.*?)\]", text).group(1)
    assert re.fullmatch(r"\d+ getASMTransformerClass\(\)\[Ljava/lang/String;, \d+ injectData\(Ljava/util/Map;\)V",
                        differing), text
    report["method_diff." + PLUGIN] = text.strip()

    # Registration, on the real JVM verifier.
    lists = {}
    for label, version_jar in (("beta41", BASE), ("beta42", jar)):
        for side in ("CLIENT", "SERVER"):
            text = run([java8, "-Xverify:all", "-Dstub.side=" + side, "-cp", cp(test, version_jar, stubs, runtime),
                        "RegistrationGate"], "REGISTRATION side=" + side)
            lists[(label, side)] = re.search(r"list=\[(.*)\]", text).group(1).split(", ")
            report["registration.%s.%s" % (label, side)] = re.search(r"REGISTRATION.*", text).group(0)
    client41, client42 = lists[("beta41", "CLIENT")], lists[("beta42", "CLIENT")]
    at = client41.index(ANCHOR_NAME) + 1
    assert client42 == client41[:at] + NEW_NAMES + client41[at:], client42
    assert lists[("beta42", "SERVER")] == lists[("beta41", "SERVER")]
    assert not set(NEW_NAMES) & set(lists[("beta42", "SERVER")])

    # Mirai x Angelica shader bridge on Angelica's real classes.
    a37, a23, a18 = (libs / ("angelica-2.2.%d.jar" % v) for v in (37, 23, 18))
    cases = [("beta42", jar, a37, mirai, [], "patched"),
             ("beta41", BASE, a37, mirai, [], "untouched"),
             ("beta42.disabled", jar, a37, mirai, ["-Dakashic.miraiShaderBridge.disable=true"], "untouched"),
             ("beta42.server", jar, a37, mirai, ["-Dstub.side=SERVER"], "untouched"),
             ("beta42.no-mirai", jar, a37, None, [], "untouched"),
             ("beta42.upstream-tweaker", jar, a37, cp(mirai, upstream), [], "untouched"),
             ("beta42.no-angelica", jar, None, mirai, [], "untouched"),
             ("beta42.angelica-2.2.23", jar, a23, mirai, [], "patched"),
             ("beta42.angelica-2.2.18", jar, a18, mirai, [], "patched")]
    if args.mirai:
        assert sha(pathlib.Path(args.mirai).read_bytes()) == MIRAI_SHA, "unexpected Mirai jar"
        cases.append(("beta42.real-mirai-0.1.19", jar, a37, args.mirai, [], "patched"))
    for label, version_jar, angelica, dh, flags, expect in cases:
        classpath = cp(test, version_jar, stubs, runtime, *([angelica] if angelica else []) + ([dh] if dh else []))
        text = run([java8, "-Xverify:all"] + flags + ["-cp", classpath, "MiraiBridgeGate", expect],
                   "MIRAI_BRIDGE_GATE PASS expect=" + expect)
        report["mirai_bridge." + label] = lines(text, "A ", "MIRAI_BRIDGE_GATE", "[Akashic-ThePatcher]")

    text = run([java8, "-Xverify:all", "-cp", cp(test, jar, stubs, runtime, mirai), "HooksGate"], "HOOKS_GATE PASS")
    report["hooks"] = lines(text, "A ", "HOOKS_GATE", "[Akashic-ThePatcher]")
    if args.mirai:
        text = run([java8, "-cp", cp(test / "asm9", asm9), "LinkGate", jar, "com/akashic/thepatcher/module/mirai/",
                    args.mirai, "com/seibel/distanthorizons/"], "LINK_GATE PASS")
        report["link.hooks_to_real_mirai_0.1.19"] = lines(text, "A ", "LINK_GATE")
        # Angelica's DH support links against Mirai's API (it is compiled against upstream's DH API).
        for angelica in (a18, a23, a37):
            text = run([java8, "-cp", cp(test / "asm9", asm9), "LinkGate", angelica, "", args.mirai,
                        "com/seibel/distanthorizons/"], "LINK_GATE PASS")
            report["link.%s_to_real_mirai_0.1.19" % angelica.stem] = lines(text, "LINK_GATE")

    # Angelica's dimension-folder scan in .zip shader packs on Java 8.
    java21 = pathlib.Path(args.jdk21) / "bin/java"
    cases = [("beta42.java8", java8, jar, [], "fixed"),
             ("beta41.java8", java8, BASE, [], "broken"),
             ("beta42.java8.disabled", java8, jar, ["-Dakashic.angelicaShaderPackFolders.disable=true"], "broken"),
             ("beta42.java8.server", java8, jar, ["-Dstub.side=SERVER"], "broken"),
             ("beta42.java21", java21, jar, [], "fixed"),
             ("beta41.java21", java21, BASE, [], "broken")]
    for label, java, version_jar, flags, expect in cases:
        folder_work = work / ("folder-" + label)
        folder_work.mkdir()
        text = run([java, "-Xverify:all"] + flags + ["-cp", cp(test, version_jar, stubs, runtime), "FolderGate",
                    folder_work, angelica_shape, expect, a18, a23, a37], "FOLDER_GATE PASS expect=" + expect)
        report["angelica_zip_folders." + label] = lines(text, "A ", "FOLDER_GATE", "[Akashic-ThePatcher]")

    # Folded-in ChromaPlayerApiPreload fix.
    cases = [("beta42", jar, [], "fixed", ""),
             ("beta41", BASE, [], "broken", ""),
             ("beta42.disabled", jar, ["-Dakashic.chromaPlayerApiPreload.disable=true"], "broken", ""),
             ("beta42.server", jar, ["-Dstub.side=SERVER"], "broken", ""),
             ("beta42.standalone-jar-armed", jar, [], "standalone", standalone)]
    for label, version_jar, flags, expect, extra in cases:
        classpath = cp(test, version_jar, stubs, runtime, chroma, *([extra] if extra else []))
        text = run([java8, "-Xverify:all"] + flags + ["-cp", classpath, "ChromaGate", expect],
                   "CHROMA_GATE PASS expect=" + expect)
        report["chroma." + label] = lines(text, "A ", "CHROMA_GATE", "[Akashic-ThePatcher]")
    return report


def main():
    parser = argparse.ArgumentParser()
    for name in ("--jdk8", "--jdk21", "--libs", "--out"):
        parser.add_argument(name, required=True)
    parser.add_argument("--mirai")
    parser.add_argument("--report", default=str(ROOT / "reports/final-build-audit.json"))
    args = parser.parse_args()

    assert sha(BASE.read_bytes()) == BASE_SHA, "unexpected beta.41 base"
    builds = []
    for _ in range(2):
        with tempfile.TemporaryDirectory(prefix="beta42-") as tmp:
            builds.append(build(args, pathlib.Path(tmp)))
    assert builds[0] == builds[1], "non-deterministic build"
    out = pathlib.Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_bytes(builds[0])
    with tempfile.TemporaryDirectory(prefix="beta42-gates-") as tmp:
        report = gates(args, out.resolve(), pathlib.Path(tmp))
    report = dict({
        "version": NEW,
        "base": {"path": str(BASE.relative_to(REPO)), "sha256": BASE_SHA},
        "output": {"name": out.name, "sha256": sha(builds[0]), "bytes": len(builds[0])},
        "deterministic_rebuild": "PASS",
        "real_mirai_gates": "PASS (Mirai 0.1.19 %s)" % MIRAI_SHA if args.mirai else "not run (no --mirai)",
    }, **report)
    pathlib.Path(args.report).write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print("BETA42_BUILD PASS sha256=%s bytes=%d" % (sha(builds[0]), len(builds[0])))


if __name__ == "__main__":
    main()
