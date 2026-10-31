"""Build and gate 1.1.0-beta.47-BaublesApiAngelicaLookup from the beta.46 release JAR.

    python3 beta47-build.py --jdk8 <jdk8 home> --jdk21 <jdk21 home> --libs <dir> --out <output.jar>

--libs: tools/ci/fetch_build_libs.sh (beta.47 adds BaublesExpanded 2.2.22 and 2.2.26, GTNHLib 0.11.54 and JOML 1.10.5,
which Angelica's GLSM classes need to link in the gates).

Problems (2026-10-30, two players, beta.43, Angelica 2.2.40, Baubles Expanded 2.2.26):
  1. Putting Et Futurum Requiem's elytra in a bauble slot crashes (CoreTweaks catches it):
         java.lang.NoSuchMethodError: baubles.api.expanded.IBaubleExpanded.onSlotContentsChanged(ItemStack, EntityLivingBase)V
             at baubles.common.container.SlotBauble.func_75218_e(SlotBauble.java:85)
     Baubles Expanded 2.2.26 added that default method and calls it; the IBaubleExpanded actually loaded is an older
     copy embedded in another jar that comes earlier on the class path.
  2. Opening the inventory (or seeing a player) while wearing an Adventure Backpack crashes:
         ExceptionInInitializerError at br.com.akashic.backpackcompat.client.RenderHooks.renderPart(RenderHooks.java:19)
         Caused by: IllegalStateException: Missing GL API glPushMatrix in class ...angelica.glsm.GLStateManager
     Angelica 2.2.40 moved most GL entry points out of GLStateManager (table GLSMOwners.METHODS); AkashicBackpackCompat
     1.0.0-rc1 looks them up there with MethodHandles.Lookup.findStatic. On the phone this was followed by an
     OutOfMemoryError and an Android ANR.

beta.47 = beta.46 +
  BaublesExpandedApiTransformer  (both sides) the loaded IBaubleExpanded gets the 2.2.26 empty default
                                 onSlotContentsChanged when it lacks it, and the log names every jar carrying a copy
                                 (-Dakashic.baublesExpandedApi.disable=true)
  AngelicaGlLookupTransformer    (client) GlApi's Lookup.findStatic calls go through AngelicaGlLookupHooks.findStatic:
                                 original lookup first, then the class GLSMOwners moved the method to
                                 (-Dakashic.angelicaGlLookup.disable=true)
Both registered last, in that order, after beta.46's projector slot guard.

Gates (results in reports/final-build-audit.json):
  * deterministic double build; entries: plugin, mod class, manifest, mcmod.info changed, four classes added, the rest
    byte-identical to beta.46; class major <= 52; plugin: only getASMTransformerClass differs
  * registration on the JVM verifier: client = beta.46's + both transformers last; server = beta.46's + the Baubles one
  * RealBaublesGate: the real 2.2.22 IBaubleExpanded gets exactly the empty public default method (client and server),
    a second pass is a no-op; the real 2.2.26 one, beta.46 and the kill-switch leave the bytes unchanged
  * BaublesGate (-Xverify:all, real 2.2.22 / 2.2.26 API classes + slot/elytra shapes compiled against 2.2.26): the elytra
    in a bauble slot reproduces the NoSuchMethodError with beta.46 / kill-switch and works with beta.47, an overriding
    bauble still gets its call; with the 2.2.26 API nothing is transformed
  * GlApiGate (-Xverify:all, real Angelica 2.2.40 / 2.2.37 + GTNHLib, shape of GlApi): on 2.2.40 beta.46 / kill-switch
    / server reproduce "Missing GL API glPushMatrix" and beta.47 resolves glPushMatrix... in GLSMMatrices, glEnable in
    GLSMEnable; on 2.2.37 everything still resolves in GLStateManager
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
BASE = REPO / "releases/v1.1.0-beta.46-ProjectorOffhandGuard/!Akashic-ThePatcher-1.1.0-beta.46-ProjectorOffhandGuard.jar"
BASE_SHA = "e813946b3b4ead550dc38ae147ae60a7454ddaa338e81fee332e6a74ad7306d5"
OLD = "1.1.0-beta.46"
NEW = "1.1.0-beta.47"

THIRD_PARTY = {
    "BaublesExpanded-2.2.22-GTNH.jar": "beb573d425a583c8cec1ee6e19815cdcff44d1e8982fc2ed3ff140f86b2e8e99",
    "BaublesExpanded-2.2.26-GTNH.jar": "554624dd865489d852dd463abe17cebc546961f1a288a78f5712aeb6d8253d8d",
    "angelica-2.2.40.jar": "c91adf2712aa65f8d99f5378a3027f538ab8e027c0b5b2aa261cc632ae937c54",
    "angelica-2.2.37.jar": "2125183d9f3c11a401be61e6273e1e813a51e41d892ba035b99cdfdb83e2677b",
    "gtnhlib-0.11.54.jar": "61340948f200ce52f210380ec00ec763fe867fac10c6342743e04c1688e85039",
    "joml-1.10.5.jar": "cac9f22f83a7aa33eebda73c16ff5261e3cb4911b6bafcf4c79ea486099d0c9a",
    "fastutil-core-8.5.12.jar": "f31c20f5b06312f3d5e06e6160a32e274d819aa6cebf27528b26b6b5c0c1df19",
    "lwjgl-2.9.1.jar": "c118f8d84313980f79e1e7f7ae4b14b10c2f20c77ea4a1c29f6f0b02bc139077",
    "launchwrapper-1.12.jar": "57f402b626d16cc2705bf2a37add7adbb074f0ca3b3102fa6e23aa303dae682f",
    "asm-all-5.0.3.jar": "09d44e6adddaab9fd89ea3164c439f71c755769fa8d3a940352ab3d2003b98ff",
}
ASM9 = ("asm-9.7.jar", "asm-tree-9.7.jar", "asm-analysis-9.7.jar", "asm-util-9.7.jar")
RUNTIME = ("launchwrapper-1.12.jar", "asm-all-5.0.3.jar", "log4j-api.jar", "log4j-core.jar", "guava-17.0.jar",
           "jopt-simple-4.5.jar")

SRC = ROOT / "src/com/akashic/thepatcher/module"
SOURCES = [SRC / "baubles/BaublesExpandedApiTransformer.java", SRC / "angelica/AngelicaGlLookupTransformer.java",
           SRC / "angelica/AngelicaGlLookupHooks.java"]
EXPECTED_ADDED = sorted([
    "com/akashic/thepatcher/module/angelica/AngelicaGlLookupHooks.class",
    "com/akashic/thepatcher/module/angelica/AngelicaGlLookupTransformer.class",
    "com/akashic/thepatcher/module/baubles/BaublesExpandedApiTransformer.class",
])
PLUGIN = "com/akashic/thepatcher/core/AkashicThePatcherLoadingPlugin.class"
MOD = "com/akashic/thepatcher/AkashicThePatcherMod.class"
MANIFEST = "META-INF/MANIFEST.MF"
MCMOD = "mcmod.info"
EXPECTED_CHANGED = sorted([PLUGIN, MOD, MANIFEST, MCMOD])
BAUBLES_NAME = "com.akashic.thepatcher.module.baubles.BaublesExpandedApiTransformer"
GL_NAME = "com.akashic.thepatcher.module.angelica.AngelicaGlLookupTransformer"
BAUBLES_OFF = "-Dakashic.baublesExpandedApi.disable=true"
GL_OFF = "-Dakashic.angelicaGlLookup.disable=true"
GLSM_LIBS = ("gtnhlib-0.11.54.jar", "lwjgl-2.9.1.jar", "joml-1.10.5.jar", "fastutil-core-8.5.12.jar")


def sha(data):
    return hashlib.sha256(data).hexdigest()


def major(data):
    return struct.unpack_from(">H", data, 6)[0]


def readzip(path):
    with zipfile.ZipFile(str(path)) as z:
        assert z.testzip() is None
        return {n: z.read(n) for n in z.namelist() if not n.endswith("/")}


def make_zip(content, target):
    stamp = (2026, 10, 30, 3, 30, 0)
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


def build(args, work):
    libs = pathlib.Path(args.libs)
    stubs = compile_dir(args, ROOT / "test/stubs", work / "stubs")
    classes = work / "classes"
    javac(args, "-Xlint:all,-options", "-Werror",
          "-cp", cp(BASE, libs / "launchwrapper-1.12.jar", libs / "asm-all-5.0.3.jar", stubs), "-d", classes, *SOURCES)
    assert sorted(p.relative_to(classes).as_posix() for p in classes.rglob("*.class")) == EXPECTED_ADDED

    asm9 = cp(*(libs / j for j in ASM9))
    tools = work / "tools"
    javac(args, "-cp", asm9, "-d", tools, HERE / "PatchThePatcherVersion.java", HERE / "PatchBeta47Classes.java")
    java21 = pathlib.Path(args.jdk21) / "bin/java"
    run([java21, "-Xverify:all", "-cp", cp(tools, asm9), "com.akashic.compatlab.tools.PatchThePatcherVersion",
         BASE, OLD, NEW, work / "mod/AkashicThePatcherMod.class"], "THEPATCHER_VERSION_PATCH PASS")
    run([java21, "-Xverify:all", "-cp", cp(tools, asm9), "com.akashic.compatlab.tools.PatchBeta47Classes",
         BASE, work / "plugin/AkashicThePatcherLoadingPlugin.class"], "BETA47_PATCH PASS")

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


def gates(args, jar, work):
    report = {}
    libs = pathlib.Path(args.libs).resolve()
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

    java8 = pathlib.Path(args.jdk8) / "bin/java"
    report["gate_jvm"] = run([java8, "-version"]).strip().splitlines()[0]
    asm9 = cp(*(libs / j for j in ASM9))
    runtime = cp(*(libs / j for j in RUNTIME))
    stubs = compile_dir(args, ROOT / "test/stubs", work / "stubs")
    mc = compile_dir(args, ROOT / "test/mc-stubs", work / "mc")
    test = work / "test"
    javac(args, "-cp", cp(runtime, jar, stubs), "-d", test,
          *(ROOT / "test" / n for n in ("TransformingLoader.java", "RegistrationGate.java", "BaublesGate.java",
                                        "RealBaublesGate.java", "GlApiGate.java")))
    # The two Baubles API copies under test, each with the slot/elytra shapes compiled against 2.2.26.
    be22, be26 = libs / "BaublesExpanded-2.2.22-GTNH.jar", libs / "BaublesExpanded-2.2.26-GTNH.jar"
    baubles_shapes = compile_dir(args, ROOT / "test/baubles-shapes", work / "baubles-shapes", be26, mc)
    api_dirs = {}
    for label, api_jar in (("2.2.22", be22), ("2.2.26", be26)):
        target = work / ("api-" + label)
        with zipfile.ZipFile(str(api_jar)) as z:
            for entry in z.namelist():
                if entry.startswith("baubles/api/") and entry.endswith(".class"):
                    (target / entry).parent.mkdir(parents=True, exist_ok=True)
                    (target / entry).write_bytes(z.read(entry))
        for shape in baubles_shapes.rglob("*.class"):
            dest = target / shape.relative_to(baubles_shapes)
            dest.parent.mkdir(parents=True, exist_ok=True)
            dest.write_bytes(shape.read_bytes())
        api_dirs[label] = target
    glapi = compile_dir(args, ROOT / "test/angelica-shapes", work / "angelica-shapes")
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
    for label, version_jar in (("beta46", BASE), ("beta47", jar)):
        for side in ("CLIENT", "SERVER"):
            text = run([java8, "-Xverify:all", "-Dstub.side=" + side, "-cp", cp(test, version_jar, stubs, runtime),
                        "RegistrationGate"], "REGISTRATION side=" + side)
            lists[(label, side)] = re.search(r"list=\[(.*)\]", text).group(1).split(", ")
            report["registration.%s.%s" % (label, side)] = re.search(r"REGISTRATION.*", text).group(0)
    assert lists[("beta47", "CLIENT")] == lists[("beta46", "CLIENT")] + [BAUBLES_NAME, GL_NAME], lists[("beta47", "CLIENT")]
    assert lists[("beta47", "SERVER")] == lists[("beta46", "SERVER")] + [BAUBLES_NAME], lists[("beta47", "SERVER")]

    # The real IBaubleExpanded of Baubles Expanded 2.2.22 (no default method) and 2.2.26 (has it).
    cases = [("beta47.2.2.22", jar, [], be22, "patched"),
             ("beta47.server.2.2.22", jar, ["-Dstub.side=SERVER"], be22, "patched"),
             ("beta47.2.2.26", jar, [], be26, "untouched"),
             ("beta46.2.2.22", BASE, [], be22, "untouched"),
             ("beta47.disabled.2.2.22", jar, [BAUBLES_OFF], be22, "untouched")]
    for label, version_jar, flags, api_jar, expect in cases:
        text = run([java8, "-Xverify:all"] + flags + ["-cp", cp(test, version_jar, stubs, runtime),
                    "RealBaublesGate", api_jar, expect], "REAL_BAUBLES PASS expect=" + expect)
        report["real_baubles." + label] = lines(text, "A ", "REAL_BAUBLES", "[Akashic-ThePatcher]")
    assert any("Baubles Expanded API guard applied" in l for l in report["real_baubles.beta47.2.2.22"])
    # With copies on the class path the warning names them, first the one in use (in the game: LaunchClassLoader).
    text = run([java8, "-Xverify:all", "-cp", cp(test, jar, stubs, runtime, be22, be26),
                "RealBaublesGate", be22, "patched"], "REAL_BAUBLES PASS expect=patched")
    report["real_baubles.beta47.2.2.22.sources"] = lines(text, "A ", "REAL_BAUBLES", "[Akashic-ThePatcher]")
    assert any("in use: [BaublesExpanded-2.2.22-GTNH.jar, BaublesExpanded-2.2.26-GTNH.jar]" in l
               for l in report["real_baubles.beta47.2.2.22.sources"]), report["real_baubles.beta47.2.2.22.sources"]

    # Behaviour: Et Futurum's elytra put in a bauble slot.
    cases = [("beta47.api-2.2.22", jar, [], "2.2.22", "guarded", 1),
             ("beta47.server.api-2.2.22", jar, ["-Dstub.side=SERVER"], "2.2.22", "guarded", 1),
             ("beta46.api-2.2.22", BASE, [], "2.2.22", "crash", 0),
             ("beta47.disabled.api-2.2.22", jar, [BAUBLES_OFF], "2.2.22", "crash", 0),
             ("beta47.api-2.2.26", jar, [], "2.2.26", "guarded", 0),
             ("beta46.api-2.2.26", BASE, [], "2.2.26", "guarded", 0)]
    for label, version_jar, flags, api, expect, transformed in cases:
        text = run([java8, "-Xverify:all"] + flags + ["-cp", cp(test, version_jar, stubs, runtime, mc),
                    "BaublesGate", api_dirs[api], expect],
                   "BAUBLES_GATE PASS expect=%s transformed=%d" % (expect, transformed))
        report["baubles." + label] = lines(text, "A ", "BAUBLES_GATE", "[Akashic-ThePatcher]")

    # Behaviour: AkashicBackpackCompat's GlApi (shape) against the real Angelica.
    glsm_libs = cp(*(libs / j for j in GLSM_LIBS))
    cases = [("beta47.angelica-2.2.40", jar, [], "2.2.40", "moved", 1),
             ("beta46.angelica-2.2.40", BASE, [], "2.2.40", "crash", 0),
             ("beta47.disabled.angelica-2.2.40", jar, [GL_OFF], "2.2.40", "crash", 0),
             ("beta47.server.angelica-2.2.40", jar, ["-Dstub.side=SERVER"], "2.2.40", "crash", 0),
             ("beta47.angelica-2.2.37", jar, [], "2.2.37", "glsm", 1),
             ("beta46.angelica-2.2.37", BASE, [], "2.2.37", "glsm", 0)]
    for label, version_jar, flags, angelica, expect, transformed in cases:
        text = run([java8, "-Xverify:all"] + flags + ["-cp", cp(test, version_jar, stubs, runtime,
                    libs / ("angelica-%s.jar" % angelica), glsm_libs), "GlApiGate", glapi, expect],
                   "GLAPI_GATE PASS expect=%s transformed=%d" % (expect, transformed), cwd=work)  # Angelica's log4j writes logs/
        report["glapi." + label] = lines(text, "A ", "GLAPI_GATE", "[Akashic-ThePatcher]")
    assert any("resolved in com.gtnewhorizons.angelica.glsm.GLSMMatrices" in l
               for l in report["glapi.beta47.angelica-2.2.40"]), report["glapi.beta47.angelica-2.2.40"]
    return report


def main():
    parser = argparse.ArgumentParser()
    for name in ("--jdk8", "--jdk21", "--libs", "--out"):
        parser.add_argument(name, required=True)
    parser.add_argument("--report", default=str(ROOT / "reports/final-build-audit.json"))
    args = parser.parse_args()

    assert sha(BASE.read_bytes()) == BASE_SHA, "unexpected beta.46 base"
    builds = []
    for _ in range(2):
        with tempfile.TemporaryDirectory(prefix="beta47-") as tmp:
            builds.append(build(args, pathlib.Path(tmp)))
    assert builds[0] == builds[1], "non-deterministic build"
    out = pathlib.Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_bytes(builds[0])
    with tempfile.TemporaryDirectory(prefix="beta47-gates-") as tmp:
        report = gates(args, out.resolve(), pathlib.Path(tmp))
    report = dict({
        "version": NEW,
        "base": {"path": str(BASE.relative_to(REPO)), "sha256": BASE_SHA},
        "output": {"name": out.name, "sha256": sha(builds[0]), "bytes": len(builds[0])},
        "deterministic_rebuild": "PASS",
    }, **report)
    pathlib.Path(args.report).parent.mkdir(parents=True, exist_ok=True)
    pathlib.Path(args.report).write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print("BETA47_BUILD PASS sha256=%s bytes=%d" % (sha(builds[0]), len(builds[0])))


if __name__ == "__main__":
    main()
