"""Build and gate 1.1.0-beta.44-Angelica240Decoration from the beta.43 release JAR.

    python3 beta44-build.py --jdk8 <jdk8 home> --jdk21 <jdk21 home> --libs <dir> --out <output.jar>

--libs: tools/ci/fetch_build_libs.sh (beta.44 adds angelica-2.2.40.jar).

Problem (2026-10-29): after updating Angelica to 2.2.40, struck-through/underlined runs of spaces in the NecroTempus tab
list (the header separators) are cut short. The pack's log says
    Angelica whitespace decoration fix skipped: unsupported drawString fingerprint dba827d8...
Angelica 2.2.40 still has the bug beta.38 fixed for 2.2.37 (the whitespace branch of BatchingFontRenderer.drawString
advances curX and continues without moving underlineEndX/strikethroughEndX), but drawString was rewritten (FormatCodes,
whitespaceStep, new locals), so its fingerprint and local layout changed and the fail-closed post-processor skips it.

beta.44 = beta.43 +
  AngelicaDecorationPostProcessor  PROFILES gains 2.2.40 (dba827d8..., curX=12, underlineEndX=46,
                                   strikethroughEndX=49); hasEndpointFix(MethodNode) recognises that layout
                                   (idempotency). Bytecode-level edit (tools/PatchBeta44Classes.java), like beta.38.

Gates (results in reports/final-build-audit.json):
  * deterministic double build; entries: decoration post-processor, mod class, manifest, mcmod.info changed, the
    rest byte-identical to beta.43; class major <= 52; decoration: only <clinit> and hasEndpointFix(MethodNode) differ
  * Angelica 2.2.40: beta.43 skips (the pack's warning, same fingerprint); beta.44 installs the endpoint fix, every
    method verifies, the fix sits in the whitespace branch (PlacementGate), a second run is a no-op
  * Angelica 2.2.37 / 2.2.23 / 2.2.18: beta.44 produces exactly the bytes beta.43 produces
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
BASE = REPO / "releases/v1.1.0-beta.43-HologramFastPath/!Akashic-ThePatcher-1.1.0-beta.43-HologramFastPath.jar"
BASE_SHA = "38f97b919e59011c9ee4fdf0829e958c3b5a3828e68aa25177dbad5b685da59a"
OLD = "1.1.0-beta.43"
NEW = "1.1.0-beta.44"

THIRD_PARTY = {
    "asm-all-5.0.3.jar": "09d44e6adddaab9fd89ea3164c439f71c755769fa8d3a940352ab3d2003b98ff",
    "angelica-2.2.40.jar": "c91adf2712aa65f8d99f5378a3027f538ab8e027c0b5b2aa261cc632ae937c54",
    "angelica-2.2.37.jar": "2125183d9f3c11a401be61e6273e1e813a51e41d892ba035b99cdfdb83e2677b",
    "angelica-2.2.23.jar": "a7b82ca3a193f6737ebb36650f7287b883cf6985a30608c8fed0a6a9c1d4a5f0",
    "angelica-2.2.18.jar": "563d907f3035aedc7761be0f882674d6e736b63c5860e126e7af7df0d9f15c50",
}
ASM9 = ("asm-9.7.jar", "asm-tree-9.7.jar", "asm-analysis-9.7.jar", "asm-util-9.7.jar")
RUNTIME = ("launchwrapper-1.12.jar", "asm-all-5.0.3.jar", "log4j-api.jar", "log4j-core.jar", "guava-17.0.jar",
           "jopt-simple-4.5.jar")

DECORATION = "com/akashic/thepatcher/module/rgb/AngelicaDecorationPostProcessor.class"
MOD = "com/akashic/thepatcher/AkashicThePatcherMod.class"
MANIFEST = "META-INF/MANIFEST.MF"
MCMOD = "mcmod.info"
EXPECTED_CHANGED = sorted([DECORATION, MOD, MANIFEST, MCMOD])
FINGERPRINT_2_2_40 = "dba827d81374f97b0de687e6fdc9e11b45b6a9ff79d2255cb73c12a74b1489a2"
LAYOUT_2_2_40 = ("12", "46", "49")


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


def compile_tools(args, work):
    asm9 = cp(*(pathlib.Path(args.libs) / j for j in ASM9))
    tools = work / "tools"
    javac(args, "-cp", asm9, "-d", tools, HERE / "PatchThePatcherVersion.java", HERE / "PatchBeta44Classes.java")
    return tools, asm9


def build(args, work):
    tools, asm9 = compile_tools(args, work)
    java21 = pathlib.Path(args.jdk21) / "bin/java"
    run([java21, "-Xverify:all", "-cp", cp(tools, asm9), "com.akashic.compatlab.tools.PatchThePatcherVersion",
         BASE, OLD, NEW, work / "mod/AkashicThePatcherMod.class"], "THEPATCHER_VERSION_PATCH PASS")
    run([java21, "-Xverify:all", "-cp", cp(tools, asm9), "com.akashic.compatlab.tools.PatchBeta44Classes",
         BASE, work / "patched"], "BETA44_CLASS_PATCH PASS")

    content = readzip(BASE)
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
    assert set(base) == set(new), "entries added or removed"
    changed = sorted(n for n in base if base[n] != new[n])
    assert changed == EXPECTED_CHANGED, changed
    for name in changed:
        if name.endswith(".class"):
            assert major(new[name]) <= 52, name
    report.update(entries=len(new), changed=changed, inherited_identical=len(base) - len(changed))

    java8 = pathlib.Path(args.jdk8) / "bin/java"
    report["gate_jvm"] = run([java8, "-version"]).strip().splitlines()[0]
    asm9 = cp(*(libs / j for j in ASM9))
    runtime = cp(*(libs / j for j in RUNTIME))
    stubs = work / "test-stubs"
    javac(args, "-d", stubs, *sorted((ROOT / "test/stubs").rglob("*.java")))
    test = work / "test"
    javac(args, "-cp", cp(runtime, jar, stubs), "-d", test,
          *(ROOT / "test" / n for n in ("DecorationGate.java", "PlacementGate.java")))
    javac(args, "-cp", asm9, "-d", test / "asm9", ROOT / "test/MethodDiff.java")

    old_file, new_file = work / "old.class", work / "new.class"
    old_file.write_bytes(base[DECORATION])
    new_file.write_bytes(new[DECORATION])
    text = run([java8, "-cp", cp(test / "asm9", asm9), "MethodDiff", old_file, new_file], "METHOD_DIFF")
    differing = re.search(r"differing=\[(.*?)\]", text).group(1).split(", ")
    assert sorted(re.sub(r"^\d+ ", "", d) for d in differing) == \
        ["<clinit>()V", "hasEndpointFix(Lorg/objectweb/asm/tree/MethodNode;)Z"], text
    report["method_diff." + DECORATION] = text.strip()

    def decoration(label, version_jar, source, expect="DECORATION"):
        out = work / ("decoration-%s.class" % label)
        text = run([java8, "-Xverify:all", "-cp", cp(test, version_jar, stubs, runtime), "DecorationGate", source, out],
                   expect)
        report["decoration." + label] = [l.strip() for l in text.splitlines() if "DECORATION" in l or "whitespace" in l]
        return out

    decoration("beta43.2.2.40", BASE, libs / "angelica-2.2.40.jar", "DECORATION changed=false endpointFix=false")
    assert any("unsupported drawString fingerprint " + FINGERPRINT_2_2_40 in l
               for l in report["decoration.beta43.2.2.40"]), "beta.43 does not reproduce the pack's warning"
    fixed = decoration("beta44.2.2.40", jar, libs / "angelica-2.2.40.jar", "DECORATION changed=true endpointFix=true")
    assert any("endpoint fix installed" in l for l in report["decoration.beta44.2.2.40"])
    report["decoration.beta44.2.2.40.sha256"] = sha(fixed.read_bytes())
    text = run([java8, "-cp", cp(test, runtime), "PlacementGate", fixed] + list(LAYOUT_2_2_40), "PLACEMENT PASS sites=1")
    report["decoration.beta44.2.2.40.placement"] = text.strip()
    decoration("beta44.2.2.40.again", jar, fixed, "DECORATION changed=false endpointFix=true")
    report["decoration.beta44.2.2.40.idempotent"] = "PASS"

    for angelica in ("2.2.37", "2.2.23", "2.2.18"):
        old = decoration("beta43." + angelica, BASE, libs / ("angelica-%s.jar" % angelica)).read_bytes()
        now = decoration("beta44." + angelica, jar, libs / ("angelica-%s.jar" % angelica)).read_bytes()
        assert old == now, "Angelica %s result changed" % angelica
        report["decoration.%s_identical_to_beta43" % angelica] = sha(now)
    return report


def main():
    parser = argparse.ArgumentParser()
    for name in ("--jdk8", "--jdk21", "--libs", "--out"):
        parser.add_argument(name, required=True)
    parser.add_argument("--report", default=str(ROOT / "reports/final-build-audit.json"))
    args = parser.parse_args()

    assert sha(BASE.read_bytes()) == BASE_SHA, "unexpected beta.43 base"
    builds = []
    for _ in range(2):
        with tempfile.TemporaryDirectory(prefix="beta44-") as tmp:
            builds.append(build(args, pathlib.Path(tmp)))
    assert builds[0] == builds[1], "non-deterministic build"
    out = pathlib.Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_bytes(builds[0])
    with tempfile.TemporaryDirectory(prefix="beta44-gates-") as tmp:
        report = gates(args, out.resolve(), pathlib.Path(tmp))
    report = dict({
        "version": NEW,
        "base": {"path": str(BASE.relative_to(REPO)), "sha256": BASE_SHA},
        "output": {"name": out.name, "sha256": sha(builds[0]), "bytes": len(builds[0])},
        "deterministic_rebuild": "PASS",
    }, **report)
    pathlib.Path(args.report).parent.mkdir(parents=True, exist_ok=True)
    pathlib.Path(args.report).write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print("BETA44_BUILD PASS sha256=%s bytes=%d" % (sha(builds[0]), len(builds[0])))


if __name__ == "__main__":
    main()
