"""Build and gate 1.1.0-beta.41-RenderCrashRecovery from the beta.40 release JAR.

    python3 beta41-build.py --jdk8 <jdk8 home> --jdk21 <jdk21 home> --libs <dir> --out <output.jar>

--libs: tools/ci/fetch_build_libs.sh (beta.40's inputs plus coretweaks-0.3.4.7-GTNH.jar and
fastutil-core-8.5.12.jar, which the real Angelica 2.2.37 GbufferPrograms needs to initialise).

Crash reports of 2026-10-25 (pack with DragonAPI V33b, ChromatiCraft V33a, Waystones-X 1.0.15 and its
"Waystones Modernity Textures" pack selected, Angelica 2.2.37, CoreTweaks 0.3.4.7-GTNH crash handler):
  10:19 / 18:27  ClassCastException WaystonesAlternateResourcePack -> AbstractResourcePack in DragonAPI's
                 ReikaImageLoader.getImageFromResourcePack (ChromatiCraft TESRs, world and creative tab);
  10:21 / 10:22  IllegalStateException "GbufferPrograms in weird state ... blockEntities = true": CoreTweaks
                 kept the game running after 10:19, but nothing reset Iris' block-entity phase, so every
                 block outline crashed again.
beta.41 adds two client transformers (registered after NecroTempus' in AkashicThePatcherLoadingPlugin):
  DragonApiResourcePackTransformer     DragonAPI skips packs that are not AbstractResourcePack
                                       (-Dakashic.dragonApiResourcePack.disable=true);
  CoreTweaksCrashRecoveryTransformer   CoreTweaks' CrashHandler.resetState() also runs Angelica's
                                       RenderRecovery.resetAfterCrash() and resets GbufferPrograms
                                       (-Dakashic.crashRecoveryBridge.disable=true;
                                       -Dakashic.crashRecoveryBridge.angelicaReset=false keeps only the reset).

Gates (results in reports/final-build-audit.json):
  * deterministic double build; entries: plugin, mod class, manifest, mcmod.info changed, four classes added,
    the rest byte-identical to beta.40; class major <= 52; plugin: only getASMTransformerClass differs
  * registration on the JVM verifier: client list = beta.40's plus the two new names after NecroTempus;
    server list unchanged
  * DragonApiGate (shape of the two DragonAPI methods, -Xverify:all): beta.40 / kill-switch / server reproduce
    the ClassCastException; beta.41 skips the foreign pack and keeps the real pack's texture and dye colours
  * CrashRecoveryGate (real Angelica 2.2.37 GbufferPrograms): the 10:21 message is reproduced, then after
    CoreTweaks' resetState() beta.41 clears it (RenderRecovery called once; also when it throws, when
    disabled by property, and with the real RenderRecovery that cannot run outside the game); beta.40 /
    kill-switch / server keep it
  * RealCoreTweaksGate: the transformer on the real CoreTweaks 0.3.4.7 CrashHandler adds only the hook calls
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
BASE = REPO / "releases/v1.1.0-beta.40-ItemsAtlasAngelicaGuard/!Akashic-ThePatcher-1.1.0-beta.40-ItemsAtlasAngelicaGuard.jar"
BASE_SHA = "242f07422c1a8013ca72755b8cc3765636a2cc1a3f04b98075475707b78676f2"
OLD = "1.1.0-beta.40"
NEW = "1.1.0-beta.41"

THIRD_PARTY = {
    "angelica-2.2.37.jar": "2125183d9f3c11a401be61e6273e1e813a51e41d892ba035b99cdfdb83e2677b",
    "coretweaks-0.3.4.7-GTNH.jar": "89c117aeee723df3a31d8d341626e7d080b78e50e23564c889e59b198f9bde9b",
    "fastutil-core-8.5.12.jar": "f31c20f5b06312f3d5e06e6160a32e274d819aa6cebf27528b26b6b5c0c1df19",
    "launchwrapper-1.12.jar": "57f402b626d16cc2705bf2a37add7adbb074f0ca3b3102fa6e23aa303dae682f",
    "asm-all-5.0.3.jar": "09d44e6adddaab9fd89ea3164c439f71c755769fa8d3a940352ab3d2003b98ff",
}
ASM9 = ("asm-9.7.jar", "asm-tree-9.7.jar", "asm-analysis-9.7.jar", "asm-util-9.7.jar")
RUNTIME = ("launchwrapper-1.12.jar", "asm-all-5.0.3.jar", "log4j-api.jar", "log4j-core.jar", "guava-17.0.jar",
           "jopt-simple-4.5.jar")

SRC = ROOT / "src/com/akashic/thepatcher/module"
SOURCES = [SRC / "dragonapi/DragonApiResourcePackTransformer.java", SRC / "dragonapi/DragonApiResourcePackHooks.java",
           SRC / "crashrecovery/CoreTweaksCrashRecoveryTransformer.java",
           SRC / "crashrecovery/AngelicaCrashRecoveryHooks.java"]
EXPECTED_ADDED = sorted([
    "com/akashic/thepatcher/module/crashrecovery/AngelicaCrashRecoveryHooks.class",
    "com/akashic/thepatcher/module/crashrecovery/CoreTweaksCrashRecoveryTransformer.class",
    "com/akashic/thepatcher/module/dragonapi/DragonApiResourcePackHooks.class",
    "com/akashic/thepatcher/module/dragonapi/DragonApiResourcePackTransformer.class",
])
PLUGIN = "com/akashic/thepatcher/core/AkashicThePatcherLoadingPlugin.class"
MOD = "com/akashic/thepatcher/AkashicThePatcherMod.class"
MANIFEST = "META-INF/MANIFEST.MF"
MCMOD = "mcmod.info"
EXPECTED_CHANGED = sorted([PLUGIN, MOD, MANIFEST, MCMOD])
ANCHOR_NAME = "com.akashic.thepatcher.module.necrotempus.NecroTempusAngelicaFontTransformer"
NEW_NAMES = ["com.akashic.thepatcher.module.dragonapi.DragonApiResourcePackTransformer",
             "com.akashic.thepatcher.module.crashrecovery.CoreTweaksCrashRecoveryTransformer"]


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


def build(args, work):
    libs = pathlib.Path(args.libs)
    stubs = compile_dir(args, ROOT / "test/stubs", work / "stubs")
    classes = work / "classes"
    javac(args, "-Xlint:all,-options", "-Werror",
          "-cp", cp(BASE, libs / "launchwrapper-1.12.jar", libs / "asm-all-5.0.3.jar", stubs), "-d", classes, *SOURCES)
    assert sorted(p.relative_to(classes).as_posix() for p in classes.rglob("*.class")) == EXPECTED_ADDED

    asm9 = cp(*(libs / j for j in ASM9))
    tools = work / "tools"
    javac(args, "-cp", asm9, "-d", tools, HERE / "PatchThePatcherVersion.java", HERE / "PatchBeta41Classes.java")
    java21 = pathlib.Path(args.jdk21) / "bin/java"
    run([java21, "-Xverify:all", "-cp", cp(tools, asm9), "com.akashic.compatlab.tools.PatchThePatcherVersion",
         BASE, OLD, NEW, work / "mod/AkashicThePatcherMod.class"], "THEPATCHER_VERSION_PATCH PASS")
    run([java21, "-Xverify:all", "-cp", cp(tools, asm9), "com.akashic.compatlab.tools.PatchBeta41Classes",
         BASE, work / "plugin/AkashicThePatcherLoadingPlugin.class"], "BETA41_PATCH PASS")

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
    asm9 = cp(*(libs / j for j in ASM9))
    runtime = cp(*(libs / j for j in RUNTIME))
    stubs = compile_dir(args, ROOT / "test/stubs", work / "stubs")
    shapes = compile_dir(args, ROOT / "test/shapes", work / "shapes", stubs)
    recovery_stub = compile_dir(args, ROOT / "test/recovery-stub", work / "recovery-stub")
    test = work / "test"
    javac(args, "-cp", cp(runtime, jar, stubs), "-d", test,
          *(ROOT / "test" / n for n in ("TransformingLoader.java", "RegistrationGate.java", "DragonApiGate.java",
                                        "CrashRecoveryGate.java", "RealCoreTweaksGate.java")))
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
    for label, version_jar in (("beta40", BASE), ("beta41", jar)):
        for side in ("CLIENT", "SERVER"):
            text = run([java8, "-Xverify:all", "-Dstub.side=" + side, "-cp", cp(test, version_jar, stubs, runtime),
                        "RegistrationGate"], "REGISTRATION side=" + side)
            lists[(label, side)] = re.search(r"list=\[(.*)\]", text).group(1).split(", ")
            report["registration.%s.%s" % (label, side)] = re.search(r"REGISTRATION.*", text).group(0)
    client40, client41 = lists[("beta40", "CLIENT")], lists[("beta41", "CLIENT")]
    at = client40.index(ANCHOR_NAME) + 1
    assert client41 == client40[:at] + NEW_NAMES + client40[at:], client41
    assert lists[("beta41", "SERVER")] == lists[("beta40", "SERVER")]
    assert not set(NEW_NAMES) & set(lists[("beta41", "SERVER")])

    # DragonAPI x Waystones' resource pack.
    cases = [("beta41", jar, "", "fixed"),
             ("beta40", BASE, "", "broken"),
             ("beta41.disabled", jar, "-Dakashic.dragonApiResourcePack.disable=true", "broken"),
             ("beta41.server", jar, "-Dstub.side=SERVER", "broken"),
             ("beta41.logging", jar, "-Dstub.dragonapi.log=true", "fixed")]
    for label, version_jar, flag, expect in cases:
        cmd = [java8, "-Xverify:all"] + ([flag] if flag else []) + [
            "-cp", cp(test, version_jar, stubs, runtime), "DragonApiGate", shapes, expect]
        text = run(cmd, "DRAGONAPI_GATE PASS expect=" + expect)
        report["dragonapi." + label] = lines(text, "A ", "DRAGONAPI_GATE", "[Akashic-ThePatcher]")

    # CoreTweaks crash recovery x Iris GbufferPrograms (real Angelica 2.2.37 class).
    iris = cp(libs / "angelica-2.2.37.jar", libs / "fastutil-core-8.5.12.jar")
    with_stub = lambda version_jar: cp(test, version_jar, stubs, runtime, recovery_stub, iris)
    cases = [("beta41", with_stub(jar), [], "fixed", 1),
             ("beta41.recovery-throws", with_stub(jar), ["-Dstub.recovery.throw=true"], "fixed", 1),
             ("beta41.angelica-reset-off", with_stub(jar), ["-Dakashic.crashRecoveryBridge.angelicaReset=false"], "fixed", 0),
             ("beta41.coretweaks-step-fails", with_stub(jar), ["-Dstub.coretweaks.stepFails=true"], "fixed", 1),
             ("beta41.real-render-recovery", cp(test, jar, stubs, runtime, iris), [], "fixed", -1),
             ("beta41.disabled", with_stub(jar), ["-Dakashic.crashRecoveryBridge.disable=true"], "broken", 0),
             ("beta41.server", with_stub(jar), ["-Dstub.side=SERVER"], "broken", 0),
             ("beta40", with_stub(BASE), [], "broken", 0)]
    for label, classpath, flags, expect, calls in cases:
        cmd = [java8, "-Xverify:all"] + flags + ["-cp", classpath, "CrashRecoveryGate", shapes, expect, str(calls)]
        text = run(cmd, "CRASH_RECOVERY_GATE PASS expect=" + expect)
        report["crash_recovery." + label] = lines(text, "A ", "CRASH_RECOVERY_GATE", "[Akashic-ThePatcher]")
    assert any("left set by the crash: [blockEntities=true]" in l for l in report["crash_recovery.beta41"])
    assert not any("not found" in l for l in report["crash_recovery.beta41"]), "a GbufferPrograms field is missing"

    text = run([java8, "-Xverify:all", "-cp", cp(test, jar, stubs, runtime), "RealCoreTweaksGate",
                libs / "coretweaks-0.3.4.7-GTNH.jar"], "REAL_CORETWEAKS PASS")
    report["real_coretweaks"] = lines(text, "REAL_CORETWEAKS", "[Akashic-ThePatcher]")
    return report


def main():
    parser = argparse.ArgumentParser()
    for name in ("--jdk8", "--jdk21", "--libs", "--out"):
        parser.add_argument(name, required=True)
    parser.add_argument("--report", default=str(ROOT / "reports/final-build-audit.json"))
    args = parser.parse_args()

    assert sha(BASE.read_bytes()) == BASE_SHA, "unexpected beta.40 base"
    builds = []
    for _ in range(2):
        with tempfile.TemporaryDirectory(prefix="beta41-") as tmp:
            builds.append(build(args, pathlib.Path(tmp)))
    assert builds[0] == builds[1], "non-deterministic build"
    out = pathlib.Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_bytes(builds[0])
    with tempfile.TemporaryDirectory(prefix="beta41-gates-") as tmp:
        report = gates(args, out.resolve(), pathlib.Path(tmp))
    report = dict({
        "version": NEW,
        "base": {"path": str(BASE.relative_to(REPO)), "sha256": BASE_SHA},
        "output": {"name": out.name, "sha256": sha(builds[0]), "bytes": len(builds[0])},
        "deterministic_rebuild": "PASS",
    }, **report)
    pathlib.Path(args.report).write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print("BETA41_BUILD PASS sha256=%s bytes=%d" % (sha(builds[0]), len(builds[0])))


if __name__ == "__main__":
    main()
