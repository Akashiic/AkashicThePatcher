"""Build and gate 1.1.0-beta.40-ItemsAtlasAngelicaGuard from the beta.39 release JAR.

    python3 beta40-build.py --jdk8 <jdk8 home> --jdk21 <jdk21 home> --libs <dir> --out <output.jar>

--libs: as for beta.39 (tools/ci/fetch_build_libs.sh): launchwrapper-1.12, asm-all-5.0.3, log4j 2.0-beta9,
guava-17.0, jopt-simple-4.5, ASM 9.7 jars, angelica-2.2.37/2.2.23/2.2.18.

With Angelica 2.2.24+ (AtlasAssembler, "Parallelize atlas loading") the items atlas sprites no longer go through
TextureUtil.uploadTextureMipmap, so ItemsAtlasUploadBatcher staged a single sprite and then uploaded its
mostly-empty 4096x4096 staging copy over the atlas Angelica had filled: items drawn from textures/items
(NEI, inventory, in hand) became invisible. beta.40 adds ItemsAtlasAngelicaGuard and two calls to it in
ItemsAtlasUploadBatcher (begin: bypass with AtlasAssembler present; flush: skip when nothing was cancelled).

Gates (results in reports/final-build-audit.json):
  * deterministic double build; entries: ItemsAtlasUploadBatcher, mod class, manifest, mcmod.info changed,
    ItemsAtlasAngelicaGuard added, the rest byte-identical to beta.39; class major <= 52
  * ItemsAtlasUploadBatcher: only begin and flush differ from beta.39 (fields and header unchanged)
  * ItemsAtlasGuardGate (simulated atlas texture): beta.39 overwrites a filled atlas (reproduces the bug);
    beta.40 leaves it intact and still uploads a vanilla batch; with the real Angelica 2.2.37 jar on the class
    path the batch never arms; with 2.2.23 / 2.2.18 it behaves as before; -Dakashic.itemsAtlasBatch.guard=false
    restores beta.39's behaviour
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
BASE = REPO / "releases/v1.1.0-beta.39-AngelicaFontMixinTrust/!Akashic-ThePatcher-1.1.0-beta.39-AngelicaFontMixinTrust.jar"
BASE_SHA = "8054509c0dc5067e2e043b34f6b93480e29aca285eabb1fcb3d7d8ac9ca73cbd"
OLD = "1.1.0-beta.39"
NEW = "1.1.0-beta.40"

THIRD_PARTY = {
    "angelica-2.2.37.jar": "2125183d9f3c11a401be61e6273e1e813a51e41d892ba035b99cdfdb83e2677b",
    "angelica-2.2.23.jar": "a7b82ca3a193f6737ebb36650f7287b883cf6985a30608c8fed0a6a9c1d4a5f0",
    "angelica-2.2.18.jar": "563d907f3035aedc7761be0f882674d6e736b63c5860e126e7af7df0d9f15c50",
    "log4j-api.jar": "eb0ca9bdd938f80711152a758d6bc331a8b0d769199d457294dc67ed03f73498",
    "log4j-core.jar": "dcde6033b205433d6e9855c93740f798951fa3a3f252035a768d9f356fde806d",
}
ASM9 = ("asm-9.7.jar", "asm-tree-9.7.jar", "asm-analysis-9.7.jar", "asm-util-9.7.jar")
LOG4J = ("log4j-api.jar", "log4j-core.jar")

UPLOAD = "dev/akashic/asynciconcompat/upload/"
BATCHER = UPLOAD + "ItemsAtlasUploadBatcher.class"
GUARD = UPLOAD + "ItemsAtlasAngelicaGuard.class"
MOD = "com/akashic/thepatcher/AkashicThePatcherMod.class"
MANIFEST = "META-INF/MANIFEST.MF"
MCMOD = "mcmod.info"
EXPECTED_CHANGED = sorted([BATCHER, MOD, MANIFEST, MCMOD])
EXPECTED_DIFF = ("differing=[10 flush(Ldev/akashic/asynciconcompat/upload/ItemsAtlasUploadBatcher$State;"
                 "Ljava/lang/String;)V, 9 begin(Ljava/lang/String;IIII)V]")


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
    classes = work / "classes"
    javac(args, "-Xlint:all,-options", "-Werror", "-cp", cp(*(libs / j for j in LOG4J)), "-d", classes,
          ROOT / "src/dev/akashic/asynciconcompat/upload/ItemsAtlasAngelicaGuard.java")
    built = sorted(p.relative_to(classes).as_posix() for p in classes.rglob("*.class"))
    assert built == [GUARD], built

    asm9 = cp(*(libs / j for j in ASM9))
    tools = work / "tools"
    javac(args, "-cp", asm9, "-d", tools, HERE / "PatchThePatcherVersion.java", HERE / "PatchBeta40Classes.java")
    java21 = pathlib.Path(args.jdk21) / "bin/java"
    run([java21, "-Xverify:all", "-cp", cp(tools, asm9), "com.akashic.compatlab.tools.PatchThePatcherVersion",
         BASE, OLD, NEW, work / "mod/AkashicThePatcherMod.class"], "THEPATCHER_VERSION_PATCH PASS")
    run([java21, "-Xverify:all", "-cp", cp(tools, asm9), "com.akashic.compatlab.tools.PatchBeta40Classes",
         BASE, work / "batcher/ItemsAtlasUploadBatcher.class"], "BETA40_PATCH PASS")

    content = readzip(BASE)
    assert GUARD not in content
    content[GUARD] = (classes / GUARD).read_bytes()
    content[BATCHER] = (work / "batcher/ItemsAtlasUploadBatcher.class").read_bytes()
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
    assert set(new) == set(base) | {GUARD}
    changed = sorted(n for n in base if base[n] != new[n])
    assert changed == EXPECTED_CHANGED, changed
    for name in changed + [GUARD]:
        if name.endswith(".class"):
            assert major(new[name]) <= 52, name
    report.update(entries=len(new), changed=changed, added=[GUARD], inherited_identical=len(base) - len(changed))

    java8 = pathlib.Path(args.jdk8) / "bin/java"
    asm9 = cp(*(libs / j for j in ASM9))
    log4j = cp(*(libs / j for j in LOG4J))
    stubs = compile_stubs(args, work / "stubs")
    test = work / "test"
    javac(args, "-cp", cp(jar, stubs), "-d", test, ROOT / "test/ItemsAtlasGuardGate.java")
    javac(args, "-cp", asm9, "-d", test / "asm9", ROOT / "test/MethodDiff.java")

    old_file, new_file = work / "old.class", work / "new.class"
    old_file.write_bytes(base[BATCHER])
    new_file.write_bytes(new[BATCHER])
    text = run([java8, "-cp", cp(test / "asm9", asm9), "MethodDiff", old_file, new_file], EXPECTED_DIFF)
    report["method_diff." + BATCHER] = text.strip()

    cases = [("beta39", BASE, None, "", "broken"),
             ("beta40", jar, None, "", "fixed"),
             ("beta40+angelica-2.2.37", jar, "angelica-2.2.37.jar", "", "angelica"),
             ("beta40+angelica-2.2.23", jar, "angelica-2.2.23.jar", "", "fixed"),
             ("beta40+angelica-2.2.18", jar, "angelica-2.2.18.jar", "", "fixed"),
             ("beta40+angelica-2.2.37.guard-off", jar, "angelica-2.2.37.jar", "-Dakashic.itemsAtlasBatch.guard=false", "broken")]
    for label, version_jar, angelica, flag, expect in cases:
        classpath = cp(test, stubs, version_jar, log4j, *([libs / angelica] if angelica else []))
        cmd = [java8, "-Xverify:all", "-Xmx512m"] + ([flag] if flag else []) + ["-cp", classpath,
                                                                              "ItemsAtlasGuardGate", expect]
        text = run(cmd, "ITEMS_ATLAS_GATE PASS expect=" + expect)
        report["items_atlas." + label] = [l.strip() for l in text.splitlines() if l.startswith(("A ", "B ", "ITEMS"))]
    return report


def main():
    parser = argparse.ArgumentParser()
    for name in ("--jdk8", "--jdk21", "--libs", "--out"):
        parser.add_argument(name, required=True)
    parser.add_argument("--report", default=str(ROOT / "reports/final-build-audit.json"))
    args = parser.parse_args()

    assert sha(BASE.read_bytes()) == BASE_SHA, "unexpected beta.39 base"
    builds = []
    for _ in range(2):
        with tempfile.TemporaryDirectory(prefix="beta40-") as tmp:
            builds.append(build(args, pathlib.Path(tmp)))
    assert builds[0] == builds[1], "non-deterministic build"
    out = pathlib.Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_bytes(builds[0])
    with tempfile.TemporaryDirectory(prefix="beta40-gates-") as tmp:
        report = gates(args, out.resolve(), pathlib.Path(tmp))
    report = dict({
        "version": NEW,
        "base": {"path": str(BASE.relative_to(REPO)), "sha256": BASE_SHA},
        "output": {"name": out.name, "sha256": sha(builds[0]), "bytes": len(builds[0])},
        "deterministic_rebuild": "PASS",
    }, **report)
    pathlib.Path(args.report).write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print("BETA40_BUILD PASS sha256=%s bytes=%d" % (sha(builds[0]), len(builds[0])))


if __name__ == "__main__":
    main()
