from __future__ import print_function
import hashlib, json, pathlib, struct, subprocess, tempfile, zipfile

ROOT = pathlib.Path(r"C:\Users\User\Desktop\Codex\Akashic-ThePatcher-1.1.0-beta.25-TerrainSemanticProbe")
BASE = ROOT / "base-beta23.jar"
COMP = ROOT / "terrain-semantic-alpha2.14.jar"
OUT = ROOT / "releases/!Akashic-ThePatcher-1.1.0-beta.25-TerrainSemanticProbe.jar"
REPORT = ROOT / "reports/final-build-audit.json"

BASE_SHA = "5a3c563b61f26a24d84b3b4fe89e626afa82e87f35e1ccd51bd9e39acd6097c3"
COMP_SHA = "97987d9549732d8b37a67d4972ea8c93c98d258e0599fe9c3c584ab8ce178c35"
OLD = "1.1.0-beta.23"
NEW = "1.1.0-beta.25"
MOD = "com/akashic/thepatcher/AkashicThePatcherMod.class"
MIXIN_CFG = "mixins.akashicasynccompat.json"

NEW_MIXINS = [
    "dev/akashic/asynciconcompat/mixins/MixinTerrainRenderPassSemanticBlend.class",
    "dev/akashic/asynciconcompat/mixins/MixinAngelicaChunkBuildContextVertexAlpha.class",
]

JDK = pathlib.Path(r"C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot\bin")
ASM = pathlib.Path(r"C:\Users\User\.gradle\caches\modules-2\files-2.1\org.ow2.asm\asm\9.8\dc19ecb3f7889b7860697215cae99c0f9b6f6b4b\asm-9.8.jar")
TREE = pathlib.Path(r"C:\Users\User\.gradle\caches\modules-2\files-2.1\org.ow2.asm\asm-tree\9.8\18419ca5b77a2f81097c741e7872e6ab8d2f40d\asm-tree-9.8.jar")
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

for name in NEW_MIXINS:
    assert name in component, name

cfg = component[MIXIN_CFG]
assert b"MixinTerrainRenderPassSemanticBlend" in cfg
assert b"MixinAngelicaChunkBuildContextVertexAlpha" in cfg
assert b"MixinRenderRegionManagerStagingFallback" not in cfg
assert b"MixinCeleritasWorldStateProbe" not in cfg

def patch_version():
    RUNS.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="beta25-version-", dir=RUNS) as tmp:
        tmp = pathlib.Path(tmp)
        cp = ";".join(str(x) for x in (ASM, TREE))
        subprocess.run(
            [str(JDK / "javac.exe"), "--release", "8", "-encoding", "UTF-8",
             "-Xlint:all,-options", "-Werror", "-cp", cp, "-d", str(tmp), str(TOOL)],
            check=True, timeout=60)
        out = tmp / "AkashicThePatcherMod.class"
        cp2 = ";".join([str(tmp), str(ASM), str(TREE)])
        proc = subprocess.run(
            [str(JDK / "java.exe"), "-Xverify:all", "-cp", cp2,
             "com.akashic.compatlab.tools.PatchThePatcherVersion",
             str(BASE), OLD, NEW, str(out)],
            check=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=60)
        text = proc.stdout.decode("utf-8", "replace")
        assert "THEPATCHER_VERSION_PATCH PASS" in text
        return out.read_bytes(), text.strip()

v1, p1 = patch_version()
v2, p2 = patch_version()
assert v1 == v2

content = dict(base)
for name in NEW_MIXINS:
    content[name] = component[name]
content[MIXIN_CFG] = cfg
content[MOD] = v1

manifest = content["META-INF/MANIFEST.MF"].decode("utf-8")
needle = "Implementation-Version: " + OLD
assert manifest.count(needle) == 1
content["META-INF/MANIFEST.MF"] = manifest.replace(
    needle, "Implementation-Version: " + NEW, 1).encode("utf-8")

mcmod = content["mcmod.info"].decode("utf-8")
assert mcmod.count(OLD) == 1
content["mcmod.info"] = mcmod.replace(OLD, NEW, 1).encode("utf-8")

base_classes = {n for n in base if n.endswith(".class")}
result_classes = {n for n in content if n.endswith(".class")}
changed = sorted(n for n in base_classes if base[n] != content[n])
added = sorted(result_classes - base_classes)
removed = sorted(base_classes - result_classes)

assert changed == [MOD], changed
assert added == sorted(NEW_MIXINS), added
assert removed == [], removed

changed_resources = sorted(
    n for n in base
    if not n.endswith(".class") and base[n] != content[n])
assert changed_resources == [
    "META-INF/MANIFEST.MF",
    "mcmod.info",
    MIXIN_CFG,
], changed_resources

for name, data in content.items():
    if name.endswith(".class"):
        assert major(data) <= 52, name

preserved = [
    "com/akashic/thepatcher/module/forestry/ForestryC2RuntimeGuard.class",
    "com/akashic/thepatcher/module/forestry/ForestryGenomeCompat.class",
    "com/akashic/thepatcher/module/forestry/ForestryGenomeC2Transformer.class",
    "dev/akashic/asynciconcompat/CompatHooks.class",
    "dev/akashic/asynciconcompat/CompatState.class",
    "dev/akashic/asynciconcompat/cache/StitchLayoutCache.class",
    "dev/akashic/asynciconcompat/mixins/MixinTextureMapHybridAsync.class",
    "dev/akashic/asynciconcompat/mixins/MixinStitcherLayoutCache.class",

    "dev/akashic/asynciconcompat/mixins/MixinTextureUtilUploadProfiler.class",
    "dev/akashic/asynciconcompat/mixins/MixinAngelicaMipmapProfiler.class",
    "dev/akashic/asynciconcompat/upload/ItemsAtlasUploadBatcher.class",
    "dev/akashic/asynciconcompat/upload/ItemsAtlasUploadBatcher$State.class",
    "dev/akashic/asynciconcompat/mixins/MixinTextureMapItemsUploadBatch.class",
    "dev/akashic/asynciconcompat/mixins/MixinTextureUtilItemsUploadBatch.class",
]
for name in preserved:
    assert content[name] == base[name], name

for forbidden in [
    "dev/akashic/asynciconcompat/graphics/WorldRenderStateProbe.class",
    "dev/akashic/asynciconcompat/mixins/MixinCeleritasWorldStateProbe.class",
    "dev/akashic/asynciconcompat/mixins/MixinRenderRegionManagerStagingFallback.class",
]:
    assert forbidden not in content, forbidden

for prefix in [
    "org/embeddedt/embeddium/",
    "com/gtnewhorizons/angelica/",
    "com/gtnewhorizon/gtnhlib/",
]:
    assert not any(n.startswith(prefix) for n in content), prefix

def make_jar():
    RUNS.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="beta25-jar-", dir=RUNS) as tmp:
        path = pathlib.Path(tmp) / "candidate.jar"
        with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as z:
            for name in sorted(content):
                info = zipfile.ZipInfo(name, (2026, 9, 24, 22, 50, 0))
                info.create_system = 3
                info.compress_type = zipfile.ZIP_DEFLATED
                info.external_attr = 0o644 << 16
                z.writestr(info, content[name], compresslevel=9)
        with zipfile.ZipFile(path) as z:
            assert z.testzip() is None
        return path.read_bytes()

j1 = make_jar()
j2 = make_jar()
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
    "changedResources": changed_resources,
    "scope": "probe semantic terrain blend after pass setup and sample raw terrain vertex alpha",
    "forceOpaqueTerrainBlendOffDefault": True,
    "disableBlendGuardProperty": "-Dakashic.forceOpaqueTerrainBlendOff=false",
    "stagingFallbackPresent": False,
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
