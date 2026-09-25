from __future__ import print_function

import hashlib
import json
import pathlib
import struct
import subprocess
import tempfile
import zipfile

ROOT = pathlib.Path(
    r"C:\Users\User\Desktop\Codex\Akashic-ThePatcher-1.1.0-beta.25-TerrainBlendAlphaProbe")
BASE = ROOT / "base-beta23.jar"
COMP = ROOT / "terrain-blend-alpha2.14.jar"
OUT = ROOT / "releases/!Akashic-ThePatcher-1.1.0-beta.25-TerrainBlendAlphaProbe.jar"
REPORT = ROOT / "reports/final-build-audit.json"

BASE_SHA = "5a3c563b61f26a24d84b3b4fe89e626afa82e87f35e1ccd51bd9e39acd6097c3"
COMP_SHA = "40476388facbfcc0fbaf95ed0bac688dbe3aa5f9b69e18b59702183638535e9f"
OLD = "1.1.0-beta.23"
NEW = "1.1.0-beta.25"
MOD = "com/akashic/thepatcher/AkashicThePatcherMod.class"
MIXIN_CFG = "mixins.akashicasynccompat.json"
REFMAP = "mixins.akashicasynccompat.refmap.json"

NEW_CLASSES = [
    "dev/akashic/asynciconcompat/graphics/TerrainSemanticBlendProbe.class",
    "dev/akashic/asynciconcompat/graphics/TerrainVertexAlphaProbe.class",
    "dev/akashic/asynciconcompat/mixins/MixinAngelicaChunkBuildContextVertexAlphaProbe.class",
    "dev/akashic/asynciconcompat/mixins/MixinCeleritasTerrainSemanticBlendProbe.class",
]

JDK = pathlib.Path(
    r"C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot\bin")
ASM = pathlib.Path(
    r"C:\Users\User\.gradle\caches\modules-2\files-2.1\org.ow2.asm\asm\9.8"
    r"\dc19ecb3f7889b7860697215cae99c0f9b6f6b4b\asm-9.8.jar")
TREE = pathlib.Path(
    r"C:\Users\User\.gradle\caches\modules-2\files-2.1\org.ow2.asm\asm-tree\9.8"
    r"\18419ca5b77a2f81097c741e7872e6ab8d2f40d\asm-tree-9.8.jar")
TOOL = ROOT / "tools/PatchThePatcherVersion.java"
RUNS = ROOT / "runs"

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def major(data):
    return struct.unpack_from(">H", data, 6)[0]

assert sha(BASE) == BASE_SHA
assert sha(COMP) == COMP_SHA

with zipfile.ZipFile(BASE) as z:
    base = {n: z.read(n) for n in z.namelist() if not n.endswith("/")}
with zipfile.ZipFile(COMP) as z:
    component = {n: z.read(n) for n in z.namelist() if not n.endswith("/")}

for name in NEW_CLASSES:
    assert name in component, name

assert component[REFMAP] == base[REFMAP], "refmap unexpectedly changed"
cfg = component[MIXIN_CFG]
assert b"MixinCeleritasTerrainSemanticBlendProbe" in cfg
assert b"MixinAngelicaChunkBuildContextVertexAlphaProbe" in cfg
assert b"MixinCeleritasWorldStateProbe" not in cfg
assert b"MixinRenderRegionManagerStagingFallback" not in cfg

def patch_version():
    with tempfile.TemporaryDirectory(prefix="beta25-version-", dir=RUNS) as work:
        work = pathlib.Path(work)
        cp = ";".join(str(x) for x in (ASM, TREE))
        subprocess.run([
            str(JDK / "javac.exe"), "--release", "8", "-encoding", "UTF-8",
            "-Xlint:all,-options", "-Werror", "-cp", cp,
            "-d", str(work), str(TOOL)
        ], check=True, timeout=60)

        out = work / "AkashicThePatcherMod.class"
        cp2 = ";".join([str(work), str(ASM), str(TREE)])
        proc = subprocess.run([
            str(JDK / "java.exe"), "-Xverify:all", "-cp", cp2,
            "com.akashic.compatlab.tools.PatchThePatcherVersion",
            str(BASE), OLD, NEW, str(out)
        ], check=True, stdout=subprocess.PIPE,
           stderr=subprocess.STDOUT, timeout=60)
        text = proc.stdout.decode("utf-8", "replace")
        assert "THEPATCHER_VERSION_PATCH PASS" in text
        return out.read_bytes(), text.strip()

v1, proof1 = patch_version()
v2, proof2 = patch_version()
assert v1 == v2

content = dict(base)
for name in NEW_CLASSES:
    content[name] = component[name]
content[MIXIN_CFG] = cfg
content[MOD] = v1

manifest = content["META-INF/MANIFEST.MF"].decode("utf-8")
assert manifest.count("Implementation-Version: " + OLD) == 1
content["META-INF/MANIFEST.MF"] = manifest.replace(
    "Implementation-Version: " + OLD,
    "Implementation-Version: " + NEW, 1).encode("utf-8")

mcmod = content["mcmod.info"].decode("utf-8")
assert mcmod.count(OLD) == 1
content["mcmod.info"] = mcmod.replace(OLD, NEW, 1).encode("utf-8")

base_classes = {n for n in base if n.endswith(".class")}
result_classes = {n for n in content if n.endswith(".class")}
changed = sorted(n for n in base_classes if base[n] != content[n])
added = sorted(result_classes - base_classes)
removed = sorted(base_classes - result_classes)

assert changed == [MOD], changed
assert added == sorted(NEW_CLASSES), added
assert removed == [], removed

changed_res = sorted(
    n for n in base
    if not n.endswith(".class") and base[n] != content[n])
assert changed_res == [
    "META-INF/MANIFEST.MF", "mcmod.info", MIXIN_CFG
], changed_res

for name, data in content.items():
    if name.endswith(".class"):
        assert major(data) <= 52, name

assert not any("WorldRenderStateProbe" in n for n in content)
assert not any("MixinCeleritasWorldStateProbe" in n for n in content)
assert not any("StagingFallback" in n for n in content)

for prefix in [
    "org/embeddedt/embeddium/",
    "com/gtnewhorizons/angelica/",
    "com/gtnewhorizon/gtnhlib/",
]:
    assert not any(n.startswith(prefix) for n in content), prefix

for literal in [
    b"TerrainBlend", b"forceOpaqueTerrainBlendOff",
    b"TerrainVertexAlpha", b"terrainVertexAlphaProbe"
]:
    assert any(
        literal in content[n] for n in NEW_CLASSES
    ), literal

def make():
    with tempfile.TemporaryDirectory(prefix="beta25-jar-", dir=RUNS) as work:
        target = pathlib.Path(work) / "candidate.jar"
        with zipfile.ZipFile(
            target, "w", zipfile.ZIP_DEFLATED, compresslevel=9
        ) as z:
            for name in sorted(content):
                info = zipfile.ZipInfo(name, (2026, 9, 24, 23, 30, 0))

                info.create_system = 3
                info.compress_type = zipfile.ZIP_DEFLATED
                info.external_attr = 0o644 << 16
                z.writestr(
                    info, content[name],
                    compress_type=zipfile.ZIP_DEFLATED,
                    compresslevel=9)
        with zipfile.ZipFile(target) as z:
            assert z.testzip() is None
        return target.read_bytes()

j1 = make()
j2 = make()
assert j1 == j2
OUT.parent.mkdir(parents=True, exist_ok=True)
OUT.write_bytes(j1)

report = {
    "schema": 1,
    "version": NEW,
    "artifact": str(OUT.relative_to(ROOT)),
    "sha256": sha(OUT),
    "bytes": OUT.stat().st_size,
    "deterministicRebuild": True,
    "baseBeta23Sha256": BASE_SHA,
    "componentAlpha214Sha256": COMP_SHA,
    "changedClasses": changed,
    "addedClasses": added,
    "removedClasses": removed,

    "changedResources": changed_res,
    "scope": (
        "probe absolute terrain blend semantics, force GL_BLEND off "
        "for opaque terrain layers, and sample pre-encoder vertex alpha"
    ),
    "blendGuardDefaultEnabled": True,
    "blendGuardDisableProperty":
        "-Dakashic.forceOpaqueTerrainBlendOff=false",
    "vertexAlphaProbeDefaultEnabled": True,
    "vertexAlphaProbeDisableProperty":
        "-Dakashic.terrainVertexAlphaProbe=false",
    "stagingFallbackPresent": False,
    "worldGlStateProbePresent": False,
    "versionPatchProof": proof1,
    "productionInstalled": False,
    "productionApproved": False,
}
REPORT.parent.mkdir(parents=True, exist_ok=True)
REPORT.write_text(
    json.dumps(report, indent=2, sort_keys=True) + "\n",
    encoding="utf-8")
print(
    "THEPATCHER_BETA25_BUILD PASS sha256=%s bytes=%d"
    % (report["sha256"], report["bytes"]))
