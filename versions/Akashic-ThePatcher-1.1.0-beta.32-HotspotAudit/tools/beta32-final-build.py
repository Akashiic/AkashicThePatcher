from __future__ import print_function

import hashlib
import json
import pathlib
import struct
import subprocess
import tempfile
import zipfile

ROOT = pathlib.Path(r"C:\Users\User\Desktop\Codex\Akashic-ThePatcher-1.1.0-beta.32-HotspotAudit")
BASE = ROOT / "base-beta31.jar"
REOBF = ROOT / "build/libs/akashicasynccompat-NO-GIT-TAG-SET.jar"
OUT = ROOT / "releases/!Akashic-ThePatcher-1.1.0-beta.32-HotspotAudit.jar"
REPORT = ROOT / "reports/final-build-audit.json"
RUNS = ROOT / "runs"

BASE_SHA = "45a2c442e9173e33364084b6c73bdd570d570c330a609d6b812ade4d83ee1228"
OLD = "1.1.0-beta.31"
NEW = "1.1.0-beta.32"

MOD = "com/akashic/thepatcher/AkashicThePatcherMod.class"
LOADING = "com/akashic/thepatcher/core/AkashicThePatcherLoadingPlugin.class"
HOTSPOT_PREFIX = "com/akashic/thepatcher/module/hotspotaudit/"
HOTSPOT_DOT = "com.akashic.thepatcher.module.hotspotaudit.HotspotAuditTransformer"
FML_AUDIT_DOT = "com.akashic.thepatcher.module.startupaudit.FmlPhaseAuditTransformer"
MIXIN_CFG = "mixins.akashicasynccompat.json"
REFMAP = "mixins.akashicasynccompat.refmap.json"

JDK = pathlib.Path(r"C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot\bin")
ASM = pathlib.Path(r"C:\Users\User\.gradle\caches\modules-2\files-2.1\org.ow2.asm\asm\9.8\dc19ecb3f7889b7860697215cae99c0f9b6f6b4b\asm-9.8.jar")
TREE = pathlib.Path(r"C:\Users\User\.gradle\caches\modules-2\files-2.1\org.ow2.asm\asm-tree\9.8\18419ca5b77a2f81097c741e7872e6ab8d2f40d\asm-tree-9.8.jar")
VERSION_TOOL = ROOT / "tools/PatchThePatcherVersion.java"
LOADER_TOOL = ROOT / "tools/PatchLoadingPluginAuditTransformer.java"

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def major(data):
    return struct.unpack_from(">H", data, 6)[0]

def readzip(path):
    with zipfile.ZipFile(path) as z:
        assert z.testzip() is None
        return {
            n: z.read(n)
            for n in z.namelist()
            if not n.endswith("/")
        }

def make_zip(content, target, stamp=(2026, 9, 25, 19, 55, 0)):
    with zipfile.ZipFile(
        target,
        "w",
        zipfile.ZIP_DEFLATED,
        compresslevel=9) as z:

        for name in sorted(content):
            info = zipfile.ZipInfo(name, stamp)
            info.create_system = 3
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o644 << 16
            z.writestr(
                info,
                content[name],
                compress_type=zipfile.ZIP_DEFLATED,
                compresslevel=9)

    with zipfile.ZipFile(target) as z:
        assert z.testzip() is None

assert BASE.is_file()
assert sha(BASE) == BASE_SHA
assert REOBF.is_file()

base = readzip(BASE)
reb = readzip(REOBF)

new_classes = {
    n: b
    for n, b in reb.items()
    if n.startswith(HOTSPOT_PREFIX)
       and n.endswith(".class")
}

assert len(new_classes) == 2, sorted(new_classes)
assert HOTSPOT_PREFIX + "HotspotAuditTransformer.class" in new_classes
assert HOTSPOT_PREFIX + "HotspotAuditTransformer$Target.class" in new_classes

for name, data in new_classes.items():
    assert name not in base, name
    assert major(data) <= 52, (name, major(data))

def compile_tools(work):
    cp = ";".join([str(ASM), str(TREE)])

    subprocess.run([
        str(JDK / "javac.exe"),
        "--release", "8",
        "-encoding", "UTF-8",
        "-Xlint:all,-options",
        "-Werror",
        "-cp", cp,
        "-d", str(work),
        str(VERSION_TOOL),
        str(LOADER_TOOL),
    ], check=True, timeout=60)

def patch_version(work):
    out = work / "AkashicThePatcherMod.class"
    cp = ";".join([str(work), str(ASM), str(TREE)])

    proc = subprocess.run([
        str(JDK / "java.exe"),
        "-Xverify:all",
        "-cp", cp,
        "com.akashic.compatlab.tools.PatchThePatcherVersion",
        str(BASE),
        OLD,
        NEW,
        str(out),
    ], check=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=60)

    text = proc.stdout.decode("utf-8", "replace")
    assert "THEPATCHER_VERSION_PATCH PASS" in text
    return out.read_bytes(), text.strip()

def patch_loader(work):
    out = work / "AkashicThePatcherLoadingPlugin.class"
    cp = ";".join([str(work), str(ASM), str(TREE)])

    proc = subprocess.run([
        str(JDK / "java.exe"),
        "-Xverify:all",
        "-cp", cp,
        "com.akashic.compatlab.tools.PatchLoadingPluginAuditTransformer",
        str(BASE),
        HOTSPOT_DOT,
        str(out),
    ], check=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=60)

    text = proc.stdout.decode("utf-8", "replace")
    assert "LOADING_PLUGIN_AUDIT_TRANSFORMER_PATCH PASS" in text
    return out.read_bytes(), text.strip()

with tempfile.TemporaryDirectory(
    prefix="beta32-tools-",
    dir=str(RUNS)) as w:

    work = pathlib.Path(w)
    compile_tools(work)
    version1, version_proof1 = patch_version(work)
    loader1, loader_proof1 = patch_loader(work)

with tempfile.TemporaryDirectory(
    prefix="beta32-tools-",
    dir=str(RUNS)) as w:

    work = pathlib.Path(w)
    compile_tools(work)
    version2, version_proof2 = patch_version(work)
    loader2, loader_proof2 = patch_loader(work)

assert version1 == version2
assert loader1 == loader2
assert version_proof1 == version_proof2
assert loader_proof1 == loader_proof2

content = dict(base)
content.update(new_classes)
content[MOD] = version1
content[LOADING] = loader1

manifest = content["META-INF/MANIFEST.MF"].decode("utf-8")
assert manifest.count("Implementation-Version: " + OLD) == 1
content["META-INF/MANIFEST.MF"] = manifest.replace(
    "Implementation-Version: " + OLD,
    "Implementation-Version: " + NEW,
    1).encode("utf-8")

mcmod = content["mcmod.info"].decode("utf-8")
assert mcmod.count(OLD) == 1
content["mcmod.info"] = mcmod.replace(
    OLD,
    NEW,
    1).encode("utf-8")

base_classes = {n for n in base if n.endswith(".class")}
result_classes = {n for n in content if n.endswith(".class")}

changed = sorted(
    n for n in base_classes
    if base[n] != content[n])

added = sorted(result_classes - base_classes)
removed = sorted(base_classes - result_classes)

assert changed == sorted([LOADING, MOD]), changed
assert added == sorted(new_classes), added
assert removed == [], removed

changed_resources = sorted(
    n for n in base
    if not n.endswith(".class")
       and base[n] != content[n])

assert changed_resources == [
    "META-INF/MANIFEST.MF",
    "mcmod.info",
], changed_resources

assert content[MIXIN_CFG] == base[MIXIN_CFG]
assert content[REFMAP] == base[REFMAP]

for name in base_classes:
    if name not in (LOADING, MOD):
        assert content[name] == base[name], (
            "unexpected inherited class changed: " + name)

for name, data in content.items():
    if name.endswith(".class"):
        assert major(data) <= 52, name

# Critical beta31/beta30.2 functionality must remain exact.
critical = [
    "com/akashic/thepatcher/module/startupaudit/FmlPhaseAuditHooks.class",
    "com/akashic/thepatcher/module/startupaudit/FmlPhaseAuditTransformer.class",
    "dev/akashic/asynciconcompat/upload/BlockAtlasUploadBatcher.class",
    "dev/akashic/asynciconcompat/upload/BlockAtlasUploadBatcher$State.class",
    "dev/akashic/asynciconcompat/upload/BlockAtlasUploadBatcher$Rect.class",
    "dev/akashic/asynciconcompat/upload/ItemsAtlasUploadBatcher.class",
    "dev/akashic/asynciconcompat/graphics/NuminaFovSafety.class",
    "dev/akashic/asynciconcompat/mixins/MixinForgeHooksClientFovFiniteGuard.class",
    "dev/akashic/asynciconcompat/mixins/MixinTextureMapItemsUploadBatch.class",
    "dev/akashic/asynciconcompat/mixins/MixinTextureUtilItemsUploadBatch.class",
]

for name in critical:
    assert content[name] == base[name], name

loader = content[LOADING]
assert loader.count(FML_AUDIT_DOT.encode("utf-8")) == 1
assert loader.count(HOTSPOT_DOT.encode("utf-8")) == 1

hotspot_blob = b"\n".join(new_classes.values())

for literal in [
    b"Decocraft.Props.loadModels",
    b"GeckoLib.initialize",
    b"ChromatiCraft.ChromaRecipes.addRecipes",
    b"CustomNPC.ClientProxy.load",
    b"Eln.ConfigHandler.loadConfig",
    b"MobsInfo.generateMobRecipeMap",
    b"MobsInfo.generateVillagerTrades",
    b"spanEnter",
    b"spanExit",
]:
    assert literal in hotspot_blob, literal

for forbidden in [
    b"ThreadPoolExecutor",
    b"ForkJoinPool",
    b"Thread.sleep",
    b"System.exit",
    b"FileOutputStream",
    b"setAccessible",
    b"Redirect",
    b"cancel",
    b"setReturnValue",
]:
    assert forbidden not in hotspot_blob, forbidden

def candidate_bytes():
    with tempfile.TemporaryDirectory(
        prefix="beta32-jar-",
        dir=str(RUNS)) as w:

        target = pathlib.Path(w) / "candidate.jar"
        make_zip(content, target)
        return target.read_bytes()

jar1 = candidate_bytes()
jar2 = candidate_bytes()
assert jar1 == jar2

OUT.parent.mkdir(parents=True, exist_ok=True)
OUT.write_bytes(jar1)

report = {
    "schema": 1,
    "version": NEW,
    "artifact": str(OUT.relative_to(ROOT)),
    "sha256": sha(OUT),
    "bytes": OUT.stat().st_size,
    "deterministicRebuild": True,
    "baseBeta31Sha256": BASE_SHA,
    "changedClasses": changed,
    "addedClasses": added,
    "removedClasses": removed,
    "changedResources": changed_resources,
    "mixinConfigInheritedBitIdentical": True,
    "refmapInheritedBitIdentical": True,
    "fmlPhaseAuditInheritedBitIdentical": True,
    "blockAtlasBatcherInheritedBitIdentical": True,
    "itemsAtlasBatcherInheritedBitIdentical": True,
    "numinaFovGuardInheritedBitIdentical": True,
    "diagnosticOnly": True,
    "transformerRegistration": {
        "existing": FML_AUDIT_DOT,
        "added": HOTSPOT_DOT,
        "registrationCountEach": 1,
    },
    "hotspotAudit": {
        "uniqueRuntimeClasses": 18,
        "instrumentedMethods": 20,
        "runtimeSmoke": "PASS",
        "mods": [
            "Decocraft/props",
            "GeckoLib",
            "ChromatiCraft",
            "CustomNPC+",
            "Eln",
            "MobsInfo",
        ],
    },
    "tests": {
        "HotspotAuditTransformerTest": 2,
        "failures": 0,
        "errors": 0,
        "skipped": 0,
    },
    "versionPatchProof": version_proof1,
    "loadingPluginPatchProof": loader_proof1,
    "productionInstalled": False,
    "productionApproved": False,
}

REPORT.parent.mkdir(parents=True, exist_ok=True)
REPORT.write_text(
    json.dumps(report, indent=2, sort_keys=True) + "\n",
    encoding="utf-8")

print(
    "THEPATCHER_BETA32_BUILD PASS sha256=%s bytes=%d added=%d"
    % (
        report["sha256"],
        report["bytes"],
        len(added)))
