"""Build and gate 1.1.0-beta.49-BlockIconCrossDeps from the beta.48 release JAR.

    python3 beta49-build.py --jdk8 <jdk8 home> --jdk21 <jdk21 home> --libs <dir> --out <output.jar>

--libs: tools/ci/fetch_build_libs.sh (uses log4j-api / log4j-core 2.0-beta9 and the ASM 9.7 jars).

Problem (TEOF 5.5 test client, 2026-11-01): the texture reload aborted with "Parallel block icon registration failed",
NullPointerException in BlockSandStone.getIcon called from Et Futurum's BlockRedSandstoneSlab.registerBlockIcons on a
worker of the hybrid async icon loader (AsyncCompat, unchanged since beta.34). The slab reads vanilla sandstone's icon
while registering its own; in parallel the sandstone job may not have set it yet. A timing race: it can hit any
player, more often when the boot timing shifts. A bytecode scan of the pack's mods found 28 block classes with the same
pattern (Et Futurum, ChromatiCraft, ReactorCraft, RotaryCraft, Twilight Forest, Magic Bees, Botania, Thaumic Horizons,
Extra Utilities).

beta.49 = beta.48 +
  CompatHooks   (compiled from src/, = beta.34 source + this) blocks of those 28 classes, or of a subclass, run on the
                client thread; the hybrid loop then waits for all jobs submitted before them. Log line once per class;
                telemetry gains crossDependentSyncBlocks. -Dakashic.iconCrossDeps.disable=true: beta.48 behaviour.

Gates (results in reports/final-build-audit.json):
  * deterministic double build; entries: CompatHooks, mod class, manifest, mcmod.info changed, nothing added or
    removed, the rest byte-identical to beta.48; class major <= 52
  * HooksDiff vs beta.48: only the expected fields/methods differ; every beta.48 method still present
  * CrossDepsGate on the JVM verifier through the shipped class: beta.48 sends the slab to a worker and the race replay
    reads null; beta.49 keeps it on the client thread (also a subclass), leaves unlisted and vanilla blocks parallel,
    the race replay reads the icon; beta.49 with the kill-switch = beta.48
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
BASE = REPO / "releases/v1.1.0-beta.48-BackhandProxyReturn/!Akashic-ThePatcher-1.1.0-beta.48-BackhandProxyReturn.jar"
BASE_SHA = "b750f8109dff722e2f91195573d5c80668555553645ee621fed0e95c6f4253e5"
OLD = "1.1.0-beta.48"
NEW = "1.1.0-beta.49"
VERSION_TOOL = HERE / "PatchThePatcherVersion.java"

THIRD_PARTY = {
    "asm-9.7.jar": "adf46d5e34940bdf148ecdd26a9ee8eea94496a72034ff7141066b3eea5c4e9d",
    "asm-tree-9.7.jar": "62f4b3bc436045c1acb5c3ba2d8ec556ec3369093d7f5d06c747eb04b56d52b1",
    "asm-analysis-9.7.jar": "7bc6bcbc21379948a0c8c467fb0f864206e5b818f6bc0b546872f5c9f941556f",
    "asm-util-9.7.jar": "37a6414d36641973f1af104937c95d6d921b2ddb4d612c66c5a9f2b13fc14211",
    "log4j-api.jar": "eb0ca9bdd938f80711152a758d6bc331a8b0d769199d457294dc67ed03f73498",
    "log4j-core.jar": "dcde6033b205433d6e9855c93740f798951fa3a3f252035a768d9f356fde806d",
}
ASM9 = ("asm-9.7.jar", "asm-tree-9.7.jar", "asm-analysis-9.7.jar", "asm-util-9.7.jar")
LOG4J = ("log4j-api.jar", "log4j-core.jar")

COMPAT = "dev/akashic/asynciconcompat/CompatHooks.class"
MOD = "com/akashic/thepatcher/AkashicThePatcherMod.class"
MANIFEST = "META-INF/MANIFEST.MF"
MCMOD = "mcmod.info"
EXPECTED_CHANGED = sorted([COMPAT, MANIFEST, MCMOD, MOD])
EXPECTED_DIFF = ('fields+=[26 CROSS_DEPENDENT_BLOCKSLjava/util/Set; null, 26 CROSS_DEPENDENT_MATCHLjava/util/Map; null, 26 CROSS_DEPENDENT_SYNC_BLOCKSLjava/util/concurrent/atomic/AtomicInteger; null, 26 CROSS_DEPS_ENABLEDZ null]'
                 ' differing=[+10 crossDependentAncestor(Ljava/lang/Class;)Ljava/lang/String;, +10 isCrossDependentBlock(Lnet/minecraft/block/Block;)Z, +8 crossDependentBlockClassNames()Ljava/util/Set;, 8 <clinit>()V, 9 beginReload(Ljava/lang/String;)V, 9 shouldRunBlockSynchronously(Lnet/minecraft/block/Block;)Z, 9 telemetry()Ljava/lang/String;]')


def sha(data):
    return hashlib.sha256(data).hexdigest()


def major(data):
    return struct.unpack_from(">H", data, 6)[0]


def readzip(path):
    with zipfile.ZipFile(str(path)) as z:
        assert z.testzip() is None
        return {n: z.read(n) for n in z.namelist() if not n.endswith("/")}


def make_zip(content, target):
    stamp = (2026, 11, 1, 12, 0, 0)
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


def compile_stubs(args, target):
    javac(args, "-d", target, *sorted((ROOT / "test/stubs").rglob("*.java")))
    return target


def build(args, work):
    stubs = compile_stubs(args, work / "stubs")
    classes = work / "classes"
    javac(args, "-Xlint:all,-options", "-Werror", "-cp", cp(stubs, libjars(args, LOG4J)), "-d", classes,
          ROOT / "src/dev/akashic/asynciconcompat/CompatHooks.java")
    built = sorted(p.relative_to(classes).as_posix() for p in classes.rglob("*.class"))
    assert built == [COMPAT], built

    tools = work / "tools"
    javac(args, "-cp", libjars(args, ASM9), "-d", tools, VERSION_TOOL)
    java21 = pathlib.Path(args.jdk21) / "bin/java"
    run([java21, "-Xverify:all", "-cp", cp(tools, libjars(args, ASM9)), "com.akashic.compatlab.tools.PatchThePatcherVersion",
         BASE, OLD, NEW, work / "mod/AkashicThePatcherMod.class"], "THEPATCHER_VERSION_PATCH PASS")

    content = readzip(BASE)
    assert COMPAT in content
    content[COMPAT] = (classes / COMPAT).read_bytes()
    content[MOD] = (work / "mod/AkashicThePatcherMod.class").read_bytes()
    for name in (MANIFEST, MCMOD):
        text = content[name].decode("utf-8")
        assert text.count(OLD) == 1, name
        content[name] = text.replace(OLD, NEW).encode("utf-8")
    out = work / "out.jar"
    make_zip(content, out)
    return out.read_bytes()


def gate_lines(text):
    return [l.strip() for l in text.splitlines() if l.strip().startswith(("GATE", "LIST", "RACE", "TELEMETRY", "CROSS_DEPS_GATE"))]


def gates(args, jar, work):
    report = {}
    libs = pathlib.Path(args.libs)
    for name, expected in THIRD_PARTY.items():
        assert sha((libs / name).read_bytes()) == expected, "unexpected " + name
    report["third_party_sha256"] = dict(THIRD_PARTY)

    base, new = readzip(BASE), readzip(jar)
    assert set(base) == set(new), sorted(set(base) ^ set(new))
    changed = sorted(n for n in base if base[n] != new[n])
    assert changed == EXPECTED_CHANGED, changed
    for name in changed:
        if name.endswith(".class"):
            assert major(new[name]) <= 52, name
    report.update(entries=len(new), changed=changed, added=[], inherited_identical=len(base) - len(changed))

    stubs = compile_stubs(args, work / "stubs")
    test = work / "test"
    javac(args, "-cp", libjars(args, ASM9), "-d", test / "asm9", ROOT / "test/HooksDiff.java")
    javac(args, "-cp", cp(jar, stubs), "-d", test, ROOT / "test/CrossDepsGate.java")
    java8 = pathlib.Path(args.jdk8) / "bin/java"
    old_file, new_file = work / "old.class", work / "new.class"
    old_file.write_bytes(base[COMPAT])
    new_file.write_bytes(new[COMPAT])
    text = run([java8, "-cp", cp(test / "asm9", libjars(args, ASM9)), "HooksDiff", old_file, new_file], "HOOKS_DIFF")
    assert EXPECTED_DIFF in text, text
    report["hooks_diff." + COMPAT] = text.strip()

    cases = [("beta48", BASE, [], "bug"), ("beta49", jar, [], "fixed"),
             ("beta49.disabled", jar, ["-Dakashic.iconCrossDeps.disable=true"], "bug")]
    for label, version_jar, flags, expect in cases:
        # the test classes first: the gate's own stubs must not shadow the shipped CompatHooks
        classpath = cp(test, version_jar, stubs, libjars(args, LOG4J))
        text = run([java8, "-Xverify:all"] + flags + ["-cp", classpath, "CrossDepsGate", expect],
                   "CROSS_DEPS_GATE PASS expect=" + expect)
        report["cross_deps_gate." + label] = gate_lines(text)
    return report


def main():
    parser = argparse.ArgumentParser()
    for name in ("--jdk8", "--jdk21", "--libs", "--out"):
        parser.add_argument(name, required=True)
    parser.add_argument("--report", default=str(ROOT / "reports/final-build-audit.json"))
    args = parser.parse_args()

    assert sha(BASE.read_bytes()) == BASE_SHA, "unexpected beta.48 base"
    builds = []
    for _ in range(2):
        with tempfile.TemporaryDirectory(prefix="beta49-") as tmp:
            builds.append(build(args, pathlib.Path(tmp)))
    assert builds[0] == builds[1], "non-deterministic build"
    out = pathlib.Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_bytes(builds[0])
    with tempfile.TemporaryDirectory(prefix="beta49-gates-") as tmp:
        report = gates(args, out.resolve(), pathlib.Path(tmp))
    report = dict({
        "version": NEW,
        "base": {"path": str(BASE.relative_to(REPO)), "sha256": BASE_SHA},
        "output": {"name": out.name, "sha256": sha(builds[0]), "bytes": len(builds[0])},
        "deterministic_rebuild": "PASS",
    }, **report)
    pathlib.Path(args.report).parent.mkdir(parents=True, exist_ok=True)
    pathlib.Path(args.report).write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print("BETA49_BUILD PASS sha256=%s bytes=%d" % (sha(builds[0]), len(builds[0])))


if __name__ == "__main__":
    main()
