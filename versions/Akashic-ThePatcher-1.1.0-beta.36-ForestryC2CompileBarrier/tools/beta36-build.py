"""Build and gate 1.1.0-beta.36-ForestryC2CompileBarrier from the beta.35 release JAR.

Usage (from anywhere):
    python3 beta36-build.py --jdk21 /usr/lib/jvm/java-21-openjdk-amd64 --jdk8 <jdk8 home> \
        --asm <asm-9.x.jar> --asm-tree <asm-tree-9.x.jar> --out <output.jar>

Steps: rebuild twice and compare SHA-256, then run the offline gates:
  * exactly 5 entries differ from beta.35 (guard, ForestryGenomeCompat, mod class, manifest, mcmod.info);
  * changed classes are class major <= 52;
  * GenomeCompatHarness on Java 8 and 21 with -Xverify:all -XX:+PrintCompilation:
      padded class  -> results correct, no ForestryGenomeCompat method compiled;
      beta.35 class -> ForestryGenomeCompat IS compiled (proves the probe detects compilation);
  * GuardHarness on Java 8: barrier verdict with default flags, UNPROTECTED with -XX:-DontCompileHugeMethods,
    full CompilerOracle verdict with the six CompileCommands.
"""
import argparse
import hashlib
import json
import pathlib
import struct
import subprocess
import tempfile
import zipfile

HERE = pathlib.Path(__file__).resolve().parent
ROOT = HERE.parent
REPO = ROOT.parent.parent
BASE = REPO / "releases/v1.1.0-beta.35-BackhandLifecycleFix/!Akashic-ThePatcher-1.1.0-beta.35-BackhandLifecycleFix.jar"
BASE_SHA = "c48d3ec3b1ef35f907baca47cd78ead48730b4cd556592e529c0140d7a869c17"

OLD_MOD_VERSION = "1.1.0-beta.34"  # beta.35 did not bump AkashicThePatcherMod.VERSION
OLD_TEXT_VERSION = "1.1.0-beta.35"
NEW = "1.1.0-beta.36"

GUARD = "com/akashic/thepatcher/module/forestry/ForestryC2RuntimeGuard.class"
COMPAT = "com/akashic/thepatcher/module/forestry/ForestryGenomeCompat.class"
MOD = "com/akashic/thepatcher/AkashicThePatcherMod.class"
MANIFEST = "META-INF/MANIFEST.MF"
MCMOD = "mcmod.info"
EXPECTED_CHANGED = sorted([GUARD, COMPAT, MOD, MANIFEST, MCMOD])

EXCLUSIONS = [
    "forestry/core/genetics/Genome.getChromosomes",
    "com/akashic/thepatcher/module/forestry/ForestryGenomeCompat.loadChromosomes",
    "com/akashic/thepatcher/module/forestry/ForestryGenomeCompat.loadStoredChromosome",
    "com/akashic/thepatcher/module/forestry/ForestryGenomeCompat.repairInvalidAlleles",
    "com/akashic/thepatcher/module/forestry/ForestryGenomeCompat.validateChromosome",
    "com/akashic/thepatcher/module/forestry/ForestryGenomeCompat.fillMissingChromosomes",
]


def sha(data):
    return hashlib.sha256(data).hexdigest()


def major(data):
    return struct.unpack_from(">H", data, 6)[0]


def readzip(path):
    with zipfile.ZipFile(str(path)) as z:
        assert z.testzip() is None
        return {n: z.read(n) for n in z.namelist() if not n.endswith("/")}


def make_zip(content, target):
    stamp = (2026, 10, 24, 12, 0, 0)
    with zipfile.ZipFile(str(target), "w", zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        for name in sorted(content):
            info = zipfile.ZipInfo(name, stamp)
            info.create_system = 3
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o644 << 16
            z.writestr(info, content[name], compress_type=zipfile.ZIP_DEFLATED, compresslevel=9)
    with zipfile.ZipFile(str(target)) as z:
        assert z.testzip() is None


def run(cmd, expect=None, env_clear=True):
    env = {"PATH": "/usr/bin:/bin"} if env_clear else None
    proc = subprocess.run([str(c) for c in cmd], stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                          timeout=600, env=env)
    text = proc.stdout.decode("utf-8", "replace")
    if proc.returncode != 0 or (expect and expect not in text):
        raise SystemExit("FAILED: %s\n%s" % (" ".join(map(str, cmd)), text))
    return text


def build(args, work):
    javac = pathlib.Path(args.jdk21) / "bin/javac"
    java = pathlib.Path(args.jdk21) / "bin/java"
    guard_out = work / "guard"
    tools_out = work / "tools"
    run([javac, "--release", "8", "-encoding", "UTF-8", "-Xlint:all,-options", "-Werror",
         "-cp", BASE, "-d", guard_out,
         ROOT / "src/com/akashic/thepatcher/module/forestry/ForestryC2RuntimeGuard.java"])
    cp = ":".join(map(str, [guard_out, BASE, args.asm, args.asm_tree]))
    run([javac, "--release", "8", "-encoding", "UTF-8", "-cp", cp, "-d", tools_out,
         HERE / "ApplyForestryCompileBarrier.java", HERE / "PatchThePatcherVersion.java"])
    cp = ":".join(map(str, [tools_out, guard_out, BASE, args.asm, args.asm_tree]))
    run([java, "-Xverify:all", "-cp", cp, "com.akashic.compatlab.tools.ApplyForestryCompileBarrier",
         BASE, work / "ForestryGenomeCompat.class"], "FORESTRY_COMPILE_BARRIER PASS")
    run([java, "-Xverify:all", "-cp", cp, "com.akashic.compatlab.tools.PatchThePatcherVersion",
         BASE, OLD_MOD_VERSION, NEW, work / "mod/AkashicThePatcherMod.class"], "THEPATCHER_VERSION_PATCH PASS")

    content = readzip(BASE)
    content[GUARD] = (guard_out / GUARD).read_bytes()
    content[COMPAT] = (work / "ForestryGenomeCompat.class").read_bytes()
    content[MOD] = (work / "mod/AkashicThePatcherMod.class").read_bytes()
    for name in (MANIFEST, MCMOD):
        text = content[name].decode("utf-8")
        assert text.count(OLD_TEXT_VERSION) == 1, name
        content[name] = text.replace(OLD_TEXT_VERSION, NEW).encode("utf-8")
    out = work / "out.jar"
    make_zip(content, out)
    return out.read_bytes()


def compiled_compat_methods(text):
    return sorted({line.split("ForestryGenomeCompat::")[1].split()[0]
                   for line in text.splitlines() if "ForestryGenomeCompat::" in line})


def gates(args, jar_path, work):
    report = {}
    base = readzip(BASE)
    new = readzip(jar_path)
    assert sorted(base) == sorted(new), "entry set changed"
    changed = sorted(n for n in base if base[n] != new[n])
    assert changed == EXPECTED_CHANGED, changed
    for name in changed:
        if name.endswith(".class"):
            assert major(new[name]) <= 52, (name, major(new[name]))
    report["entries"] = len(new)
    report["changed"] = changed
    report["inherited_identical"] = len(new) - len(changed)

    # Harness: stubs + harness compiled once (Java 8 bytecode), run against padded and original compat class.
    javac = pathlib.Path(args.jdk21) / "bin/javac"
    padded_dir = work / "padded"
    original_dir = work / "original"
    for target, data in ((padded_dir, new[COMPAT]), (original_dir, base[COMPAT])):
        path = target / COMPAT
        path.parent.mkdir(parents=True)
        path.write_bytes(data)
    stubs = sorted((ROOT / "test/stubs").rglob("*.java"))
    harness_out = work / "harness"
    run([javac, "--release", "8", "-encoding", "UTF-8", "-cp", padded_dir, "-d", harness_out,
         ROOT / "test/GenomeCompatHarness.java"] + stubs)
    for label, home in (("java8", args.jdk8), ("java21", args.jdk21)):
        java = pathlib.Path(home) / "bin/java"
        for variant, compat_dir in (("padded", padded_dir), ("beta35", original_dir)):
            text = run([java, "-Xverify:all", "-XX:+PrintCompilation",
                        "-cp", "%s:%s" % (compat_dir, harness_out), "GenomeCompatHarness", "2000000"],
                       "HARNESS PASS")
            compiled = compiled_compat_methods(text)
            report["%s.%s.compiled" % (label, variant)] = compiled
            if variant == "padded":
                assert compiled == [], (label, compiled)
            else:
                assert "loadChromosomes" in compiled, (label, compiled)

    java8 = pathlib.Path(args.jdk8) / "bin/java"
    guard_cp = "%s:%s" % (jar_path, harness_out)
    run([javac, "--release", "8", "-cp", jar_path, "-d", harness_out, ROOT / "test/GuardHarness.java"])
    text = run([java8, "-cp", guard_cp, "GuardHarness"], "GUARD active=false matched=0 rewrite=true barrier=true")
    assert "In-JAR compile barrier active (5/5" in text, text
    report["guard.default"] = text.strip().splitlines()
    text = run([java8, "-XX:-DontCompileHugeMethods", "-cp", guard_cp, "GuardHarness"], "barrier=false")
    assert "UNPROTECTED" in text, text
    report["guard.hugeMethodsOff"] = text.strip().splitlines()
    text = run([java8] + ["-XX:CompileCommand=exclude," + e for e in EXCLUSIONS]
               + ["-cp", guard_cp, "GuardHarness"], "GUARD active=true matched=6 rewrite=true barrier=true")
    report["guard.compileCommands"] = [l for l in text.strip().splitlines() if not l.startswith("CompilerOracle:")]
    return report


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--jdk21", required=True)
    parser.add_argument("--jdk8", required=True)
    parser.add_argument("--asm", required=True)
    parser.add_argument("--asm-tree", required=True)
    parser.add_argument("--out", required=True)
    parser.add_argument("--report", default=str(ROOT / "reports/final-build-audit.json"))
    args = parser.parse_args()

    base_bytes = BASE.read_bytes()
    assert sha(base_bytes) == BASE_SHA, "unexpected beta.35 base"
    builds = []
    for _ in range(2):
        with tempfile.TemporaryDirectory(prefix="beta36-") as tmp:
            builds.append(build(args, pathlib.Path(tmp)))
    assert builds[0] == builds[1], "non-deterministic build"
    out = pathlib.Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_bytes(builds[0])

    with tempfile.TemporaryDirectory(prefix="beta36-gates-") as tmp:
        report = gates(args, out, pathlib.Path(tmp))
    report = dict({
        "version": NEW,
        "base": {"path": str(BASE.relative_to(REPO)), "sha256": BASE_SHA},
        "output": {"name": out.name, "sha256": sha(builds[0]), "bytes": len(builds[0])},
        "deterministic_rebuild": "PASS",
    }, **report)
    pathlib.Path(args.report).write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print("BETA36_BUILD PASS sha256=%s bytes=%d" % (sha(builds[0]), len(builds[0])))


if __name__ == "__main__":
    main()
