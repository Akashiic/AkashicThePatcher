"""Build and gate 1.1.0-beta.52-HeldItemCullBox from the beta.51 release JAR.

    python3 beta52-build.py --jdk8 <jdk8 home> --jdk21 <jdk21 home> --libs <dir> --out <output.jar>

--libs: tools/ci/fetch_build_libs.sh (uses launchwrapper-1.12, asm-all-5.0.3, the ASM 9.7 jars, angelica-2.2.37 and
2.2.40).

Held 3D items vanish at the edge of the view (TEOF: Kunftig's 3D fishing rods for AkashicFishing): vanilla's
RenderGlobal.renderEntities and Angelica's CeleritasWorldRenderer.isEntityVisible cull an entity by its own box
(0.6 x 1.8 for a player), so a player just outside the view is skipped while the rod in its hand still reaches into
it; the fishing line then falls back to vanilla's line from the hand.

beta.52 = beta.51 +
  HeldItemCullTransformer (client only, registered last, after beta.51's GeckoLib animation copy)
    renderEntities: both "entity.boundingBox -> ICamera.isBoundingBoxInFrustum" sites read the box through
      HeldItemCullHooks.cullBox(entity, box)
    CeleritasWorldRenderer.isEntityVisible: the box local goes through cullBox before isInfiniteExtentsBox
    cullBox: the same box, or a copy grown by -Dakashic.heldItemCull.margin blocks (default 3) when the entity is
      living and holds an item whose IItemRenderer handles EQUIPPED; the entity's own box is never changed
    fails closed: exact descriptors and site counts (2 / 1), ASM verifier; -Dakashic.heldItemCull.disable=true
  HeldItemCullHooks (runtime side, SRG member names: the package is in ThePatcher's TransformerExclusions)
  AkashicThePatcherLoadingPlugin.getASMTransformerClass: one register call added (tools/PatchBeta52Plugin.java)

Gates (results in reports/final-build-audit.json):
  * deterministic double build; entries: plugin, mod class, manifest, mcmod.info changed, the two helditemcull classes
    added, the rest byte-identical to beta.51; class major <= 52; plugin: only getASMTransformerClass differs
  * registration on the JVM verifier: client = beta.51's list + HeldItemCullTransformer last; server = beta.51's
  * HeldItemCullGate (-Xverify:all) on shapes of 1.7.10's renderEntities culling and Angelica's isEntityVisible: a
    player just outside the view holding a 3D item is drawn by both, nobody else changes, the entities' boxes are
    untouched; beta.51 and the kill-switch draw only what is in view; a renderEntities of another shape is left alone;
    the real CeleritasWorldRenderer of Angelica 2.2.37 and 2.2.40 is patched once and verifies
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
BASE = REPO / "releases/v1.1.0-beta.51-GeckoAnimationCopy/!Akashic-ThePatcher-1.1.0-beta.51-GeckoAnimationCopy.jar"
BASE_SHA = "5e2fce966fa780f54a5405980636cb35027ea9c7b1f536482e65441d85e871f1"
OLD = "1.1.0-beta.51"
NEW = "1.1.0-beta.52"

THIRD_PARTY = {
    "asm-9.7.jar": "adf46d5e34940bdf148ecdd26a9ee8eea94496a72034ff7141066b3eea5c4e9d",
    "asm-tree-9.7.jar": "62f4b3bc436045c1acb5c3ba2d8ec556ec3369093d7f5d06c747eb04b56d52b1",
    "asm-analysis-9.7.jar": "7bc6bcbc21379948a0c8c467fb0f864206e5b818f6bc0b546872f5c9f941556f",
    "asm-util-9.7.jar": "37a6414d36641973f1af104937c95d6d921b2ddb4d612c66c5a9f2b13fc14211",
    "launchwrapper-1.12.jar": "57f402b626d16cc2705bf2a37add7adbb074f0ca3b3102fa6e23aa303dae682f",
    "asm-all-5.0.3.jar": "09d44e6adddaab9fd89ea3164c439f71c755769fa8d3a940352ab3d2003b98ff",
    "angelica-2.2.37.jar": "2125183d9f3c11a401be61e6273e1e813a51e41d892ba035b99cdfdb83e2677b",
    "angelica-2.2.40.jar": "c91adf2712aa65f8d99f5378a3027f538ab8e027c0b5b2aa261cc632ae937c54",
}
ASM9 = ("asm-9.7.jar", "asm-tree-9.7.jar", "asm-analysis-9.7.jar", "asm-util-9.7.jar")
RUNTIME = ("launchwrapper-1.12.jar", "asm-all-5.0.3.jar")

PKG = "com/akashic/thepatcher/module/helditemcull/"
SOURCES = [ROOT / "src" / PKG / "HeldItemCullTransformer.java", ROOT / "src" / PKG / "HeldItemCullHooks.java"]
EXPECTED_ADDED = sorted([PKG + "HeldItemCullHooks.class", PKG + "HeldItemCullTransformer.class",
                         PKG + "HeldItemCullTransformer$Names.class"])
PLUGIN = "com/akashic/thepatcher/core/AkashicThePatcherLoadingPlugin.class"
MOD = "com/akashic/thepatcher/AkashicThePatcherMod.class"
MANIFEST = "META-INF/MANIFEST.MF"
MCMOD = "mcmod.info"
EXPECTED_CHANGED = sorted([MANIFEST, MOD, PLUGIN, MCMOD])
TRANSFORMER = "com.akashic.thepatcher.module.helditemcull.HeldItemCullTransformer"
KILL = ["-Dakashic.heldItemCull.disable=true"]
ANGELICA = ("angelica-2.2.37.jar", "angelica-2.2.40.jar")


def sha(data):
    return hashlib.sha256(data).hexdigest()


def major(data):
    return struct.unpack_from(">H", data, 6)[0]


def readzip(path):
    with zipfile.ZipFile(str(path)) as z:
        assert z.testzip() is None
        return {n: z.read(n) for n in z.namelist() if not n.endswith("/")}


def make_zip(content, target):
    stamp = (2026, 11, 5, 12, 0, 0)
    with zipfile.ZipFile(str(target), "w", zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        for name in sorted(content):
            info = zipfile.ZipInfo(name, stamp)
            info.create_system = 3
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o644 << 16
            z.writestr(info, content[name], compress_type=zipfile.ZIP_DEFLATED, compresslevel=9)
    with zipfile.ZipFile(str(target)) as z:
        assert z.testzip() is None


def run(cmd, expect=None):
    proc = subprocess.run([str(c) for c in cmd], stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                          timeout=1800, env={"PATH": "/usr/bin:/bin"})
    text = proc.stdout.decode("utf-8", "replace")
    text = "\n".join(l for l in text.splitlines() if not l.startswith("Picked up JAVA_TOOL_OPTIONS"))
    if proc.returncode != 0 or (expect and expect not in text):
        raise SystemExit("FAILED: %s\n%s" % (" ".join(map(str, cmd)), text[-4000:]))
    return text


def cp(*parts):
    return ":".join(str(p) for p in parts)


def libjars(args, names):
    return cp(*(pathlib.Path(args.libs) / j for j in names))


def javac(args, *rest):
    return run([pathlib.Path(args.jdk21) / "bin/javac", "--release", "8", "-nowarn", "-encoding", "UTF-8"] + list(rest))


def compile_dir(args, source_dir, target, *classpath):
    javac(args, *(["-cp", cp(*classpath)] if classpath else []), "-d", target, *sorted(source_dir.rglob("*.java")))
    return target


def build(args, work):
    classes = work / "classes"
    stubs = compile_dir(args, ROOT / "test/stubs", work / "stubs")
    fml_remapper = compile_dir(args, ROOT / "stubs", work / "fml-remapper")
    javac(args, "-Xlint:all,-options", "-Werror", "-cp", cp(BASE, libjars(args, RUNTIME), stubs, fml_remapper), "-d", classes,
          *SOURCES)
    built = sorted(p.relative_to(classes).as_posix() for p in classes.rglob("*.class"))
    assert built == EXPECTED_ADDED, built

    tools = work / "tools"
    javac(args, "-cp", libjars(args, ASM9), "-d", tools, HERE / "PatchThePatcherVersion.java", HERE / "PatchBeta52Plugin.java")
    java21 = pathlib.Path(args.jdk21) / "bin/java"
    run([java21, "-Xverify:all", "-cp", cp(tools, libjars(args, ASM9)), "com.akashic.compatlab.tools.PatchThePatcherVersion",
         BASE, OLD, NEW, work / "mod/AkashicThePatcherMod.class"], "THEPATCHER_VERSION_PATCH PASS")
    run([java21, "-Xverify:all", "-cp", cp(tools, libjars(args, ASM9)), "com.akashic.compatlab.tools.PatchBeta52Plugin",
         BASE, work / "plugin/AkashicThePatcherLoadingPlugin.class"], "BETA52_PATCH PASS")

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
    runtime = libjars(args, RUNTIME)
    fml = compile_dir(args, ROOT / "test/fml-stubs", work / "fml")
    stubs = compile_dir(args, ROOT / "test/stubs", work / "stubs")
    shapes = compile_dir(args, ROOT / "test/shapes", work / "shapes", stubs)
    other = compile_dir(args, ROOT / "test/shapes-other", work / "shapes-other", stubs)
    test = work / "test"
    javac(args, "-cp", cp(runtime, fml, stubs), "-d", test,
          *(ROOT / "test" / n for n in ("TransformingLoader.java", "HeldItemCullGate.java", "RegistrationGate.java")))
    javac(args, "-cp", libjars(args, ASM9), "-d", test / "asm9", ROOT / "test/MethodDiff.java")

    old_file, new_file = work / "old.class", work / "new.class"
    old_file.write_bytes(base[PLUGIN])
    new_file.write_bytes(new[PLUGIN])
    text = run([java8, "-cp", cp(test / "asm9", libjars(args, ASM9)), "MethodDiff", old_file, new_file], "METHOD_DIFF")
    differing = re.search(r"differing=\[(.*?)\]", text).group(1)
    assert re.fullmatch(r"\d+ getASMTransformerClass\(\)\[Ljava/lang/String;", differing), text
    report["method_diff." + PLUGIN] = text.strip()

    lists = {}
    for label, version_jar in (("beta51", BASE), ("beta52", jar)):
        for side in ("CLIENT", "SERVER"):
            text = run([java8, "-Xverify:all", "-Dstub.side=" + side, "-cp", cp(test, version_jar, fml, runtime),
                        "RegistrationGate"], "REGISTRATION side=" + side)
            lists[(label, side)] = re.search(r"list=\[(.*)\]", text).group(1).split(", ")
            report["registration.%s.%s" % (label, side)] = re.search(r"REGISTRATION.*", text).group(0)
    assert lists[("beta52", "CLIENT")] == lists[("beta51", "CLIENT")] + [TRANSFORMER], lists[("beta52", "CLIENT")]
    assert lists[("beta52", "SERVER")] == lists[("beta51", "SERVER")], lists[("beta52", "SERVER")]

    gate_cp = lambda version_jar: cp(test, version_jar, stubs, fml, runtime)
    cases = [("beta52", jar, [], shapes, ["patched"]),
             ("beta51", BASE, [], shapes, ["unpatched"]),
             ("beta52.disabled", jar, KILL, shapes, ["unpatched"]),
             ("beta52.other-shape", jar, [], other, ["unpatched", "other"])]
    for label, version_jar, flags, shape_dir, expect in cases:
        text = run([java8, "-Xverify:all"] + flags + ["-cp", gate_cp(version_jar), "HeldItemCullGate", shape_dir] + expect,
                   "HELD_ITEM_CULL_GATE PASS expect=" + expect[0])
        report["held_item_cull_gate." + label] = lines(text, "GATE", "HELD_ITEM_CULL_GATE", "[Akashic-ThePatcher]")
    if "skipped" not in " ".join(report["held_item_cull_gate.beta52.other-shape"]):
        raise SystemExit("FAILED: the other shape was not reported as skipped")
    for name in sorted(ANGELICA):
        text = run([java8, "-Xverify:all", "-cp", gate_cp(jar), "HeldItemCullGate", "real", libs / name],
                   "HELD_ITEM_CULL_GATE PASS real=" + name)
        report["held_item_cull_gate.real." + name] = lines(text, "HELD_ITEM_CULL_GATE", "[Akashic-ThePatcher]")
    return report


def main():
    parser = argparse.ArgumentParser()
    for name in ("--jdk8", "--jdk21", "--libs", "--out"):
        parser.add_argument(name, required=True)
    parser.add_argument("--report", default=str(ROOT / "reports/final-build-audit.json"))
    args = parser.parse_args()

    assert sha(BASE.read_bytes()) == BASE_SHA, "unexpected beta.51 base"
    builds = []
    for _ in range(2):
        with tempfile.TemporaryDirectory(prefix="beta52-") as tmp:
            builds.append(build(args, pathlib.Path(tmp)))
    assert builds[0] == builds[1], "non-deterministic build"
    out = pathlib.Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_bytes(builds[0])
    with tempfile.TemporaryDirectory(prefix="beta52-gates-") as tmp:
        report = gates(args, out.resolve(), pathlib.Path(tmp))
    report = dict({
        "version": NEW,
        "base": {"path": str(BASE.relative_to(REPO)), "sha256": BASE_SHA},
        "output": {"name": out.name, "sha256": sha(builds[0]), "bytes": len(builds[0])},
        "deterministic_rebuild": "PASS",
    }, **report)
    pathlib.Path(args.report).parent.mkdir(parents=True, exist_ok=True)
    pathlib.Path(args.report).write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print("BETA52_BUILD PASS sha256=%s bytes=%d" % (sha(builds[0]), len(builds[0])))


if __name__ == "__main__":
    main()
