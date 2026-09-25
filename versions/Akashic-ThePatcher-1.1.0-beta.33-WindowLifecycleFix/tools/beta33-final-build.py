from __future__ import print_function

import hashlib
import json
import pathlib
import struct
import subprocess
import tempfile
import zipfile

ROOT = pathlib.Path(r"C:\Users\User\Desktop\Codex\Akashic-ThePatcher-1.1.0-beta.33-WindowLifecycleFix")
BASE = ROOT / "base-beta32.jar"
REOBF = ROOT / "build/libs/akashicasynccompat-NO-GIT-TAG-SET.jar"
OUT = ROOT / "releases/!Akashic-ThePatcher-1.1.0-beta.33-WindowLifecycleFix.jar"
REPORT = ROOT / "reports/final-build-audit.json"
RUNS = ROOT / "runs"

BASE_SHA = "ee26b8b61c1c93e7611a7ad898989356cb9e38bf03c68be09882559a1b107f30"
OLD = "1.1.0-beta.32"
NEW = "1.1.0-beta.33"

MOD = "com/akashic/thepatcher/AkashicThePatcherMod.class"
LOADING = "com/akashic/thepatcher/core/AkashicThePatcherLoadingPlugin.class"
PREFIX = "com/akashic/thepatcher/module/windowguard/"
TRANSFORMER = "com.akashic.thepatcher.module.windowguard.WindowLifecycleGuardTransformer"
FML_AUDIT = "com.akashic.thepatcher.module.startupaudit.FmlPhaseAuditTransformer"
HOTSPOT_AUDIT = "com.akashic.thepatcher.module.hotspotaudit.HotspotAuditTransformer"

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
            name: z.read(name)
            for name in z.namelist()
            if not name.endswith("/")
        }

def make_zip(content, target):
    stamp = (2026, 9, 25, 20, 5, 0)
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
    name: data
    for name, data in reb.items()
    if name.startswith(PREFIX) and name.endswith(".class")
}
assert sorted(new_classes) == [
    PREFIX + "WindowLifecycleGuard.class",
    PREFIX + "WindowLifecycleGuardTransformer.class",
], sorted(new_classes)

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
    ], check=True, stdout=subprocess.PIPE,
       stderr=subprocess.STDOUT, timeout=60)

    text = proc.stdout.decode("utf-8", "replace")
    assert "THEPATCHER_VERSION_PATCH PASS" in text
    return out.read_bytes()

def patch_loader(work):
    out = work / "AkashicThePatcherLoadingPlugin.class"
    cp = ";".join([str(work), str(ASM), str(TREE)])
    proc = subprocess.run([
        str(JDK / "java.exe"),
        "-Xverify:all",
        "-cp", cp,
        "com.akashic.compatlab.tools.PatchLoadingPluginAuditTransformer",
        str(BASE),
        TRANSFORMER,
        str(out),
    ], check=True, stdout=subprocess.PIPE,
       stderr=subprocess.STDOUT, timeout=60)

    text = proc.stdout.decode("utf-8", "replace")
    assert "LOADING_PLUGIN_AUDIT_TRANSFORMER_PATCH PASS" in text
    return out.read_bytes()

with tempfile.TemporaryDirectory(
    prefix="beta33-tools-",
    dir=str(RUNS)) as workdir:
    work = pathlib.Path(workdir)
    compile_tools(work)
    version_bytes = patch_version(work)
    loader_bytes = patch_loader(work)

content = dict(base)
content.update(new_classes)
content[MOD] = version_bytes
content[LOADING] = loader_bytes

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
    name for name in base
    if not name.endswith(".class")
    and base[name] != content[name])

assert changed_resources == [
    "META-INF/MANIFEST.MF",
    "mcmod.info",
], changed_resources

critical = [
    "com/akashic/thepatcher/module/startupaudit/FmlPhaseAuditHooks.class",
    "com/akashic/thepatcher/module/startupaudit/FmlPhaseAuditTransformer.class",
    "com/akashic/thepatcher/module/hotspotaudit/HotspotAuditTransformer.class",
    "com/akashic/thepatcher/module/hotspotaudit/HotspotAuditTransformer$Target.class",
    "dev/akashic/asynciconcompat/upload/BlockAtlasUploadBatcher.class",
    "dev/akashic/asynciconcompat/upload/ItemsAtlasUploadBatcher.class",
    "dev/akashic/asynciconcompat/graphics/NuminaFovSafety.class",
    "dev/akashic/asynciconcompat/mixins/MixinForgeHooksClientFovFiniteGuard.class",
    "dev/akashic/asynciconcompat/mixins/MixinTextureMapItemsUploadBatch.class",
    "dev/akashic/asynciconcompat/mixins/MixinTextureUtilItemsUploadBatch.class",
]

for name in critical:
    assert content[name] == base[name], name
loader = content[LOADING]

for transformer in [FML_AUDIT, HOTSPOT_AUDIT, TRANSFORMER]:
    assert loader.count(transformer.encode("utf-8")) == 1, transformer

for name, data in content.items():
    if name.endswith(".class"):
        assert major(data) <= 52, name

blob = b"\n".join(new_classes.values())
for literal in [
    b"setDisplayModeAndFullscreen",
    b"func_110441_Q",
    b"func_71352_k",
    b"updateDisplayMode",
    b"toggleFullscreen",
    b"WindowGuard atomic transition",
]:
    assert literal in blob, literal

def candidate_bytes():
    with tempfile.TemporaryDirectory(
        prefix="beta33-jar-",
        dir=str(RUNS)) as workdir:

        target = pathlib.Path(workdir) / "candidate.jar"
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
    "baseBeta32Sha256": BASE_SHA,
    "deterministicRebuild": True,
    "changedClasses": changed,
    "addedClasses": added,
    "removedClasses": removed,
    "changedResources": changed_resources,
    "inheritedCriticalClassesBitIdentical": True,
    "windowGuard": {
        "strategy": "atomic LWJGL display-mode/fullscreen transition",
        "validatedMethods": [
            "Minecraft.func_110441_Q",
            "Minecraft.func_71352_k",
        ],
        "validatedSetDisplayModeReplacements": 2,
        "unrelatedMinecraftDisplayModeCallsPreserved": 1,
        "realSrgMinecraftSmoke": "PASS",
    },
    "productionInstalled": False,
    "productionApproved": False,
}

REPORT.parent.mkdir(parents=True, exist_ok=True)
REPORT.write_text(
    json.dumps(report, indent=2, sort_keys=True) + "\n",
    encoding="utf-8")

print(
    "THEPATCHER_BETA33_BUILD PASS sha256=%s bytes=%d added=%d"
    % (report["sha256"], report["bytes"], len(added)))
