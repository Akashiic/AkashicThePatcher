"""Build and gate 1.1.0-beta.37-ForestryGenomeBarrier from the beta.36 release JAR.

    python3 beta37-build.py --jdk8u51 <zulu 8u51 home> --jdk8 <recent jdk8 home> --jdk21 <jdk21 home> \
        --forestry-jar Forestry-4.11.36.jar --libs <dir> --asm9 asm-9.x.jar --out <output.jar>

--libs must contain: asm-all-5.0.3.jar, launchwrapper-1.12.jar (the exact runtime of Forge 1.7.10),
guava-17.0.jar, log4j-api.jar, log4j-core.jar (2.0-beta9). Third-party jars are inputs, never committed.

Gates (all must pass; results in reports/final-build-audit.json):
  * deterministic double build; entries: 5 changed + ForestryGenomeCompat removed; class major <= 52
  * the bundled transformer, run with the real launchwrapper + ASM 5.0.3, turns the REAL Forestry 4.11.36
    Genome.getChromosomes from 288 into 8484 bytes, keeps every original instruction, is idempotent, and
    honours -Dakashic.forestryGenomeBarrier.disable=true
  * GenomeChaos (-Xverify:all -XX:+PrintCompilation, real Forestry classes) on 8u51, recent 8 and 21:
      original Forestry (control) -> Genome::getChromosomes IS compiled
      barrier                    -> never compiled (also under -Xcomp), identical result digest to the original
  * GuardHarness verdicts; GenomeBench per-call cost (informational)
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
BASE = REPO / "releases/v1.1.0-beta.36-ForestryC2CompileBarrier/!Akashic-ThePatcher-1.1.0-beta.36-ForestryC2CompileBarrier.jar"
BASE_SHA = "45a00f1ea27778d9d01d392180c1a3980e440370beeb84ec0af9213e836e2bf4"
FORESTRY_SHA = "78daf273c4d112324ea7fb3ad9fbac99ecd088c1b78982811deebd58da712d6f"
GENOME_SHA = "fe62518c6126cc715610ef3ced8685edac0e5aeb0c0315a5c1d2d284d55f2683"
OLD = "1.1.0-beta.36"
NEW = "1.1.0-beta.37"

PKG = "com/akashic/thepatcher/module/forestry/"
GUARD = PKG + "ForestryC2RuntimeGuard.class"
TRANSFORMER = PKG + "ForestryGenomeC2Transformer.class"
COMPAT = PKG + "ForestryGenomeCompat.class"
MOD = "com/akashic/thepatcher/AkashicThePatcherMod.class"
MANIFEST = "META-INF/MANIFEST.MF"
MCMOD = "mcmod.info"
EXPECTED_CHANGED = sorted([GUARD, TRANSFORMER, MOD, MANIFEST, MCMOD])
GENOME = "forestry/core/genetics/Genome.class"
CHAOS_ITERATIONS = "600000"


def sha(data):
    return hashlib.sha256(data).hexdigest()


def major(data):
    return struct.unpack_from(">H", data, 6)[0]


def readzip(path):
    with zipfile.ZipFile(str(path)) as z:
        assert z.testzip() is None
        return {n: z.read(n) for n in z.namelist() if not n.endswith("/")}


def make_zip(content, target):
    stamp = (2026, 10, 24, 18, 0, 0)
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
    if proc.returncode != 0 or (expect and expect not in text):
        raise SystemExit("FAILED: %s\n%s" % (" ".join(map(str, cmd)), text[-4000:]))
    return text


def cp(*parts):
    return ":".join(str(p) for p in parts)


def javac(args, *rest):
    return run([pathlib.Path(args.jdk21) / "bin/javac", "--release", "8", "-nowarn", "-encoding", "UTF-8"] + list(rest))


def build(args, work):
    libs = pathlib.Path(args.libs)
    classes = work / "classes"
    javac(args, "-Xlint:all,-options", "-Werror",
          "-cp", cp(BASE, libs / "launchwrapper-1.12.jar", libs / "asm-all-5.0.3.jar"), "-d", classes,
          ROOT / "src/com/akashic/thepatcher/module/forestry/ForestryGenomeC2Transformer.java",
          ROOT / "src/com/akashic/thepatcher/module/forestry/ForestryC2RuntimeGuard.java")
    tools = work / "tools"
    javac(args, "-cp", args.asm9, "-d", tools, HERE / "PatchThePatcherVersion.java")
    run([pathlib.Path(args.jdk21) / "bin/java", "-Xverify:all", "-cp", cp(tools, args.asm9),
         "com.akashic.compatlab.tools.PatchThePatcherVersion", BASE, OLD, NEW, work / "mod/AkashicThePatcherMod.class"],
        "THEPATCHER_VERSION_PATCH PASS")

    content = readzip(BASE)
    del content[COMPAT]
    content[GUARD] = (classes / GUARD).read_bytes()
    content[TRANSFORMER] = (classes / TRANSFORMER).read_bytes()
    assert sorted(p.relative_to(classes).as_posix() for p in classes.rglob("*.class")) == sorted([GUARD, TRANSFORMER])
    content[MOD] = (work / "mod/AkashicThePatcherMod.class").read_bytes()
    for name in (MANIFEST, MCMOD):
        text = content[name].decode("utf-8")
        assert text.count(OLD) == 1, name
        content[name] = text.replace(OLD, NEW).encode("utf-8")
    out = work / "out.jar"
    make_zip(content, out)
    return out.read_bytes()


def compiled(text, needle):
    return sorted({l.strip() for l in text.splitlines() if needle in l and "made not entrant" not in l})


def gates(args, jar, work):
    report = {}
    libs = pathlib.Path(args.libs)
    base, new = readzip(BASE), readzip(jar)
    assert sorted(set(base) - set(new)) == [COMPAT] and not set(new) - set(base)
    changed = sorted(n for n in new if base[n] != new[n])
    assert changed == EXPECTED_CHANGED, changed
    for name in changed:
        if name.endswith(".class"):
            assert major(new[name]) <= 52, name
    report.update(entries=len(new), changed=changed, removed=[COMPAT], inherited_identical=len(new) - len(changed))

    forestry = pathlib.Path(args.forestry_jar)
    assert sha(forestry.read_bytes()) == FORESTRY_SHA, "unexpected Forestry jar"
    original_genome = readzip(forestry)[GENOME]
    assert sha(original_genome) == GENOME_SHA

    # Test classes: stubs + harnesses (Java 8 bytecode), compiled against the real Forestry jar.
    test = work / "test"
    stubs = sorted((ROOT / "test/stubs").rglob("*.java"))
    test_cp = cp(forestry, libs / "guava-17.0.jar", libs / "log4j-api.jar", libs / "log4j-core.jar")
    javac(args, "-cp", cp(test, forestry), "-d", test, *stubs)
    javac(args, "-cp", cp(test, test_cp, jar, libs / "launchwrapper-1.12.jar"), "-d", test,
          ROOT / "test/GenomeChaos.java", ROOT / "test/GenomeBench.java",
          ROOT / "test/RunTransformer.java", ROOT / "test/GuardHarness.java")
    javac(args, "-cp", libs / "asm-all-5.0.3.jar", "-d", test, ROOT / "test/BodyCompare.java")

    (work / "original" / GENOME).parent.mkdir(parents=True)
    (work / "original" / GENOME).write_bytes(original_genome)
    java51 = pathlib.Path(args.jdk8u51) / "bin/java"
    transform_cp = cp(test, jar, libs / "launchwrapper-1.12.jar", libs / "asm-all-5.0.3.jar")
    text = run([java51, "-cp", transform_cp, "RunTransformer", work / "original" / GENOME, work / "barrier" / GENOME],
               "TRANSFORM identity=false")
    assert "applied=true codeLength=8484 failed=null" in text, text
    assert "Genome compile barrier applied: Forestry's original getChromosomes kept unchanged, code 288 -> 8484" in text
    report["transform"] = [l for l in text.splitlines() if l.startswith("[") or l.startswith("TRANSFORM")]
    barrier_genome = (work / "barrier" / GENOME).read_bytes()
    report["transform_sha256"] = {"original": sha(original_genome), "barrier": sha(barrier_genome)}

    text = run([java51, "-cp", transform_cp, "RunTransformer", work / "barrier" / GENOME, work / "again" / GENOME],
               "TRANSFORM identity=true")
    assert "applied=true codeLength=8484" in text
    text = run([java51, "-Dakashic.forestryGenomeBarrier.disable=true", "-cp", transform_cp, "RunTransformer",
                work / "original" / GENOME, work / "disabled" / GENOME], "TRANSFORM identity=true")
    assert "DISABLED" in text and "applied=null" in text
    report["transform_idempotent"] = report["transform_killswitch"] = "PASS"

    # Every original instruction (and every other method) is still there, after the barrier head.
    text = run([java51, "-cp", cp(test, libs / "asm-all-5.0.3.jar"), "BodyCompare",
                work / "original" / GENOME, work / "barrier" / GENOME, "8192"], "BODY_COMPARE PASS")
    report["original_body_preserved"] = text.strip().splitlines()[-1]

    jdks = (("java8u51", args.jdk8u51), ("java8", args.jdk8), ("java21", args.jdk21))
    for label, home in jdks:
        java = pathlib.Path(home) / "bin/java"
        digests = {}
        for variant in ("original", "barrier"):
            text = run([java, "-Xverify:all", "-XX:+PrintCompilation",
                        "-cp", cp(work / variant, test, test_cp), "GenomeChaos", CHAOS_ITERATIONS, "11"], "CHAOS DONE")
            digests[variant] = re.search(r"digest=(\w+)", text).group(1)
            hits = compiled(text, "forestry.core.genetics.Genome::getChromosomes")
            report["%s.%s.compiled" % (label, variant)] = hits
            report["%s.%s.result" % (label, variant)] = re.search(r"CHAOS DONE (.*)", text).group(1)
            if variant == "original":
                assert hits, "control: original Genome.getChromosomes was not compiled on " + label
            else:
                assert not hits, (label, hits)
        assert digests["original"] == digests["barrier"], (label, digests)
        assert re.search(r"thrown=[1-9]", report["%s.original.result" % label]), "exception path not exercised"
        text = run([java, "-Xcomp", "-XX:+PrintCompilation", "-cp", cp(work / "barrier", test, test_cp),
                    "GenomeChaos", "20000", "12"], "CHAOS DONE")
        hits = compiled(text, "forestry.core.genetics.Genome::getChromosomes")
        assert not hits, (label, "Xcomp", hits)
        report["%s.barrier.xcomp.compiled" % label] = hits
        for variant in ("original", "barrier"):
            text = run([java, "-cp", cp(work / variant, test, test_cp), "GenomeBench", "3"], "BENCH")
            report["%s.%s.bench" % (label, variant)] = re.search(r"BENCH (ns/call=\d+)", text).group(1)

    guard_cp = cp(jar, test)
    text = run([java51, "-cp", guard_cp, "GuardHarness"], "barrier=true")
    assert "Genome compile barrier armed" in text, text
    report["guard.default"] = text.strip().splitlines()
    text = run([java51, "-XX:-DontCompileHugeMethods", "-cp", guard_cp, "GuardHarness"], "barrier=false")
    assert "UNPROTECTED" in text, text
    report["guard.hugeMethodsOff"] = text.strip().splitlines()
    text = run([java51, "-XX:-DontCompileHugeMethods",
                "-XX:CompileCommand=exclude,forestry/core/genetics/Genome.getChromosomes",
                "-cp", guard_cp, "GuardHarness"], "GUARD active=true matched=1")
    assert "CompilerOracle exclusion for Genome.getChromosomes is present" in text, text
    report["guard.compileCommandOnly"] = [l for l in text.strip().splitlines() if not l.startswith("CompilerOracle:")]
    return report


def main():
    parser = argparse.ArgumentParser()
    for name in ("--jdk8u51", "--jdk8", "--jdk21", "--forestry-jar", "--libs", "--asm9", "--out"):
        parser.add_argument(name, required=True)
    parser.add_argument("--report", default=str(ROOT / "reports/final-build-audit.json"))
    args = parser.parse_args()

    assert sha(BASE.read_bytes()) == BASE_SHA, "unexpected beta.36 base"
    builds = []
    for _ in range(2):
        with tempfile.TemporaryDirectory(prefix="beta37-") as tmp:
            builds.append(build(args, pathlib.Path(tmp)))
    assert builds[0] == builds[1], "non-deterministic build"
    out = pathlib.Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_bytes(builds[0])
    with tempfile.TemporaryDirectory(prefix="beta37-gates-") as tmp:
        report = gates(args, out.resolve(), pathlib.Path(tmp))
    report = dict({
        "version": NEW,
        "base": {"path": str(BASE.relative_to(REPO)), "sha256": BASE_SHA},
        "forestry": {"jar_sha256": FORESTRY_SHA, "genome_class_sha256": GENOME_SHA},
        "output": {"name": out.name, "sha256": sha(builds[0]), "bytes": len(builds[0])},
        "deterministic_rebuild": "PASS",
    }, **report)
    pathlib.Path(args.report).write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print("BETA37_BUILD PASS sha256=%s bytes=%d" % (sha(builds[0]), len(builds[0])))


if __name__ == "__main__":
    main()
