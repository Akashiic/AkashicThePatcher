"""Build and gate 1.1.0-beta.50-RecipeIndex from the beta.49 release JAR.

    python3 beta50-build.py --jdk8 <jdk8 home> --jdk21 <jdk21 home> --libs <dir> --out <output.jar>

--libs: tools/ci/fetch_build_libs.sh (uses launchwrapper-1.12, asm-all-5.0.3, the ASM 9.7 jars).

Boot time (TEOF 5.5): a JFR profile of the client boot put 9.7% of the main thread's Java samples in Thaumcraft's
ThaumcraftCraftingManager.generateTags (RotaryCraft, Thermal Foundation/Expansion, MFR, ChromatiCraft, Extra Utilities
and others call it while registering aspects) and 5.4% in Eln's CraftingRecipes.recipeExists (checkRecipe, which only
logs "No recipe for ..." items). Both walk the whole crafting recipe list once per item.

beta.50 = beta.49 +
  RecipeIndexTransformer (both sides, registered last, after beta.47's Angelica GL lookup bridge)
    Thaumcraft generateTagsFromCraftingRecipes gets the recipe list filtered by output item (RecipeIndexHooks: index
      rebuilt when the list changes; recipes of untrusted classes always kept; same order)
      -Dakashic.thaumcraftRecipeIndex.disable=true
    Eln checkRecipe's recipeExists calls are answered from an index of outputs and ore ids built once per checkRecipe;
      any case that depends on an exception inside Utils.areSame runs the original loop
      -Dakashic.elnRecipeCheckIndex.disable=true
    -Dakashic.recipeIndex.verify=true checks every answer against a full scan (diagnostics)
  AkashicThePatcherLoadingPlugin.getASMTransformerClass: one register call added (tools/PatchBeta50Plugin.java)

Gates (results in reports/final-build-audit.json):
  * deterministic double build; entries: plugin, mod class, manifest, mcmod.info changed, the 6 recipeindex classes
    added, the rest byte-identical to beta.49; class major <= 52; plugin: only getASMTransformerClass differs
  * registration on the JVM verifier: client and server = beta.49's list + RecipeIndexTransformer last
  * RecipeIndexGate (-Xverify:all): shapes of the Thaumcraft and Eln methods run as compiled and through the shipped
    transformer on random recipe lists (mutated between rounds, untrusted recipe classes with changing outputs, null
    outputs, ore ids, a throwing getDamage): identical answers; verify mode: 0 failures; beta.49 and the kill-switches
    leave the shapes untransformed
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
BASE = REPO / "releases/v1.1.0-beta.49-BlockIconCrossDeps/!Akashic-ThePatcher-1.1.0-beta.49-BlockIconCrossDeps.jar"
BASE_SHA = "c0b533a9756eb7b26b3ba050226dbc2a4c953d7ad9d222a7016ce14963fd2fef"
OLD = "1.1.0-beta.49"
NEW = "1.1.0-beta.50"

THIRD_PARTY = {
    "asm-9.7.jar": "adf46d5e34940bdf148ecdd26a9ee8eea94496a72034ff7141066b3eea5c4e9d",
    "asm-tree-9.7.jar": "62f4b3bc436045c1acb5c3ba2d8ec556ec3369093d7f5d06c747eb04b56d52b1",
    "asm-analysis-9.7.jar": "7bc6bcbc21379948a0c8c467fb0f864206e5b818f6bc0b546872f5c9f941556f",
    "asm-util-9.7.jar": "37a6414d36641973f1af104937c95d6d921b2ddb4d612c66c5a9f2b13fc14211",
    "launchwrapper-1.12.jar": "57f402b626d16cc2705bf2a37add7adbb074f0ca3b3102fa6e23aa303dae682f",
    "asm-all-5.0.3.jar": "09d44e6adddaab9fd89ea3164c439f71c755769fa8d3a940352ab3d2003b98ff",
}
ASM9 = ("asm-9.7.jar", "asm-tree-9.7.jar", "asm-analysis-9.7.jar", "asm-util-9.7.jar")
RUNTIME = ("launchwrapper-1.12.jar", "asm-all-5.0.3.jar")

PKG = "com/akashic/thepatcher/module/recipeindex/"
SOURCES = [ROOT / "src" / PKG / "RecipeIndexHooks.java", ROOT / "src" / PKG / "RecipeIndexTransformer.java"]
EXPECTED_ADDED = sorted(PKG + n for n in (
    "RecipeIndexHooks.class", "RecipeIndexHooks$ElnIndex.class", "RecipeIndexHooks$IntList.class",
    "RecipeIndexHooks$ListState.class", "RecipeIndexHooks$ThaumcraftIndex.class", "RecipeIndexTransformer.class"))
PLUGIN = "com/akashic/thepatcher/core/AkashicThePatcherLoadingPlugin.class"
MOD = "com/akashic/thepatcher/AkashicThePatcherMod.class"
MANIFEST = "META-INF/MANIFEST.MF"
MCMOD = "mcmod.info"
EXPECTED_CHANGED = sorted([MANIFEST, MOD, PLUGIN, MCMOD])
TRANSFORMER = "com.akashic.thepatcher.module.recipeindex.RecipeIndexTransformer"
KILL = ["-Dakashic.thaumcraftRecipeIndex.disable=true", "-Dakashic.elnRecipeCheckIndex.disable=true"]


def sha(data):
    return hashlib.sha256(data).hexdigest()


def major(data):
    return struct.unpack_from(">H", data, 6)[0]


def readzip(path):
    with zipfile.ZipFile(str(path)) as z:
        assert z.testzip() is None
        return {n: z.read(n) for n in z.namelist() if not n.endswith("/")}


def make_zip(content, target):
    stamp = (2026, 11, 2, 12, 0, 0)
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


def compile_dir(args, source_dir, target, *classpath):
    javac(args, *(["-cp", cp(*classpath)] if classpath else []), "-d", target, *sorted(source_dir.rglob("*.java")))
    return target


def build(args, work):
    stubs = compile_dir(args, ROOT / "test/stubs", work / "stubs")
    classes = work / "classes"
    javac(args, "-Xlint:all,-options", "-Werror", "-cp", cp(BASE, libjars(args, RUNTIME), stubs), "-d", classes, *SOURCES)
    built = sorted(p.relative_to(classes).as_posix() for p in classes.rglob("*.class"))
    assert built == EXPECTED_ADDED, built

    tools = work / "tools"
    javac(args, "-cp", libjars(args, ASM9), "-d", tools, HERE / "PatchThePatcherVersion.java", HERE / "PatchBeta50Plugin.java")
    java21 = pathlib.Path(args.jdk21) / "bin/java"
    run([java21, "-Xverify:all", "-cp", cp(tools, libjars(args, ASM9)), "com.akashic.compatlab.tools.PatchThePatcherVersion",
         BASE, OLD, NEW, work / "mod/AkashicThePatcherMod.class"], "THEPATCHER_VERSION_PATCH PASS")
    run([java21, "-Xverify:all", "-cp", cp(tools, libjars(args, ASM9)), "com.akashic.compatlab.tools.PatchBeta50Plugin",
         BASE, work / "plugin/AkashicThePatcherLoadingPlugin.class"], "BETA50_PATCH PASS")

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
    libs = pathlib.Path(args.libs).resolve()
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
    runtime = libjars(args, RUNTIME)
    stubs = compile_dir(args, ROOT / "test/stubs", work / "stubs")
    fml = compile_dir(args, ROOT / "test/fml-stubs", work / "fml")
    shapes = compile_dir(args, ROOT / "test/shapes", work / "shapes", stubs)
    test = work / "test"
    javac(args, "-cp", cp(stubs, runtime, fml), "-d", test,
          *(ROOT / "test" / n for n in ("TransformingLoader.java", "RecipeIndexGate.java", "RegistrationGate.java")))
    javac(args, "-cp", libjars(args, ASM9), "-d", test / "asm9", ROOT / "test/MethodDiff.java")

    old_file, new_file = work / "old.class", work / "new.class"
    old_file.write_bytes(base[PLUGIN])
    new_file.write_bytes(new[PLUGIN])
    text = run([java8, "-cp", cp(test / "asm9", libjars(args, ASM9)), "MethodDiff", old_file, new_file], "METHOD_DIFF")
    differing = re.search(r"differing=\[(.*?)\]", text).group(1)
    assert re.fullmatch(r"\d+ getASMTransformerClass\(\)\[Ljava/lang/String;", differing), text
    report["method_diff." + PLUGIN] = text.strip()

    lists = {}
    for label, version_jar in (("beta49", BASE), ("beta50", jar)):
        for side in ("CLIENT", "SERVER"):
            text = run([java8, "-Xverify:all", "-Dstub.side=" + side, "-cp", cp(test, version_jar, fml, runtime),
                        "RegistrationGate"], "REGISTRATION side=" + side)
            lists[(label, side)] = re.search(r"list=\[(.*)\]", text).group(1).split(", ")
            report["registration.%s.%s" % (label, side)] = re.search(r"REGISTRATION.*", text).group(0)
    for side in ("CLIENT", "SERVER"):
        assert lists[("beta50", side)] == lists[("beta49", side)] + [TRANSFORMER], lists[("beta50", side)]

    cases = [("beta50.verify", jar, ["-Dakashic.recipeIndex.verify=true"], "patched"),
             ("beta50", jar, [], "patched"),
             ("beta49", BASE, [], "unpatched"),
             ("beta50.disabled", jar, KILL, "unpatched")]
    for label, version_jar, flags, expect in cases:
        text = run([java8, "-Xverify:all"] + flags + ["-cp", cp(test, version_jar, stubs, runtime),
                    "RecipeIndexGate", shapes, expect], "RECIPE_INDEX_GATE PASS expect=" + expect)
        report["recipe_index_gate." + label] = lines(text, "GATE", "COUNTERS", "RECIPE_INDEX_GATE", "[Akashic-ThePatcher]")
    return report


def main():
    parser = argparse.ArgumentParser()
    for name in ("--jdk8", "--jdk21", "--libs", "--out"):
        parser.add_argument(name, required=True)
    parser.add_argument("--report", default=str(ROOT / "reports/final-build-audit.json"))
    args = parser.parse_args()

    assert sha(BASE.read_bytes()) == BASE_SHA, "unexpected beta.49 base"
    builds = []
    for _ in range(2):
        with tempfile.TemporaryDirectory(prefix="beta50-") as tmp:
            builds.append(build(args, pathlib.Path(tmp)))
    assert builds[0] == builds[1], "non-deterministic build"
    out = pathlib.Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_bytes(builds[0])
    with tempfile.TemporaryDirectory(prefix="beta50-gates-") as tmp:
        report = gates(args, out.resolve(), pathlib.Path(tmp))
    report = dict({
        "version": NEW,
        "base": {"path": str(BASE.relative_to(REPO)), "sha256": BASE_SHA},
        "output": {"name": out.name, "sha256": sha(builds[0]), "bytes": len(builds[0])},
        "deterministic_rebuild": "PASS",
    }, **report)
    pathlib.Path(args.report).parent.mkdir(parents=True, exist_ok=True)
    pathlib.Path(args.report).write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print("BETA50_BUILD PASS sha256=%s bytes=%d" % (sha(builds[0]), len(builds[0])))


if __name__ == "__main__":
    main()
