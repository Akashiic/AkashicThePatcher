"""Build and gate 1.1.0-beta.38-AngelicaFontCompat from the beta.37 release JAR.

    python3 beta38-build.py --jdk8 <jdk8 home> --jdk21 <jdk21 home> --libs <dir> --out <output.jar> \
        [--necrotempus-fixed-jar <NecroTempus built from the source fix>]

--libs must contain (third-party, never committed; hashes are recorded in the report):
  launchwrapper-1.12.jar, asm-all-5.0.3.jar (the exact runtime of Forge 1.7.10), log4j-api.jar,
  log4j-core.jar (2.0-beta9), guava-17.0.jar, jopt-simple-4.5.jar, asm-9.7.jar, asm-tree-9.7.jar,
  asm-analysis-9.7.jar, asm-util-9.7.jar, angelica-2.2.37.jar, angelica-2.2.23.jar, angelica-2.2.18.jar,
  necrotempus-2.0.0.jar, necrotempus-2.1.0.jar.

Gates (all must pass; results in reports/final-build-audit.json):
  * deterministic double build; entries: 5 changed + 2 added; class major <= 52; every other entry byte-identical
  * the two edited core classes differ from beta.37 only in the intended methods (instruction-level diff)
  * registration (-Xverify:all): the client list gains NecroTempusAngelicaFontTransformer, the server list does not
  * NecroTempus (real 2.0.0 and 2.1.0) x Angelica 2.2.37: the bundled transformer patches the mixin and both
    providers; the result verifies, is idempotent, every @Shadow/@Redirect/@Inject resolves against the real
    BatchingFontRenderer, the providers implement FontProvider; kill-switch honoured
  * Angelica 2.2.23 / 2.2.18 and the dedicated server: NecroTempus left byte-identical
  * a NecroTempus already built against the int API (optional jar): left alone, and its mixin resolves
  * NecroTempusAngelicaFontHooks.textureId against SRG-named stubs
  * Angelica 2.2.37 whitespace decoration profile: beta.37 skips it, beta.38 installs it (verified, idempotent);
    on Angelica 2.2.18 beta.38 produces exactly the bytes beta.37 produces
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
BASE = REPO / "releases/v1.1.0-beta.37-ForestryGenomeBarrier/!Akashic-ThePatcher-1.1.0-beta.37-ForestryGenomeBarrier.jar"
BASE_SHA = "2209938684e5ae9f0f86946a2ae3612fcf2bb9528d590fd29e268eb47a3e483c"
OLD = "1.1.0-beta.37"
NEW = "1.1.0-beta.38"

THIRD_PARTY = {
    "angelica-2.2.37.jar": "2125183d9f3c11a401be61e6273e1e813a51e41d892ba035b99cdfdb83e2677b",
    "angelica-2.2.23.jar": "a7b82ca3a193f6737ebb36650f7287b883cf6985a30608c8fed0a6a9c1d4a5f0",
    "angelica-2.2.18.jar": "563d907f3035aedc7761be0f882674d6e736b63c5860e126e7af7df0d9f15c50",
    "necrotempus-2.0.0.jar": "d220dad5f270b99fd6ad8a42dcdb1d092eb97412f5c4d888aa072ea3fecc374c",
    "necrotempus-2.1.0.jar": "a34a021fb88a4d3ef1806a9304e1a507ee1535e0e8203d1f5ef43bac4b842b9a",
    "launchwrapper-1.12.jar": "57f402b626d16cc2705bf2a37add7adbb074f0ca3b3102fa6e23aa303dae682f",
    "asm-all-5.0.3.jar": "09d44e6adddaab9fd89ea3164c439f71c755769fa8d3a940352ab3d2003b98ff",
}
ASM9 = ("asm-9.7.jar", "asm-tree-9.7.jar", "asm-analysis-9.7.jar", "asm-util-9.7.jar")
RUNTIME = ("launchwrapper-1.12.jar", "asm-all-5.0.3.jar", "log4j-api.jar", "log4j-core.jar", "guava-17.0.jar",
           "jopt-simple-4.5.jar")

NT = "com/akashic/thepatcher/module/necrotempus/"
TRANSFORMER = NT + "NecroTempusAngelicaFontTransformer.class"
HOOKS = NT + "NecroTempusAngelicaFontHooks.class"
PLUGIN = "com/akashic/thepatcher/core/AkashicThePatcherLoadingPlugin.class"
DECORATION = "com/akashic/thepatcher/module/rgb/AngelicaDecorationPostProcessor.class"
MOD = "com/akashic/thepatcher/AkashicThePatcherMod.class"
MANIFEST = "META-INF/MANIFEST.MF"
MCMOD = "mcmod.info"
EXPECTED_CHANGED = sorted([PLUGIN, DECORATION, MOD, MANIFEST, MCMOD])
EXPECTED_ADDED = sorted([TRANSFORMER, HOOKS])
NEW_TRANSFORMER_NAME = "com.akashic.thepatcher.module.necrotempus.NecroTempusAngelicaFontTransformer"


def sha(data):
    return hashlib.sha256(data).hexdigest()


def major(data):
    return struct.unpack_from(">H", data, 6)[0]


def readzip(path):
    with zipfile.ZipFile(str(path)) as z:
        assert z.testzip() is None
        return {n: z.read(n) for n in z.namelist() if not n.endswith("/")}


def make_zip(content, target):
    stamp = (2026, 10, 25, 12, 0, 0)
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


def compile_stubs(args, target):
    javac(args, "-d", target, *sorted((ROOT / "test/stubs").rglob("*.java")))
    return target


def build(args, work):
    libs = pathlib.Path(args.libs)
    stubs = compile_stubs(args, work / "stubs")
    classes = work / "classes"
    javac(args, "-Xlint:all,-options", "-Werror",
          "-cp", cp(BASE, libs / "launchwrapper-1.12.jar", libs / "asm-all-5.0.3.jar", stubs), "-d", classes,
          ROOT / "src/com/akashic/thepatcher/module/necrotempus/NecroTempusAngelicaFontTransformer.java",
          ROOT / "src/com/akashic/thepatcher/module/necrotempus/NecroTempusAngelicaFontHooks.java")
    assert sorted(p.relative_to(classes).as_posix() for p in classes.rglob("*.class")) == EXPECTED_ADDED

    asm9 = cp(*(libs / j for j in ASM9))
    tools = work / "tools"
    javac(args, "-cp", asm9, "-d", tools, HERE / "PatchThePatcherVersion.java", HERE / "PatchBeta38Classes.java")
    java21 = pathlib.Path(args.jdk21) / "bin/java"
    run([java21, "-Xverify:all", "-cp", cp(tools, asm9), "com.akashic.compatlab.tools.PatchThePatcherVersion",
         BASE, OLD, NEW, work / "mod/AkashicThePatcherMod.class"], "THEPATCHER_VERSION_PATCH PASS")
    run([java21, "-Xverify:all", "-cp", cp(tools, asm9), "com.akashic.compatlab.tools.PatchBeta38Classes",
         BASE, work / "patched"], "BETA38_CLASS_PATCH PASS")

    content = readzip(BASE)
    for name in EXPECTED_ADDED:
        assert name not in content
        content[name] = (classes / name).read_bytes()
    content[PLUGIN] = (work / "patched" / PLUGIN).read_bytes()
    content[DECORATION] = (work / "patched" / DECORATION).read_bytes()
    content[MOD] = (work / "mod/AkashicThePatcherMod.class").read_bytes()
    for name in (MANIFEST, MCMOD):
        text = content[name].decode("utf-8")
        assert text.count(OLD) == 1, name
        content[name] = text.replace(OLD, NEW).encode("utf-8")
    out = work / "out.jar"
    make_zip(content, out)
    return out.read_bytes()


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
    stubs = compile_stubs(args, work / "stubs")
    test = work / "test"
    javac(args, "-cp", cp(runtime, jar, stubs), "-d", test,
          ROOT / "test/NecroTempusFontGate.java", ROOT / "test/RegistrationGate.java",
          ROOT / "test/DecorationGate.java", ROOT / "test/HooksGate.java")
    javac(args, "-cp", asm9, "-d", test / "asm9", ROOT / "test/MethodDiff.java")

    # The edited core classes: only the intended methods differ from beta.37.
    for entry, expected in ((PLUGIN, "[1 getASMTransformerClass()[Ljava/lang/String;]"),
                            (DECORATION, "[10 hasEndpointFix(Lorg/objectweb/asm/tree/MethodNode;)Z, 8 <clinit>()V]")):
        old_file, new_file = work / "old.class", work / "new.class"
        old_file.write_bytes(base[entry])
        new_file.write_bytes(new[entry])
        text = run([java8, "-cp", cp(test / "asm9", asm9), "MethodDiff", old_file, new_file], "METHOD_DIFF")
        assert ("differing=" + expected) in text, text
        report["method_diff." + entry] = text.strip()

    # Registration, on the real JVM verifier.
    lists = {}
    for label, version_jar in (("beta37", BASE), ("beta38", jar)):
        for side in ("CLIENT", "SERVER"):
            text = run([java8, "-Xverify:all", "-Dstub.side=" + side, "-cp", cp(test, version_jar, stubs, runtime),
                        "RegistrationGate"], "REGISTRATION side=" + side)
            lists[(label, side)] = re.search(r"list=\[(.*)\]", text).group(1).split(", ")
            report["registration.%s.%s" % (label, side)] = re.search(r"REGISTRATION.*", text).group(0)
    client37, client38 = lists[("beta37", "CLIENT")], lists[("beta38", "CLIENT")]
    assert NEW_TRANSFORMER_NAME in client38 and NEW_TRANSFORMER_NAME not in client37
    assert [n for n in client38 if n != NEW_TRANSFORMER_NAME] == client37
    assert lists[("beta38", "SERVER")] == lists[("beta37", "SERVER")]
    assert NEW_TRANSFORMER_NAME not in lists[("beta38", "SERVER")]

    # NecroTempus x Angelica.
    gate_cp = cp(test, jar, stubs, runtime)
    cases = [("necrotempus-2.0.0.jar", "angelica-2.2.37.jar", "patch", ""),
             ("necrotempus-2.1.0.jar", "angelica-2.2.37.jar", "patch", ""),
             ("necrotempus-2.0.0.jar", "angelica-2.2.23.jar", "untouched", ""),
             ("necrotempus-2.1.0.jar", "angelica-2.2.23.jar", "untouched", ""),
             ("necrotempus-2.1.0.jar", "angelica-2.2.18.jar", "untouched", ""),
             ("necrotempus-2.1.0.jar", "angelica-2.2.37.jar", "untouched", "-Dstub.side=SERVER")]
    for necro, angelica, expect, flag in cases:
        cmd = [java8, "-Xverify:all"] + ([flag] if flag else []) + ["-cp", gate_cp, "NecroTempusFontGate",
                                                                    libs / necro, libs / angelica, expect]
        text = run(cmd, "NT_GATE PASS expect=" + expect)
        key = "necrotempus.%s+%s%s" % (necro[12:-4], angelica[9:-4], ".server" if flag else "")
        report[key] = [l.strip() for l in text.splitlines() if l.startswith(("PATCHED", "NT_GATE", "  mixin", "  provider", "  redirect"))]
    if args.necrotempus_fixed_jar:
        fixed = pathlib.Path(args.necrotempus_fixed_jar)
        text = run([java8, "-Xverify:all", "-cp", gate_cp, "NecroTempusFontGate", fixed, libs / "angelica-2.2.37.jar",
                    "fixed"], "NT_GATE PASS expect=fixed")
        report["necrotempus.source-fix+2.2.37"] = {"jar_sha256": sha(fixed.read_bytes()),
                                                   "result": [l.strip() for l in text.splitlines() if l.startswith(("NT_GATE", "  mixin"))]}

    text = run([java8, "-Xverify:all", "-cp", gate_cp, "HooksGate"], "HOOKS PASS")
    report["hooks"] = "PASS"

    # Whitespace decoration profile.
    for label, version_jar in (("beta37", BASE), ("beta38", jar)):
        for angelica in ("2.2.37", "2.2.18"):
            out = work / ("decoration-%s-%s.class" % (label, angelica))
            text = run([java8, "-Xverify:all", "-cp", cp(test, version_jar, stubs, runtime), "DecorationGate",
                        libs / ("angelica-%s.jar" % angelica), out], "DECORATION")
            report["decoration.%s.%s" % (label, angelica)] = [l.strip() for l in text.splitlines()
                                                             if "DECORATION" in l or "whitespace" in l]
    assert "changed=false" in report["decoration.beta37.2.2.37"][-1]
    assert any("unsupported drawString fingerprint e79b0611" in l for l in report["decoration.beta37.2.2.37"])
    assert "changed=true endpointFix=true" in report["decoration.beta38.2.2.37"][-1]
    assert any("endpoint fix installed" in l for l in report["decoration.beta38.2.2.37"])
    old18 = (work / "decoration-beta37-2.2.18.class").read_bytes()
    new18 = (work / "decoration-beta38-2.2.18.class").read_bytes()
    assert old18 == new18, "Angelica 2.2.18 result changed"
    report["decoration.2.2.18_identical_to_beta37"] = sha(new18)
    again = work / "decoration-beta38-2.2.37-again.class"
    text = run([java8, "-Xverify:all", "-cp", cp(test, jar, stubs, runtime), "DecorationGate",
                work / "decoration-beta38-2.2.37.class", again], "DECORATION changed=false endpointFix=true")
    report["decoration.beta38.2.2.37.idempotent"] = "PASS"
    report["decoration.beta38.2.2.37.sha256"] = sha((work / "decoration-beta38-2.2.37.class").read_bytes())
    return report


def main():
    parser = argparse.ArgumentParser()
    for name in ("--jdk8", "--jdk21", "--libs", "--out"):
        parser.add_argument(name, required=True)
    parser.add_argument("--necrotempus-fixed-jar")
    parser.add_argument("--report", default=str(ROOT / "reports/final-build-audit.json"))
    args = parser.parse_args()

    assert sha(BASE.read_bytes()) == BASE_SHA, "unexpected beta.37 base"
    builds = []
    for _ in range(2):
        with tempfile.TemporaryDirectory(prefix="beta38-") as tmp:
            builds.append(build(args, pathlib.Path(tmp)))
    assert builds[0] == builds[1], "non-deterministic build"
    out = pathlib.Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_bytes(builds[0])
    with tempfile.TemporaryDirectory(prefix="beta38-gates-") as tmp:
        report = gates(args, out.resolve(), pathlib.Path(tmp))
    report = dict({
        "version": NEW,
        "base": {"path": str(BASE.relative_to(REPO)), "sha256": BASE_SHA},
        "output": {"name": out.name, "sha256": sha(builds[0]), "bytes": len(builds[0])},
        "deterministic_rebuild": "PASS",
    }, **report)
    pathlib.Path(args.report).write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print("BETA38_BUILD PASS sha256=%s bytes=%d" % (sha(builds[0]), len(builds[0])))


if __name__ == "__main__":
    main()
