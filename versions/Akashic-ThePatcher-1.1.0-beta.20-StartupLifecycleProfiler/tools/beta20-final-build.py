from __future__ import print_function
import hashlib, json, pathlib, struct, subprocess, tempfile, zipfile

ROOT = pathlib.Path(r"C:\Users\User\Desktop\Codex\Akashic-ThePatcher-1.1.0-beta.20-StartupLifecycleProfiler")
JDK = pathlib.Path(r"C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot\bin")
BASE = ROOT / "base-beta19.jar"
BASE_SHA = "c3d2ef2127080898b0a5b0f54305056ee7deb1b8bbb8f50e9b9cf726aab08472"
COMP = ROOT / "startup-profiler-alpha2.10.jar"
COMP_SHA = "045bd10cfb6264910325342c038519dd4be94cfad6570c334e7826667d22f187"
GUARD = ROOT / "compiled/com/akashic/thepatcher/module/forestry/ForestryC2RuntimeGuard.class"
GUARD_SHA = "8874cd2db55a0f5d056fae190274f3532b41521cf9fbb36c689085c25fedfb34"
ASM = pathlib.Path(r"C:\Users\User\.gradle\caches\modules-2\files-2.1\org.ow2.asm\asm\9.8\dc19ecb3f7889b7860697215cae99c0f9b6f6b4b\asm-9.8.jar")
TREE = pathlib.Path(r"C:\Users\User\.gradle\caches\modules-2\files-2.1\org.ow2.asm\asm-tree\9.8\18419ca5b77a2f81097c741e7872e6ab8d2f40d\asm-tree-9.8.jar")
VERSION_TOOL = ROOT / "tools/PatchThePatcherVersion.java"
RUNS = ROOT / "runs"
OUT = ROOT / "releases/!Akashic-ThePatcher-1.1.0-beta.20-StartupLifecycleProfiler.jar"
REPORT = ROOT / "reports/final-build-audit.json"

OLD_VERSION = "1.1.0-beta.19"
NEW_VERSION = "1.1.0-beta.20"
MOD_ENTRY = "com/akashic/thepatcher/AkashicThePatcherMod.class"
GUARD_ENTRY = "com/akashic/thepatcher/module/forestry/ForestryC2RuntimeGuard.class"
MIXIN_CFG = "mixins.akashicasynccompat.json"
REFMAP = "mixins.akashicasynccompat.refmap.json"

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def major(data):
    return struct.unpack_from(">H", data, 6)[0]

if sha(BASE) != BASE_SHA:
    raise ValueError("beta.19 base identity changed")
if sha(COMP) != COMP_SHA:
    raise ValueError("alpha.2.10 component identity changed")
if sha(GUARD) != GUARD_SHA:
    raise ValueError("beta.20 guard identity changed")

with zipfile.ZipFile(BASE) as z:
    base = {n: z.read(n) for n in z.namelist() if not n.endswith("/")}
with zipfile.ZipFile(COMP) as z:
    comp_names = set(z.namelist())
    profiler_entries = sorted(
        n for n in comp_names
        if (
            n == "dev/akashic/asynciconcompat/mixins/MixinLoadControllerStartupProfiler.class"
            or n.startswith("dev/akashic/asynciconcompat/startup/StartupLifecycleProfiler")
        )
        and n.endswith(".class")
    )
    if len(profiler_entries) < 2:
        raise ValueError("startup profiler class closure missing: " + repr(profiler_entries))
    comp = {n: z.read(n) for n in profiler_entries}
    comp[MIXIN_CFG] = z.read(MIXIN_CFG)
    comp[REFMAP] = z.read(REFMAP)

def compile_version_tool(folder):
    cp = ";".join(str(p) for p in (ASM, TREE))
    subprocess.run([
        str(JDK / "javac.exe"), "--release", "8", "-encoding", "UTF-8",
        "-Xlint:all,-options", "-Werror", "-cp", cp, "-d", str(folder),
        str(VERSION_TOOL),
    ], check=True, timeout=60)

def patch_version():
    with tempfile.TemporaryDirectory(prefix="patcher-beta20-version-", dir=RUNS) as d:
        d = pathlib.Path(d)
        tools = d / "tools"
        tools.mkdir()
        compile_version_tool(tools)
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
        data = dst.read_bytes()
        if major(data) > 52:
            raise ValueError("version class target broken")
        return data, text.strip()

version1, proof1 = patch_version()
version2, proof2 = patch_version()
if version1 != version2:
    raise ValueError("non-deterministic version patch")

content = dict(base)
content.update(comp)
content[GUARD_ENTRY] = GUARD.read_bytes()
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

expected_changed = sorted([MOD_ENTRY, GUARD_ENTRY])
if changed_classes != expected_changed:
    raise ValueError("unexpected changed classes: " + repr(changed_classes))
if added_classes != profiler_entries:
    raise ValueError(
        "unexpected added classes: actual=%r expected=%r"
        % (added_classes, profiler_entries)
    )
if removed_classes:
    raise ValueError("unexpected removed classes: " + repr(removed_classes))

changed_resources = sorted(
    n for n in base
    if not n.endswith(".class") and n in content and base[n] != content[n]
)
expected_resources = [
    "META-INF/MANIFEST.MF",
    "mcmod.info",
    MIXIN_CFG,
]
if changed_resources != expected_resources:
    raise ValueError("unexpected changed resources: " + repr(changed_resources))

for name in result_classes:
    if major(content[name]) > 52:
        raise ValueError("Java 8 target broken: " + name)

# Existing behavior must remain byte-identical.
preserved = [
    "com/akashic/thepatcher/core/AkashicThePatcherLoadingPlugin.class",
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
if "MixinLoadControllerStartupProfiler" not in cfg:
    raise ValueError("startup profiler mixin missing from config")
if "MixinForestryGenomeC2" in cfg:
    raise ValueError("rejected Forestry Mixin leaked")

guard = content[GUARD_ENTRY]
for literal in [
    b"Full CompilerOracle pack protection active",
    b"INCOMPLETE JVM protection",
    b"compilerOracleRequired",
    b"loadStoredChromosome",
    b"fillMissingChromosomes",
]:
    if literal not in guard:
        raise ValueError("guard proof literal missing: " + repr(literal))

prof_main = content["dev/akashic/asynciconcompat/startup/StartupLifecycleProfiler.class"]
for literal in [
    b"StartupPhase event=",
    b"top=[",
    b"akashic.startupLifecycleProfiler",
]:
    if literal not in prof_main:
        raise ValueError("profiler proof literal missing: " + repr(literal))

mixin = content["dev/akashic/asynciconcompat/mixins/MixinLoadControllerStartupProfiler.class"]
for literal in [
    b"propogateStateMessage",
    b"sendEventToModContainer",
    b"beginPhase",
    b"endPhase",
    b"beginDispatch",
    b"endDispatch",
]:
    if literal not in mixin:
        raise ValueError("profiler mixin proof literal missing: " + repr(literal))

def archive_bytes():
    with tempfile.TemporaryDirectory(prefix="patcher-beta20-jar-", dir=RUNS) as d:
        p = pathlib.Path(d) / "candidate.jar"
        with zipfile.ZipFile(p, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as z:
            for name in sorted(content):
                info = zipfile.ZipInfo(name, (2026, 9, 24, 17, 10, 0))
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
    raise ValueError("non-deterministic beta.20 archive")

OUT.parent.mkdir(parents=True, exist_ok=True)
OUT.write_bytes(jar1)

report = {
    "schema": 1,
    "version": NEW_VERSION,
    "artifact": str(OUT.relative_to(ROOT)),
    "sha256": sha(OUT),
    "bytes": OUT.stat().st_size,
    "deterministicRebuild": True,
    "baseBeta19Sha256": BASE_SHA,
    "componentAlpha210Sha256": COMP_SHA,
    "changedClasses": changed_classes,
    "addedClasses": added_classes,
    "removedClasses": removed_classes,
    "changedResources": changed_resources,
    "versionPatchProof": proof1,
    "versionPatchDeterministic": proof1 == proof2,
    "startupProfiler": {
        "target": "cpw.mods.fml.common.LoadController",
        "phaseMethod": "propogateStateMessage(FMLEvent)",
        "dispatchMethod": "sendEventToModContainer(FMLEvent,ModContainer)",
        "topCount": 15,
        "disableProperty": "-Dakashic.startupLifecycleProfiler=false",
    },
    "forestryGuard": {
        "requiredCompilerOracleExclusions": 6,
        "rewriteAloneConsideredSafe": False,
        "packSideMetadataExpected": True,
    },
    "productionInstalled": False,
    "productionApproved": False,
}
REPORT.parent.mkdir(parents=True, exist_ok=True)
REPORT.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")

print(
    "THEPATCHER_BETA20_BUILD PASS sha256=%s bytes=%d changed=%d added=%d"
    % (report["sha256"], report["bytes"], len(changed_classes), len(added_classes))
)
print("PROFILER_CLASSES", len(profiler_entries))