"""Build and gate 1.1.0-beta.45-AgriCraftIrrigationGuard from the beta.44 release JAR.

    python3 beta45-build.py --jdk8 <jdk8 home> --jdk21 <jdk21 home> --libs <dir> --out <output.jar>

--libs: tools/ci/fetch_build_libs.sh (beta.45 adds AgriCraft-1.7.10-1.5.0.jar, the last upstream 1.7.10 release;
the pack runs the 1.5.2-evernife-v1 fork, whose irrigation blocks are the same five).

Problem (2026-10-29, a player's latest.log, beta.43): joining the pack server, the lobby (vanilla/Bukkit) works, then
the switch to the Forge backend ends with "Connection Lost - Fatally missing blocks and items":
    Found a missing id from the world AgriCraft:waterChannelFull   (and waterChannel, channelValve, waterTank, sprinkler)
    Failed to connect to server: there are 10 missing blocks and items
The client registered AgriCraft's crops, analyzer, water pads, seed storage, peripheral, fences and grate, but not the
irrigation blocks: AgriCraft's Blocks.init only creates them when config/agricraft/Configuration.cfg has
"Disable Irrigation"=false, and that player's file says true. The server has them, so FML refuses the connection.

beta.45 = beta.44 +
  AgriCraftIrrigationTransformer  (client) the only write of ConfigurationHandler.disableIrrigation goes through
                                  AgriCraftIrrigationHooks.clientIrrigationDisabled, which stores false: the client
                                  always registers the irrigation blocks (extra client blocks never fail the
                                  handshake). Registered last, after beta.43's hologram fast path
                                  (-Dakashic.agricraftIrrigation.disable=true keeps the config value).

Gates (results in reports/final-build-audit.json):
  * deterministic double build; entries: plugin, mod class, manifest, mcmod.info changed, two classes added, the rest
    byte-identical to beta.44; class major <= 52; plugin: only getASMTransformerClass differs
  * registration on the JVM verifier: client list = beta.44's plus AgriCraftIrrigationTransformer last; server unchanged
  * RealAgriCraftGate on the real AgriCraft 1.5.0 ConfigurationHandler: one instruction added (the hook between
    Configuration.getBoolean and PUTSTATIC disableIrrigation), every method verifies, a second pass is a no-op;
    beta.44, the kill-switch and the server side leave the class unchanged
  * IrrigationGate (shape, -Xverify:all): "Disable Irrigation"=true stores false with beta.45 (also after a reload),
    true with beta.44 / kill-switch / server; =false stores false everywhere; the other fields are untouched
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
BASE = REPO / "releases/v1.1.0-beta.44-Angelica240Decoration/!Akashic-ThePatcher-1.1.0-beta.44-Angelica240Decoration.jar"
BASE_SHA = "de1822940a65eef3a83297822a0fca143562dcda1afedae84cc2ce13d1064027"
OLD = "1.1.0-beta.44"
NEW = "1.1.0-beta.45"

THIRD_PARTY = {
    "AgriCraft-1.7.10-1.5.0.jar": "b1d847e0b3ef0286dcf40b5793e7a7057af79740d83797f70d45b9906f1af102",
    "launchwrapper-1.12.jar": "57f402b626d16cc2705bf2a37add7adbb074f0ca3b3102fa6e23aa303dae682f",
    "asm-all-5.0.3.jar": "09d44e6adddaab9fd89ea3164c439f71c755769fa8d3a940352ab3d2003b98ff",
}
ASM9 = ("asm-9.7.jar", "asm-tree-9.7.jar", "asm-analysis-9.7.jar", "asm-util-9.7.jar")
RUNTIME = ("launchwrapper-1.12.jar", "asm-all-5.0.3.jar", "log4j-api.jar", "log4j-core.jar", "guava-17.0.jar",
           "jopt-simple-4.5.jar")

SRC = ROOT / "src/com/akashic/thepatcher/module/agricraft"
SOURCES = [SRC / "AgriCraftIrrigationTransformer.java", SRC / "AgriCraftIrrigationHooks.java"]
EXPECTED_ADDED = sorted([
    "com/akashic/thepatcher/module/agricraft/AgriCraftIrrigationHooks.class",
    "com/akashic/thepatcher/module/agricraft/AgriCraftIrrigationTransformer.class",
])
PLUGIN = "com/akashic/thepatcher/core/AkashicThePatcherLoadingPlugin.class"
MOD = "com/akashic/thepatcher/AkashicThePatcherMod.class"
MANIFEST = "META-INF/MANIFEST.MF"
MCMOD = "mcmod.info"
EXPECTED_CHANGED = sorted([PLUGIN, MOD, MANIFEST, MCMOD])
NEW_NAME = "com.akashic.thepatcher.module.agricraft.AgriCraftIrrigationTransformer"
DISABLE = "-Dakashic.agricraftIrrigation.disable=true"


def sha(data):
    return hashlib.sha256(data).hexdigest()


def major(data):
    return struct.unpack_from(">H", data, 6)[0]


def readzip(path):
    with zipfile.ZipFile(str(path)) as z:
        assert z.testzip() is None
        return {n: z.read(n) for n in z.namelist() if not n.endswith("/")}


def make_zip(content, target):
    stamp = (2026, 10, 29, 12, 0, 0)
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
    javac(args, "-cp", asm9, "-d", tools, HERE / "PatchThePatcherVersion.java", HERE / "PatchBeta45Classes.java")
    java21 = pathlib.Path(args.jdk21) / "bin/java"
    run([java21, "-Xverify:all", "-cp", cp(tools, asm9), "com.akashic.compatlab.tools.PatchThePatcherVersion",
         BASE, OLD, NEW, work / "mod/AkashicThePatcherMod.class"], "THEPATCHER_VERSION_PATCH PASS")
    run([java21, "-Xverify:all", "-cp", cp(tools, asm9), "com.akashic.compatlab.tools.PatchBeta45Classes",
         BASE, work / "plugin/AkashicThePatcherLoadingPlugin.class"], "BETA45_PATCH PASS")

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
    javac(args, "-cp", cp(runtime, jar, stubs), "-d", test,
          *(ROOT / "test" / n for n in ("TransformingLoader.java", "RegistrationGate.java", "IrrigationGate.java",
                                        "RealAgriCraftGate.java")))
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
    for label, version_jar in (("beta44", BASE), ("beta45", jar)):
        for side in ("CLIENT", "SERVER"):
            text = run([java8, "-Xverify:all", "-Dstub.side=" + side, "-cp", cp(test, version_jar, stubs, runtime),
                        "RegistrationGate"], "REGISTRATION side=" + side)
            lists[(label, side)] = re.search(r"list=\[(.*)\]", text).group(1).split(", ")
            report["registration.%s.%s" % (label, side)] = re.search(r"REGISTRATION.*", text).group(0)
    assert lists[("beta45", "CLIENT")] == lists[("beta44", "CLIENT")] + [NEW_NAME], lists[("beta45", "CLIENT")]
    assert lists[("beta45", "SERVER")] == lists[("beta44", "SERVER")]
    assert NEW_NAME not in lists[("beta45", "SERVER")]

    # The real AgriCraft 1.7.10 ConfigurationHandler.
    agricraft = libs / "AgriCraft-1.7.10-1.5.0.jar"
    cases = [("beta45", jar, [], "patched"),
             ("beta44", BASE, [], "untouched"),
             ("beta45.disabled", jar, [DISABLE], "untouched"),
             ("beta45.server", jar, ["-Dstub.side=SERVER"], "untouched")]
    for label, version_jar, flags, expect in cases:
        text = run([java8, "-Xverify:all"] + flags + ["-cp", cp(test, version_jar, stubs, runtime),
                    "RealAgriCraftGate", agricraft, expect], "REAL_AGRICRAFT PASS expect=" + expect)
        report["real_agricraft." + label] = lines(text, "A ", "REAL_AGRICRAFT", "[Akashic-ThePatcher]")
    assert any("irrigation guard applied" in l for l in report["real_agricraft.beta45"])

    # Behaviour, on the shape: "Disable Irrigation"=true in the client's config.
    cases = [("beta45.config-true", jar, ["-Dshape.disableIrrigation=true"], "false", 1),
             ("beta45.config-true.warehouse", jar, ["-Dshape.disableIrrigation=true",
                                                    "-Dshape.disableSeedWarehouse=true"], "false", 1),
             ("beta45.config-false", jar, [], "false", 1),
             ("beta44.config-true", BASE, ["-Dshape.disableIrrigation=true"], "true", 0),
             ("beta45.disabled.config-true", jar, [DISABLE, "-Dshape.disableIrrigation=true"], "true", 0),
             ("beta45.server.config-true", jar, ["-Dstub.side=SERVER", "-Dshape.disableIrrigation=true"], "true", 0)]
    for label, version_jar, flags, expect, transformed in cases:
        text = run([java8, "-Xverify:all"] + flags + ["-cp", cp(test, version_jar, stubs, runtime),
                    "IrrigationGate", shapes, expect],
                   "IRRIGATION_GATE PASS expect=%s transformed=%d" % (expect, transformed))
        report["irrigation." + label] = lines(text, "A ", "IRRIGATION_GATE", "[Akashic-ThePatcher]")
    warnings = [l for l in report["irrigation.beta45.config-true"] if "\"Disable Irrigation\"=true; ignored" in l]
    assert len(warnings) == 1, report["irrigation.beta45.config-true"]
    assert not any("ignored on the client" in l for l in report["irrigation.beta45.config-false"])
    return report


def main():
    parser = argparse.ArgumentParser()
    for name in ("--jdk8", "--jdk21", "--libs", "--out"):
        parser.add_argument(name, required=True)
    parser.add_argument("--report", default=str(ROOT / "reports/final-build-audit.json"))
    args = parser.parse_args()

    assert sha(BASE.read_bytes()) == BASE_SHA, "unexpected beta.44 base"
    builds = []
    for _ in range(2):
        with tempfile.TemporaryDirectory(prefix="beta45-") as tmp:
            builds.append(build(args, pathlib.Path(tmp)))
    assert builds[0] == builds[1], "non-deterministic build"
    out = pathlib.Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_bytes(builds[0])
    with tempfile.TemporaryDirectory(prefix="beta45-gates-") as tmp:
        report = gates(args, out.resolve(), pathlib.Path(tmp))
    report = dict({
        "version": NEW,
        "base": {"path": str(BASE.relative_to(REPO)), "sha256": BASE_SHA},
        "output": {"name": out.name, "sha256": sha(builds[0]), "bytes": len(builds[0])},
        "deterministic_rebuild": "PASS",
    }, **report)
    pathlib.Path(args.report).parent.mkdir(parents=True, exist_ok=True)
    pathlib.Path(args.report).write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print("BETA45_BUILD PASS sha256=%s bytes=%d" % (sha(builds[0]), len(builds[0])))


if __name__ == "__main__":
    main()
