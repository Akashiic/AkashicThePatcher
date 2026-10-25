"""Build and gate 1.1.0-beta.39-AngelicaFontMixinTrust from the beta.38 release JAR.

    python3 beta39-build.py --jdk8 <jdk8 home> --jdk21 <jdk21 home> --libs <dir> --out <output.jar> \
        [--necrotempus-fixed-jar <NecroTempus built from the source fix>]

--libs: as for beta.38 (launchwrapper-1.12, asm-all-5.0.3, log4j 2.0-beta9, guava-17.0, jopt-simple-4.5,
ASM 9.7 jars, angelica-2.2.37/2.2.23/2.2.18, necrotempus-2.0.0/2.1.0) plus
+unimixins-all-1.7.10-0.3.1.jar (the UniMixins the pack runs; provides Mixingasm).

beta.38 registered NecroTempusAngelicaFontTransformer, but UniMixins' Mixingasm removes untrusted transformers
from the chain Mixin runs on mixin classes, so in a real client the mixin was never rewritten. beta.39 only
adds @makamys.mixingasm.api.MixinSafeTransformer to that class.

Gates (results in reports/final-build-audit.json):
  * deterministic double build; entries: 4 changed (transformer, mod class, manifest, mcmod.info), the rest
    byte-identical to beta.38; class major <= 52; every method of the transformer identical to beta.38
  * Mixingasm trust rule with the real UniMixins 0.3.1 jar: beta.39 trusted, beta.38 not
  * without UniMixins on the classpath the transformer still loads and runs
  * the beta.38 NecroTempus x Angelica gates and textureId hook, re-run on the beta.39 jar
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
BASE = REPO / "releases/v1.1.0-beta.38-AngelicaFontCompat/!Akashic-ThePatcher-1.1.0-beta.38-AngelicaFontCompat.jar"
BASE_SHA = "bef913778167a2fb540b638bf8276981073eb39b014b30ca07df1d94bf2ff701"
OLD = "1.1.0-beta.38"
NEW = "1.1.0-beta.39"

THIRD_PARTY = {
    "angelica-2.2.37.jar": "2125183d9f3c11a401be61e6273e1e813a51e41d892ba035b99cdfdb83e2677b",
    "angelica-2.2.23.jar": "a7b82ca3a193f6737ebb36650f7287b883cf6985a30608c8fed0a6a9c1d4a5f0",
    "angelica-2.2.18.jar": "563d907f3035aedc7761be0f882674d6e736b63c5860e126e7af7df0d9f15c50",
    "necrotempus-2.0.0.jar": "d220dad5f270b99fd6ad8a42dcdb1d092eb97412f5c4d888aa072ea3fecc374c",
    "necrotempus-2.1.0.jar": "a34a021fb88a4d3ef1806a9304e1a507ee1535e0e8203d1f5ef43bac4b842b9a",
    "launchwrapper-1.12.jar": "57f402b626d16cc2705bf2a37add7adbb074f0ca3b3102fa6e23aa303dae682f",
    "asm-all-5.0.3.jar": "09d44e6adddaab9fd89ea3164c439f71c755769fa8d3a940352ab3d2003b98ff",
    "+unimixins-all-1.7.10-0.3.1.jar": "ad0ea4f92daf7bf7ec5c10e16258425e47929126954aea5d80df69ce26cd7318",
}
ASM9 = ("asm-9.7.jar", "asm-tree-9.7.jar", "asm-analysis-9.7.jar", "asm-util-9.7.jar")
RUNTIME = ("launchwrapper-1.12.jar", "asm-all-5.0.3.jar", "log4j-api.jar", "log4j-core.jar", "guava-17.0.jar",
           "jopt-simple-4.5.jar")

NT = "com/akashic/thepatcher/module/necrotempus/"
TRANSFORMER = NT + "NecroTempusAngelicaFontTransformer.class"
TRANSFORMER_NAME = "com.akashic.thepatcher.module.necrotempus.NecroTempusAngelicaFontTransformer"
MOD = "com/akashic/thepatcher/AkashicThePatcherMod.class"
MANIFEST = "META-INF/MANIFEST.MF"
MCMOD = "mcmod.info"
EXPECTED_CHANGED = sorted([TRANSFORMER, MOD, MANIFEST, MCMOD])
UNIMIXINS = "+unimixins-all-1.7.10-0.3.1.jar"

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
    built = sorted(p.relative_to(classes).as_posix() for p in classes.rglob("*.class"))
    assert built == sorted([TRANSFORMER, NT + "NecroTempusAngelicaFontHooks.class"]), built

    asm9 = cp(*(libs / j for j in ASM9))
    tools = work / "tools"
    javac(args, "-cp", asm9, "-d", tools, HERE / "PatchThePatcherVersion.java")
    java21 = pathlib.Path(args.jdk21) / "bin/java"
    run([java21, "-Xverify:all", "-cp", cp(tools, asm9), "com.akashic.compatlab.tools.PatchThePatcherVersion",
         BASE, OLD, NEW, work / "mod/AkashicThePatcherMod.class"], "THEPATCHER_VERSION_PATCH PASS")

    content = readzip(BASE)
    # The hooks class is recompiled from the same source; it must come out byte-identical to beta.38's.
    assert (classes / NT / "NecroTempusAngelicaFontHooks.class").read_bytes() == content[NT + "NecroTempusAngelicaFontHooks.class"]
    content[TRANSFORMER] = (classes / TRANSFORMER).read_bytes()
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
    assert set(base) == set(new)
    changed = sorted(n for n in base if base[n] != new[n])
    assert changed == EXPECTED_CHANGED, changed
    for name in changed:
        if name.endswith(".class"):
            assert major(new[name]) <= 52, name
    report.update(entries=len(new), changed=changed, inherited_identical=len(base) - len(changed))

    java8 = pathlib.Path(args.jdk8) / "bin/java"
    asm9 = cp(*(libs / j for j in ASM9))
    runtime = cp(*(libs / j for j in RUNTIME))
    stubs = compile_stubs(args, work / "stubs")
    test = work / "test"
    javac(args, "-cp", cp(runtime, jar, stubs), "-d", test,
          ROOT / "test/NecroTempusFontGate.java", ROOT / "test/HooksGate.java", ROOT / "test/NoMixingasmGate.java")
    javac(args, "-cp", libs / UNIMIXINS, "-d", test / "mixingasm", ROOT / "test/MixingasmTrustGate.java")
    javac(args, "-cp", asm9, "-d", test / "asm9", ROOT / "test/MethodDiff.java")

    # The transformer's code is beta.38's; only the class annotation changed.
    old_file, new_file = work / "old.class", work / "new.class"
    old_file.write_bytes(base[TRANSFORMER])
    new_file.write_bytes(new[TRANSFORMER])
    text = run([java8, "-cp", cp(test / "asm9", asm9), "MethodDiff", old_file, new_file], "differing=[]")
    report["method_diff." + TRANSFORMER] = text.strip()

    # Mixingasm's trust rule, with the real UniMixins jar (no FML stubs needed: only the class is instantiated).
    for label, version_jar, expect in (("beta38", BASE, "untrusted"), ("beta39", jar, "trusted")):
        text = run([java8, "-Xverify:all", "-cp", cp(test / "mixingasm", libs / UNIMIXINS, version_jar, stubs, runtime),
                    "MixingasmTrustGate", TRANSFORMER_NAME, expect], "MIXINGASM_TRUST PASS")
        report["mixingasm." + label] = text.strip().splitlines()[-1]
    text = run([java8, "-Xverify:all", "-cp", cp(test, jar, stubs, runtime), "NoMixingasmGate", TRANSFORMER_NAME],
               "NO_MIXINGASM PASS")
    report["without_unimixins"] = text.strip().splitlines()[-1]

    # beta.38's NecroTempus x Angelica gates on the new jar.
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
        report[key] = [l.strip() for l in text.splitlines() if l.startswith(("PATCHED", "NT_GATE", "  mixin", "  provider"))]
    if args.necrotempus_fixed_jar:
        fixed = pathlib.Path(args.necrotempus_fixed_jar)
        text = run([java8, "-Xverify:all", "-cp", gate_cp, "NecroTempusFontGate", fixed, libs / "angelica-2.2.37.jar",
                    "fixed"], "NT_GATE PASS expect=fixed")
        report["necrotempus.source-fix+2.2.37"] = {"jar_sha256": sha(fixed.read_bytes()),
                                                   "result": [l.strip() for l in text.splitlines() if l.startswith(("NT_GATE", "  mixin"))]}
    run([java8, "-Xverify:all", "-cp", gate_cp, "HooksGate"], "HOOKS PASS")
    report["hooks"] = "PASS"
    return report


def main():
    parser = argparse.ArgumentParser()
    for name in ("--jdk8", "--jdk21", "--libs", "--out"):
        parser.add_argument(name, required=True)
    parser.add_argument("--necrotempus-fixed-jar")
    parser.add_argument("--report", default=str(ROOT / "reports/final-build-audit.json"))
    args = parser.parse_args()

    assert sha(BASE.read_bytes()) == BASE_SHA, "unexpected beta.38 base"
    builds = []
    for _ in range(2):
        with tempfile.TemporaryDirectory(prefix="beta39-") as tmp:
            builds.append(build(args, pathlib.Path(tmp)))
    assert builds[0] == builds[1], "non-deterministic build"
    out = pathlib.Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_bytes(builds[0])
    with tempfile.TemporaryDirectory(prefix="beta39-gates-") as tmp:
        report = gates(args, out.resolve(), pathlib.Path(tmp))
    report = dict({
        "version": NEW,
        "base": {"path": str(BASE.relative_to(REPO)), "sha256": BASE_SHA},
        "output": {"name": out.name, "sha256": sha(builds[0]), "bytes": len(builds[0])},
        "deterministic_rebuild": "PASS",
    }, **report)
    pathlib.Path(args.report).write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print("BETA39_BUILD PASS sha256=%s bytes=%d" % (sha(builds[0]), len(builds[0])))


if __name__ == "__main__":
    main()
