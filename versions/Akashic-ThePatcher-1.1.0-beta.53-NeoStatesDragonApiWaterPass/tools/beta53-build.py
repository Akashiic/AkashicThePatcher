"""Build and gate 1.1.0-beta.53-NeoStatesDragonApiWaterPass from the beta.52 release JAR.

    python3 beta53-build.py --jdk8 <jdk8 home> --jdk21 <jdk21 home> --libs <dir> --out <output.jar>

--libs: tools/ci/fetch_build_libs.sh (uses launchwrapper-1.12, asm-all-5.0.3, the ASM 9.7 jars, angelica-2.2.40 and
+unimixins-all-1.7.10-0.3.1).

Neo States (Akashic) x DragonAPI without Angelica: Neo States adds, only when Angelica is absent, a required
@WrapOperation on Block.getRenderBlockPass() inside WorldRenderer.updateRenderer (MixinWorldRenderer_WaterPass), and
DragonAPI's coremod rewrites that call into RenderBlockAtPosEvent.getMaxRenderPass(block, x, y, z). With both mods and
no Angelica the injection finds no target and the client stops in pre-init (Critical injection failure ... Scanned 0
target(s)). TEOF reaches it in the launcher's compatibility mode (Angelica removed for GPUs without OpenGL 3.3).

beta.53 = beta.52 +
  NeoStatesWaterPassTransformer (client only, @MixinSafeTransformer, registered last, after beta.52's held item cull)
    armed only with Neo States and DragonAPI present and Angelica absent, and only for the Neo States shape it knows
    (one @WrapOperation, on the vanilla call; a Neo States that wraps DragonAPI's hook itself leaves everything alone)
    Neo States' MixinWorldRenderer_WaterPass: that @WrapOperation gets require = 0
    DragonAPI's RenderBlockAtPosEvent.getMaxRenderPass: its single block.getRenderBlockPass() value goes through
      NeoStatesWaterPassHooks.waterPass(pass, block) (pass < 1 and Neo States says the block may hold water -> 1)
    fails closed: exact names, descriptors, annotation values and site count, ASM verifier;
      -Dakashic.neoStatesWaterPass.disable=true
  NeoStatesWaterPassHooks (runtime side, calls Neo States' public API NeoStates.canHoldWater)
  AkashicThePatcherLoadingPlugin.getASMTransformerClass: one register call added (tools/PatchBeta53Plugin.java)

Gates (results in reports/final-build-audit.json):
  * deterministic double build; entries: plugin, mod class, manifest, mcmod.info changed, the two neostates classes
    added, the rest byte-identical to beta.52; class major <= 52; plugin: only getASMTransformerClass differs
  * registration on the JVM verifier: client = beta.52's list + NeoStatesWaterPassTransformer last; server = beta.52's
  * Mixingasm's trust rule with the real UniMixins 0.3.1 jar: trusted; without UniMixins the class still loads
  * NeoStatesWaterPassGate (-Xverify:all) on the real Neo States 0.1.0-alpha.10 mixin source and DragonAPI V33b's
    RenderBlockAtPosEvent shape: patched with both mods and no Angelica (require=0 and nothing else of the mixin
    changes; a holder block asks for pass 1, every other answer is the original); untouched with the kill-switch, with
    Angelica 2.2.40 on the class path, without DragonAPI, with an upstream-fixed Neo States and with beta.52
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
BASE = REPO / "releases/v1.1.0-beta.52-HeldItemCullBox/!Akashic-ThePatcher-1.1.0-beta.52-HeldItemCullBox.jar"
BASE_SHA = "9fb5ae22c89be71919d31b584f138668448271af78d0b383b72e8ed9cf5059af"
OLD = "1.1.0-beta.52"
NEW = "1.1.0-beta.53"

THIRD_PARTY = {
    "asm-9.7.jar": "adf46d5e34940bdf148ecdd26a9ee8eea94496a72034ff7141066b3eea5c4e9d",
    "asm-tree-9.7.jar": "62f4b3bc436045c1acb5c3ba2d8ec556ec3369093d7f5d06c747eb04b56d52b1",
    "asm-analysis-9.7.jar": "7bc6bcbc21379948a0c8c467fb0f864206e5b818f6bc0b546872f5c9f941556f",
    "asm-util-9.7.jar": "37a6414d36641973f1af104937c95d6d921b2ddb4d612c66c5a9f2b13fc14211",
    "launchwrapper-1.12.jar": "57f402b626d16cc2705bf2a37add7adbb074f0ca3b3102fa6e23aa303dae682f",
    "asm-all-5.0.3.jar": "09d44e6adddaab9fd89ea3164c439f71c755769fa8d3a940352ab3d2003b98ff",
    "angelica-2.2.40.jar": "c91adf2712aa65f8d99f5378a3027f538ab8e027c0b5b2aa261cc632ae937c54",
    "+unimixins-all-1.7.10-0.3.1.jar": "ad0ea4f92daf7bf7ec5c10e16258425e47929126954aea5d80df69ce26cd7318",
}
ASM9 = ("asm-9.7.jar", "asm-tree-9.7.jar", "asm-analysis-9.7.jar", "asm-util-9.7.jar")
RUNTIME = ("launchwrapper-1.12.jar", "asm-all-5.0.3.jar")
UNIMIXINS = "+unimixins-all-1.7.10-0.3.1.jar"
ANGELICA = "angelica-2.2.40.jar"

PKG = "com/akashic/thepatcher/module/neostates/"
SOURCES = [ROOT / "src" / PKG / "NeoStatesWaterPassTransformer.java", ROOT / "src" / PKG / "NeoStatesWaterPassHooks.java"]
EXPECTED_ADDED = sorted([PKG + "NeoStatesWaterPassHooks.class", PKG + "NeoStatesWaterPassTransformer.class"])
PLUGIN = "com/akashic/thepatcher/core/AkashicThePatcherLoadingPlugin.class"
MOD = "com/akashic/thepatcher/AkashicThePatcherMod.class"
MANIFEST = "META-INF/MANIFEST.MF"
MCMOD = "mcmod.info"
EXPECTED_CHANGED = sorted([MANIFEST, MOD, PLUGIN, MCMOD])
TRANSFORMER = "com.akashic.thepatcher.module.neostates.NeoStatesWaterPassTransformer"
KILL = ["-Dakashic.neoStatesWaterPass.disable=true"]


def sha(data):
    return hashlib.sha256(data).hexdigest()


def major(data):
    return struct.unpack_from(">H", data, 6)[0]


def readzip(path):
    with zipfile.ZipFile(str(path)) as z:
        assert z.testzip() is None
        return {n: z.read(n) for n in z.namelist() if not n.endswith("/")}


def make_zip(content, target):
    stamp = (2026, 11, 16, 12, 0, 0)
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
    classes = work / "classes"
    stubs = compile_dir(args, ROOT / "stubs", work / "stubs")
    javac(args, "-Xlint:all,-options", "-Werror", "-cp", cp(BASE, libjars(args, RUNTIME), stubs), "-d", classes, *SOURCES)
    built = sorted(p.relative_to(classes).as_posix() for p in classes.rglob("*.class"))
    assert built == EXPECTED_ADDED, built

    tools = work / "tools"
    javac(args, "-cp", libjars(args, ASM9), "-d", tools, HERE / "PatchThePatcherVersion.java", HERE / "PatchBeta53Plugin.java")
    java21 = pathlib.Path(args.jdk21) / "bin/java"
    run([java21, "-Xverify:all", "-cp", cp(tools, libjars(args, ASM9)), "com.akashic.compatlab.tools.PatchThePatcherVersion",
         BASE, OLD, NEW, work / "mod/AkashicThePatcherMod.class"], "THEPATCHER_VERSION_PATCH PASS")
    run([java21, "-Xverify:all", "-cp", cp(tools, libjars(args, ASM9)), "com.akashic.compatlab.tools.PatchBeta53Plugin",
         BASE, work / "plugin/AkashicThePatcherLoadingPlugin.class"], "BETA53_PATCH PASS")

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
    fml = compile_dir(args, ROOT / "test/fml-stubs", work / "fml")
    blocks = compile_dir(args, ROOT / "test/gate-stubs", work / "blocks")
    mstubs = compile_dir(args, ROOT / "test/mixin-stubs", work / "mixin-stubs", blocks)
    neo = compile_dir(args, ROOT / "test/neo", work / "neo", blocks)
    mixin = compile_dir(args, ROOT / "test/shapes-mixin", work / "shapes-mixin", mstubs, blocks)
    fixed = compile_dir(args, ROOT / "test/shapes-mixin-fixed", work / "shapes-mixin-fixed", mstubs, blocks)
    dragon = compile_dir(args, ROOT / "test/shapes-dragon", work / "shapes-dragon", blocks)
    test = work / "test"
    javac(args, "-cp", cp(runtime, fml, blocks, neo), "-d", test,
          *(ROOT / "test" / n for n in ("TransformingLoader.java", "NeoStatesWaterPassGate.java", "RegistrationGate.java",
                                        "NoMixingasmGate.java")))
    javac(args, "-cp", libs / UNIMIXINS, "-d", test / "mixingasm", ROOT / "test/MixingasmTrustGate.java")
    javac(args, "-cp", libjars(args, ASM9), "-d", test / "asm9", ROOT / "test/MethodDiff.java")

    old_file, new_file = work / "old.class", work / "new.class"
    old_file.write_bytes(base[PLUGIN])
    new_file.write_bytes(new[PLUGIN])
    text = run([java8, "-cp", cp(test / "asm9", libjars(args, ASM9)), "MethodDiff", old_file, new_file], "METHOD_DIFF")
    differing = re.search(r"differing=\[(.*?)\]", text).group(1)
    assert re.fullmatch(r"\d+ getASMTransformerClass\(\)\[Ljava/lang/String;", differing), text
    report["method_diff." + PLUGIN] = text.strip()

    lists = {}
    for label, version_jar in (("beta52", BASE), ("beta53", jar)):
        for side in ("CLIENT", "SERVER"):
            text = run([java8, "-Xverify:all", "-Dstub.side=" + side, "-cp", cp(test, version_jar, fml, runtime),
                        "RegistrationGate"], "REGISTRATION side=" + side)
            lists[(label, side)] = re.search(r"list=\[(.*)\]", text).group(1).split(", ")
            report["registration.%s.%s" % (label, side)] = re.search(r"REGISTRATION.*", text).group(0)
    assert lists[("beta53", "CLIENT")] == lists[("beta52", "CLIENT")] + [TRANSFORMER], lists[("beta53", "CLIENT")]
    assert lists[("beta53", "SERVER")] == lists[("beta52", "SERVER")], lists[("beta53", "SERVER")]

    text = run([java8, "-Xverify:all", "-cp", cp(test / "mixingasm", libs / UNIMIXINS, jar, runtime),
                "MixingasmTrustGate", TRANSFORMER, "trusted"], "MIXINGASM_TRUST PASS")
    report["mixingasm.beta53"] = text.strip().splitlines()[-1]
    text = run([java8, "-Xverify:all", "-cp", cp(test, jar, runtime), "NoMixingasmGate", TRANSFORMER], "NO_MIXINGASM PASS")
    report["without_unimixins"] = text.strip().splitlines()[-1]

    # "installed" = on the class path the transformer resolves resources from
    full = [neo, mixin, dragon]
    cases = [
        ("beta53", jar, [], full, "patched"),
        ("beta53.disabled", jar, KILL, full, "unpatched"),
        ("beta53.angelica", jar, [], full + [libs / ANGELICA], "unpatched"),
        ("beta53.no-dragonapi", jar, [], [neo, mixin], "unpatched"),
        ("beta53.upstream-fixed", jar, [], [neo, fixed, dragon], "unpatched"),
        ("beta52", BASE, [], full, "unpatched"),
    ]
    for label, version_jar, flags, installed, expect in cases:
        for mode, shapes in (("mixin", mixin), ("dragon", dragon)):
            if mode == "dragon" and label == "beta53.no-dragonapi":
                continue
            text = run([java8, "-Xverify:all"] + flags + ["-cp", cp(test, version_jar, blocks, fml, runtime, *installed),
                        "NeoStatesWaterPassGate", mode, shapes, expect],
                       "NEOSTATES_WATER_PASS_GATE PASS mode=%s expect=%s" % (mode, expect))
            report["gate.%s.%s" % (label, mode)] = lines(text, "GATE", "NEOSTATES_WATER_PASS_GATE", "[Akashic-ThePatcher]")
    return report


def main():
    parser = argparse.ArgumentParser()
    for name in ("--jdk8", "--jdk21", "--libs", "--out"):
        parser.add_argument(name, required=True)
    parser.add_argument("--report", default=str(ROOT / "reports/final-build-audit.json"))
    args = parser.parse_args()

    assert sha(BASE.read_bytes()) == BASE_SHA, "unexpected beta.52 base"
    builds = []
    for _ in range(2):
        with tempfile.TemporaryDirectory(prefix="beta53-") as tmp:
            builds.append(build(args, pathlib.Path(tmp)))
    assert builds[0] == builds[1], "non-deterministic build"
    out = pathlib.Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_bytes(builds[0])
    with tempfile.TemporaryDirectory(prefix="beta53-gates-") as tmp:
        report = gates(args, out.resolve(), pathlib.Path(tmp))
    report = dict({
        "version": NEW,
        "base": {"path": str(BASE.relative_to(REPO)), "sha256": BASE_SHA},
        "output": {"name": out.name, "sha256": sha(builds[0]), "bytes": len(builds[0])},
        "deterministic_rebuild": "PASS",
    }, **report)
    pathlib.Path(args.report).parent.mkdir(parents=True, exist_ok=True)
    pathlib.Path(args.report).write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print("BETA53_BUILD PASS sha256=%s bytes=%d" % (sha(builds[0]), len(builds[0])))


if __name__ == "__main__":
    main()
