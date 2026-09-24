from __future__ import print_function
import hashlib, json, pathlib, struct, subprocess, tempfile, zipfile

ROOT = pathlib.Path(r"C:\Users\User\Desktop\Codex\Akashic-ThePatcher-1.1.0-beta.21-WorldRenderStateRepair")
JDK = pathlib.Path(r"C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot\bin")
BASE = ROOT / "base-beta20.jar"
BASE_SHA = "00a29e58ebb428e915d3579ba3f6b9bceb6464d29d4f8c08ef7fe20285e3a48a"
COMP = ROOT / "worldrender-alpha2.11.jar"
COMP_SHA = "ff9e1a9d966fb2c51b60397e96c352b7577d7202cf9eb2389a4700590f386e7d"
ASM = pathlib.Path(r"C:\Users\User\.gradle\caches\modules-2\files-2.1\org.ow2.asm\asm\9.8\dc19ecb3f7889b7860697215cae99c0f9b6f6b4b\asm-9.8.jar")
TREE = pathlib.Path(r"C:\Users\User\.gradle\caches\modules-2\files-2.1\org.ow2.asm\asm-tree\9.8\18419ca5b77a2f81097c741e7872e6ab8d2f40d\asm-tree-9.8.jar")
VERSION_TOOL = ROOT / "tools/PatchThePatcherVersion.java"
RUNS = ROOT / "runs"
OUT = ROOT / "releases/!Akashic-ThePatcher-1.1.0-beta.21-WorldRenderStateRepair.jar"
REPORT = ROOT / "reports/final-build-audit.json"

OLD_VERSION = "1.1.0-beta.20"
NEW_VERSION = "1.1.0-beta.21"
MOD_ENTRY = "com/akashic/thepatcher/AkashicThePatcherMod.class"
MIXIN_CFG = "mixins.akashicasynccompat.json"

GRAPHICS_ENTRIES = [
    "dev/akashic/asynciconcompat/graphics/WorldRenderStateProbe.class",
    "dev/akashic/asynciconcompat/mixins/MixinCeleritasWorldStateProbe.class",
]

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def major(data):
    return struct.unpack_from(">H", data, 6)[0]

if sha(BASE) != BASE_SHA:
    raise ValueError("beta.20 base identity changed")
if sha(COMP) != COMP_SHA:
    raise ValueError("alpha.2.11 identity changed")

with zipfile.ZipFile(BASE) as z:
    base = {n: z.read(n) for n in z.namelist() if not n.endswith("/")}
with zipfile.ZipFile(COMP) as z:
    comp_names = set(z.namelist())
    for entry in GRAPHICS_ENTRIES:
        if entry not in comp_names:
            raise ValueError("graphics entry missing: " + entry)
    graphics = {entry: z.read(entry) for entry in GRAPHICS_ENTRIES}
    graphics[MIXIN_CFG] = z.read(MIXIN_CFG)

startup_entries = sorted(
    n for n in base
    if (
        n == "dev/akashic/asynciconcompat/mixins/MixinLoadControllerStartupProfiler.class"
        or n.startswith("dev/akashic/asynciconcompat/startup/StartupLifecycleProfiler")
    )
    and n.endswith(".class")
)
if len(startup_entries) != 7:
    raise ValueError("unexpected beta20 startup profiler closure: " + repr(startup_entries))

def compile_tool(folder):
    cp = ";".join(str(p) for p in (ASM, TREE))
    subprocess.run([
        str(JDK / "javac.exe"), "--release", "8", "-encoding", "UTF-8",
        "-Xlint:all,-options", "-Werror", "-cp", cp, "-d", str(folder),
        str(VERSION_TOOL),
    ], check=True, timeout=60)

def patch_version():
    with tempfile.TemporaryDirectory(prefix="patcher-beta21-version-", dir=RUNS) as d:
        d = pathlib.Path(d)
        tools = d / "tools"
        tools.mkdir()
        compile_tool(tools)
        dst = d / "AkashicThePatcherMod.class"
        cp = ";".join(str(p) for p in (tools, ASM, TREE))
        proc = subprocess.run([
            str(JDK / "java.exe"), "-Xverify:all", "-cp", cp,
            "com.akashic.compatlab.tools.PatchThePatcherVersion",
            str(BASE), OLD_VERSION, NEW_VERSION, str(dst),
        ], check=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=60)
        text = proc.stdout.decode("utf-8", errors="replace")
        if "THEPATCHER_VERSION_PATCH PASS" not in text:
            raise ValueError("version proof missing: " + text[-2000:])
        return dst.read_bytes(), text.strip()

version1, proof1 = patch_version()
version2, proof2 = patch_version()
if version1 != version2:
    raise ValueError("non-deterministic version patch")

content = dict(base)
for entry in startup_entries:
    del content[entry]
content.update(graphics)
content[MOD_ENTRY] = version1

manifest = content["META-INF/MANIFEST.MF"].decode("utf-8")
old_line = "Implementation-Version: " + OLD_VERSION
new_line = "Implementation-Version: " + NEW_VERSION
if manifest.count(old_line) != 1:
    raise ValueError("manifest version shape changed")
content["META-INF/MANIFEST.MF"] = manifest.replace(old_line, new_line, 1).encode("utf-8")

mcmod = content["mcmod.info"].decode("utf-8")
if mcmod.count(OLD_VERSION) != 1:
    raise ValueError("mcmod version shape changed")
content["mcmod.info"] = mcmod.replace(OLD_VERSION, NEW_VERSION, 1).encode("utf-8")

base_classes = {n for n in base if n.endswith(".class")}
result_classes = {n for n in content if n.endswith(".class")}
changed_classes = sorted(
    n for n in base_classes if n in content and base[n] != content[n]
)
added_classes = sorted(result_classes - base_classes)
removed_classes = sorted(base_classes - result_classes)

if changed_classes != [MOD_ENTRY]:
    raise ValueError("unexpected changed classes: " + repr(changed_classes))
if added_classes != sorted(GRAPHICS_ENTRIES):
    raise ValueError("unexpected added classes: " + repr(added_classes))
if removed_classes != startup_entries:
    raise ValueError("unexpected removed classes: " + repr(removed_classes))

changed_resources = sorted(
    n for n in base
    if not n.endswith(".class") and n in content and base[n] != content[n]
)
if changed_resources != ["META-INF/MANIFEST.MF", "mcmod.info", MIXIN_CFG]:
    raise ValueError("unexpected changed resources: " + repr(changed_resources))

for name in result_classes:
    if major(content[name]) > 52:
        raise ValueError("Java 8 target broken: " + name)

# Existing accepted behavior is byte-identical.
preserved = [
    "com/akashic/thepatcher/core/AkashicThePatcherLoadingPlugin.class",
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
for entry in preserved:
    if content[entry] != base[entry]:
        raise ValueError("preserved class changed: " + entry)

cfg = content[MIXIN_CFG].decode("utf-8")
if "MixinLoadControllerStartupProfiler" in cfg:
    raise ValueError("failed startup profiler remains in mixin config")
if "MixinCeleritasWorldStateProbe" not in cfg:
    raise ValueError("graphics probe mixin absent")
if "MixinForestryGenomeC2" in cfg:
    raise ValueError("rejected Forestry Mixin leaked")

probe = content[GRAPHICS_ENTRIES[0]]
for literal in [
    b"WorldGLState new-world",
    b"mismatches=",
    b"repaired=",
    b"akashic.worldRenderStateProbe",
    b"akashic.worldRenderStateRepair",
    b"textureBinding2D",
    b"depthMask",
    b"colorMask",
    b"blendFunc",
    b"currentColor",
    b"alphaFunc",
]:
    if literal not in probe:
        raise ValueError("probe marker missing: " + repr(literal))

mixin = content[GRAPHICS_ENTRIES[1]]
for literal in [
    b"CeleritasWorldRenderer",
    b"drawChunkLayer",
    b"BlockRenderLayer",
]:
    if literal not in mixin:
        raise ValueError("graphics mixin marker missing: " + repr(literal))

# Compile-only renderer dependencies must never be bundled.
for prefix in [
    "com/gtnewhorizons/angelica/",
    "com/gtnewhorizon/gtnhlib/",
    "org/embeddedt/embeddium/",
]:
    leaked = [
        n for n in content
        if n.startswith(prefix)
        and n not in GRAPHICS_ENTRIES
    ]
    if leaked:
        raise ValueError("renderer dependency leaked: " + repr(leaked[:5]))

def archive_bytes():
    with tempfile.TemporaryDirectory(prefix="patcher-beta21-jar-", dir=RUNS) as d:
        p = pathlib.Path(d) / "candidate.jar"
        with zipfile.ZipFile(p, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as z:
            for name in sorted(content):
                info = zipfile.ZipInfo(name, (2026, 9, 24, 18, 0, 0))
                info.create_system = 3
                info.compress_type = zipfile.ZIP_DEFLATED
                info.external_attr = 0o644 << 16
                z.writestr(info, content[name], compresslevel=9)
        with zipfile.ZipFile(p) as z:
            bad = z.testzip()
            if bad is not None:
                raise ValueError("archive CRC failed: " + bad)
        return p.read_bytes()

jar1 = archive_bytes()
jar2 = archive_bytes()
if jar1 != jar2:
    raise ValueError("non-deterministic beta.21 archive")

OUT.parent.mkdir(parents=True, exist_ok=True)
OUT.write_bytes(jar1)

report = {
    "schema": 1,
    "version": NEW_VERSION,
    "artifact": str(OUT.relative_to(ROOT)),
    "sha256": sha(OUT),
    "bytes": OUT.stat().st_size,
    "deterministicRebuild": True,
    "baseBeta20Sha256": BASE_SHA,
    "componentAlpha211Sha256": COMP_SHA,
    "changedClasses": changed_classes,
    "addedClasses": added_classes,
    "removedClasses": removed_classes,
    "changedResources": changed_resources,
    "versionPatchProof": proof1,
    "worldRenderStateRepair": {
        "target": "CeleritasWorldRenderer.drawChunkLayer",
        "samplesPerVanillaPass": 12,
        "defaultRepairEnabled": True,
        "probeDisable": "-Dakashic.worldRenderStateProbe=false",
        "repairDisable": "-Dakashic.worldRenderStateRepair=false",
        "repairs": [
            "active texture",
            "texture2D enable",
            "texture binding2D",
            "blend enable",
            "depth test enable",
            "alpha test enable",
            "cull face enable",
            "depth mask",
            "depth func",
            "color mask",
            "blend func",
            "blend equation",
            "blend color",
        ],
        "diagnosticOnly": ["current color", "alpha func/ref"],
    },
    "startupProfilerRemoved": True,
    "productionInstalled": False,
    "productionApproved": False,
}
REPORT.parent.mkdir(parents=True, exist_ok=True)
REPORT.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")

print(
    "THEPATCHER_BETA21_BUILD PASS sha256=%s bytes=%d changed=%d added=%d removed=%d"
    % (
        report["sha256"],
        report["bytes"],
        len(changed_classes),
        len(added_classes),
        len(removed_classes),
    )
)