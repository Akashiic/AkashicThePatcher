"""Build and gate 1.1.0-beta.46-ProjectorOffhandGuard from the beta.45 release JAR.

    python3 beta46-build.py --jdk8 <jdk8 home> --jdk21 <jdk21 home> --libs <dir> --out <output.jar>

--libs: tools/ci/fetch_build_libs.sh (beta.46 adds the pack's Immersive Petroleum backport, 0.1.0-alpha.10 catalyst
WIP, the build in use, from the pre-release of the Akashiic/ImmersivePetroleum fork).

Problem (crash report 2026-10-29 21:09, beta.43, Thermos server through the proxy, Backhand 1.8.13 installed):
    java.lang.ArrayIndexOutOfBoundsException: 36
        at flaxbeard.immersivepetroleum.client.projector.ProjectorClient.selectedView(ProjectorClient.java:50)
        at flaxbeard.immersivepetroleum.client.projector.ProjectorClient.overlay(ProjectorClient.java:89)
        at net.minecraftforge.client.GuiIngameForge.renderHUDText(GuiIngameForge.java:699)
selectedView is "return VIEWS[mc.thePlayer.inventory.currentItem]" with VIEWS = new ProjectorView[9]. Backhand appends
the offhand as slot 36 of mainInventory and points currentItem at it while it runs an offhand action; the HUD text
event in that window indexes VIEWS[36].

beta.46 = beta.45 +
  ProjectorSlotTransformer  (client) the single AALOAD of ProjectorClient.selectedView becomes
                            ProjectorSlotHooks.view(VIEWS, slot) + CHECKCAST ProjectorView: null (no projection held,
                            which overlay already handles) for a slot outside the array. Registered last, after
                            beta.45's AgriCraft irrigation guard (-Dakashic.projectorSlotGuard.disable=true keeps the
                            original lookup).

Gates (results in reports/final-build-audit.json):
  * deterministic double build; entries: plugin, mod class, manifest, mcmod.info changed, two classes added, the rest
    byte-identical to beta.45; class major <= 52; plugin: only getASMTransformerClass differs
  * registration on the JVM verifier: client list = beta.45's plus ProjectorSlotTransformer last; server unchanged
  * RealProjectorGate on the real ProjectorClient of the pack's Immersive Petroleum: only selectedView changes (AALOAD
    -> hook + CHECKCAST, fed by GETFIELD currentItem, followed by ARETURN), every method verifies, a second pass is a
    no-op; beta.45, the kill-switch and the server side leave the class unchanged
  * ProjectorGate (shape, -Xverify:all): slots 0..8 return their own projection and no player returns null
    everywhere; slots 36 / -1 / 9 return null with beta.46 and throw ArrayIndexOutOfBoundsException with beta.45 /
    kill-switch / server (the original crash)
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
BASE = REPO / "releases/v1.1.0-beta.45-AgriCraftIrrigationGuard/!Akashic-ThePatcher-1.1.0-beta.45-AgriCraftIrrigationGuard.jar"
BASE_SHA = "d2b5e3076881cba1c8f5d1179c75b126574a16c269dce17768bc5104a93fe035"
OLD = "1.1.0-beta.45"
NEW = "1.1.0-beta.46"

THIRD_PARTY = {
    "immersivepetroleum-0.1.0-alpha.10-catalyst-wip-1ec598d.jar":
        "d5065d03a3e4ea65b518e523a5809161c486ccd4ad5c456ffc91a9770ce1c72c",
    "launchwrapper-1.12.jar": "57f402b626d16cc2705bf2a37add7adbb074f0ca3b3102fa6e23aa303dae682f",
    "asm-all-5.0.3.jar": "09d44e6adddaab9fd89ea3164c439f71c755769fa8d3a940352ab3d2003b98ff",
}
ASM9 = ("asm-9.7.jar", "asm-tree-9.7.jar", "asm-analysis-9.7.jar", "asm-util-9.7.jar")
RUNTIME = ("launchwrapper-1.12.jar", "asm-all-5.0.3.jar", "log4j-api.jar", "log4j-core.jar", "guava-17.0.jar",
           "jopt-simple-4.5.jar")

SRC = ROOT / "src/com/akashic/thepatcher/module/immersivepetroleum"
SOURCES = [SRC / "ProjectorSlotTransformer.java", SRC / "ProjectorSlotHooks.java"]
EXPECTED_ADDED = sorted([
    "com/akashic/thepatcher/module/immersivepetroleum/ProjectorSlotHooks.class",
    "com/akashic/thepatcher/module/immersivepetroleum/ProjectorSlotTransformer.class",
])
PLUGIN = "com/akashic/thepatcher/core/AkashicThePatcherLoadingPlugin.class"
MOD = "com/akashic/thepatcher/AkashicThePatcherMod.class"
MANIFEST = "META-INF/MANIFEST.MF"
MCMOD = "mcmod.info"
EXPECTED_CHANGED = sorted([PLUGIN, MOD, MANIFEST, MCMOD])
NEW_NAME = "com.akashic.thepatcher.module.immersivepetroleum.ProjectorSlotTransformer"
DISABLE = "-Dakashic.projectorSlotGuard.disable=true"
IP_JAR = "immersivepetroleum-0.1.0-alpha.10-catalyst-wip-1ec598d.jar"


def sha(data):
    return hashlib.sha256(data).hexdigest()


def major(data):
    return struct.unpack_from(">H", data, 6)[0]


def readzip(path):
    with zipfile.ZipFile(str(path)) as z:
        assert z.testzip() is None
        return {n: z.read(n) for n in z.namelist() if not n.endswith("/")}


def make_zip(content, target):
    stamp = (2026, 10, 29, 22, 30, 0)
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
    javac(args, "-cp", asm9, "-d", tools, HERE / "PatchThePatcherVersion.java", HERE / "PatchBeta46Classes.java")
    java21 = pathlib.Path(args.jdk21) / "bin/java"
    run([java21, "-Xverify:all", "-cp", cp(tools, asm9), "com.akashic.compatlab.tools.PatchThePatcherVersion",
         BASE, OLD, NEW, work / "mod/AkashicThePatcherMod.class"], "THEPATCHER_VERSION_PATCH PASS")
    run([java21, "-Xverify:all", "-cp", cp(tools, asm9), "com.akashic.compatlab.tools.PatchBeta46Classes",
         BASE, work / "plugin/AkashicThePatcherLoadingPlugin.class"], "BETA46_PATCH PASS")

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

    java8 = pathlib.Path(args.jdk8) / "bin/java"
    report["gate_jvm"] = run([java8, "-version"]).strip().splitlines()[0]
    asm9 = cp(*(libs / j for j in ASM9))
    runtime = cp(*(libs / j for j in RUNTIME))
    stubs = compile_dir(args, ROOT / "test/stubs", work / "stubs")
    shapes = compile_dir(args, ROOT / "test/shapes", work / "shapes")
    test = work / "test"
    javac(args, "-cp", cp(runtime, jar, stubs, shapes), "-d", test,
          *(ROOT / "test" / n for n in ("TransformingLoader.java", "RegistrationGate.java", "ProjectorGate.java",
                                        "RealProjectorGate.java")))
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
    for label, version_jar in (("beta45", BASE), ("beta46", jar)):
        for side in ("CLIENT", "SERVER"):
            text = run([java8, "-Xverify:all", "-Dstub.side=" + side, "-cp", cp(test, version_jar, stubs, runtime),
                        "RegistrationGate"], "REGISTRATION side=" + side)
            lists[(label, side)] = re.search(r"list=\[(.*)\]", text).group(1).split(", ")
            report["registration.%s.%s" % (label, side)] = re.search(r"REGISTRATION.*", text).group(0)
    assert lists[("beta46", "CLIENT")] == lists[("beta45", "CLIENT")] + [NEW_NAME], lists[("beta46", "CLIENT")]
    assert lists[("beta46", "SERVER")] == lists[("beta45", "SERVER")]
    assert NEW_NAME not in lists[("beta46", "SERVER")]

    # The real ProjectorClient of the pack's Immersive Petroleum.
    petroleum = libs / IP_JAR
    cases = [("beta46", jar, [], "patched"),
             ("beta45", BASE, [], "untouched"),
             ("beta46.disabled", jar, [DISABLE], "untouched"),
             ("beta46.server", jar, ["-Dstub.side=SERVER"], "untouched")]
    for label, version_jar, flags, expect in cases:
        text = run([java8, "-Xverify:all"] + flags + ["-cp", cp(test, version_jar, stubs, runtime),
                    "RealProjectorGate", petroleum, expect], "REAL_PROJECTOR PASS expect=" + expect)
        report["real_projector." + label] = lines(text, "A ", "REAL_PROJECTOR", "[Akashic-ThePatcher]")
    assert any("projector slot guard applied" in l for l in report["real_projector.beta46"])

    # Behaviour, on the shape: Backhand's offhand slot (36) selected while the HUD text is drawn.
    cases = [("beta46", jar, [], "guarded", 1),
             ("beta45", BASE, [], "crash", 0),
             ("beta46.disabled", jar, [DISABLE], "crash", 0),
             ("beta46.server", jar, ["-Dstub.side=SERVER"], "crash", 0)]
    for label, version_jar, flags, expect, transformed in cases:
        text = run([java8, "-Xverify:all"] + flags + ["-cp", cp(test, version_jar, stubs, runtime, shapes),
                    "ProjectorGate", shapes, expect],
                   "PROJECTOR_GATE PASS expect=%s transformed=%d" % (expect, transformed))
        report["projector." + label] = lines(text, "A ", "PROJECTOR_GATE", "[Akashic-ThePatcher]")
    warnings = [l for l in report["projector.beta46"] if "selected slot 36 is outside the 9 hotbar slots" in l]
    assert len(warnings) == 1, report["projector.beta46"]
    assert not any("outside the" in l for l in report["projector.beta45"])
    return report


def main():
    parser = argparse.ArgumentParser()
    for name in ("--jdk8", "--jdk21", "--libs", "--out"):
        parser.add_argument(name, required=True)
    parser.add_argument("--report", default=str(ROOT / "reports/final-build-audit.json"))
    args = parser.parse_args()

    assert sha(BASE.read_bytes()) == BASE_SHA, "unexpected beta.45 base"
    builds = []
    for _ in range(2):
        with tempfile.TemporaryDirectory(prefix="beta46-") as tmp:
            builds.append(build(args, pathlib.Path(tmp)))
    assert builds[0] == builds[1], "non-deterministic build"
    out = pathlib.Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_bytes(builds[0])
    with tempfile.TemporaryDirectory(prefix="beta46-gates-") as tmp:
        report = gates(args, out.resolve(), pathlib.Path(tmp))
    report = dict({
        "version": NEW,
        "base": {"path": str(BASE.relative_to(REPO)), "sha256": BASE_SHA},
        "output": {"name": out.name, "sha256": sha(builds[0]), "bytes": len(builds[0])},
        "deterministic_rebuild": "PASS",
    }, **report)
    pathlib.Path(args.report).parent.mkdir(parents=True, exist_ok=True)
    pathlib.Path(args.report).write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print("BETA46_BUILD PASS sha256=%s bytes=%d" % (sha(builds[0]), len(builds[0])))


if __name__ == "__main__":
    main()
