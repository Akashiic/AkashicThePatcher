from __future__ import print_function
import hashlib
import json
import pathlib
import struct
import subprocess
import tempfile
import zipfile

ROOT = pathlib.Path(r"C:\Users\User\Desktop\Codex\Akashic-ThePatcher-1.1.0-beta.11-Async-Forestry")
JDK = pathlib.Path(r"C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot\bin")
BASE = pathlib.Path(r"C:\Users\User\AppData\Roaming\.technic\modpacks\forbidden-series\mods\!Akashic-ThePatcher-1.1.0-beta.8-BASELINE.jar")
BASE_SHA = "010c3b9f4fdd20528a772904f13238aa836c8af13759aebfe7184ed07beed44b"
ASYNC = pathlib.Path(r"C:\Users\User\Desktop\Codex\Akashic-Hodgepodge-AsyncCompat\build\libs\akashicasynccompat-0.1.0-alpha.2.3.jar")
ASYNC_SHA = "7baeb83b1ea8c0139952671821fb1c3d3aedaae13306c2f31996e3170024a9f4"
ASM = pathlib.Path(r"C:\Users\User\.gradle\caches\modules-2\files-2.1\org.ow2.asm\asm\9.8\dc19ecb3f7889b7860697215cae99c0f9b6f6b4b\asm-9.8.jar")
TREE = pathlib.Path(r"C:\Users\User\.gradle\caches\modules-2\files-2.1\org.ow2.asm\asm-tree\9.8\18419ca5b77a2f81097c741e7872e6ab8d2f40d\asm-tree-9.8.jar")

GUARD_SRC = ROOT / "src/main/java/com/akashic/thepatcher/module/forestry/ForestryC2RuntimeGuard.java"
LOADER_TOOL = ROOT / "tools/PatchThePatcherBeta11LoadingPlugin.java"
VERSION_TOOL = ROOT / "tools/PatchThePatcherVersion.java"

OUT = ROOT / "releases/!Akashic-ThePatcher-1.1.0-beta.11-Async-Forestry.jar"
REPORT = ROOT / "reports/final-build-audit.json"
RUNS = ROOT / "runs"

OLD_VERSION = "1.1.0-beta.8"
NEW_VERSION = "1.1.0-beta.11"

ASYNC_ENTRIES = {
    "dev/akashic/asynciconcompat/CompatHooks.class",
    "dev/akashic/asynciconcompat/CompatState.class",
    "dev/akashic/asynciconcompat/mixins/MixinTextureMapHybridAsync.class",
    "mixins.akashicasynccompat.json",
    "mixins.akashicasynccompat.refmap.json",
}

GUARD_ENTRY = "com/akashic/thepatcher/module/forestry/ForestryC2RuntimeGuard.class"
LOADER_ENTRY = "com/akashic/thepatcher/core/AkashicThePatcherLoadingPlugin.class"
MOD_ENTRY = "com/akashic/thepatcher/AkashicThePatcherMod.class"

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def major(data):
    return struct.unpack_from(">H", data, 6)[0]

if sha(BASE) != BASE_SHA:
    raise ValueError("beta.8 base identity changed")
if sha(ASYNC) != ASYNC_SHA:
    raise ValueError("async alpha2.3 identity changed")
for p in (ASM, TREE, GUARD_SRC, LOADER_TOOL, VERSION_TOOL):
    if not p.is_file():
        raise FileNotFoundError(p)

with zipfile.ZipFile(BASE) as z:
    base = {n: z.read(n) for n in z.namelist() if not n.endswith("/")}
with zipfile.ZipFile(ASYNC) as z:
    async_data = {n: z.read(n) for n in ASYNC_ENTRIES}
    missing = ASYNC_ENTRIES - set(z.namelist())
    if missing:
        raise ValueError("async artifact missing entries: " + repr(sorted(missing)))
    forbidden = {
        "dev/akashic/asynciconcompat/AkashicAsyncIconCompat.class",
        "mcmod.info",
    }
    if not forbidden.issubset(set(z.namelist())):
        raise ValueError("async source artifact shape changed")

def compile_guard():
    with tempfile.TemporaryDirectory(prefix="patcher-beta11-guard-", dir=RUNS) as d:
        d = pathlib.Path(d)
        subprocess.run([
            str(JDK / "javac.exe"),
            "--release", "8",
            "-encoding", "UTF-8",
            "-Xlint:all,-options",
            "-Werror",
            "-cp", str(BASE),
            "-d", str(d),
            str(GUARD_SRC),
        ], check=True, timeout=60)
        p = d / GUARD_ENTRY
        data = p.read_bytes()
        if major(data) > 52:
            raise ValueError("Forestry guard is not Java 8 compatible")
        return data

def compile_tools(folder):
    cp = ";".join(str(p) for p in (ASM, TREE))
    subprocess.run([
        str(JDK / "javac.exe"),
        "--release", "8",
        "-encoding", "UTF-8",
        "-Xlint:all,-options",
        "-Werror",
        "-cp", cp,
        "-d", str(folder),
        str(LOADER_TOOL),
        str(VERSION_TOOL),
    ], check=True, timeout=60)

def patch_loader(original):
    with tempfile.TemporaryDirectory(prefix="patcher-beta11-loader-", dir=RUNS) as d:
        d = pathlib.Path(d)
        tools = d / "tools"
        tools.mkdir()
        compile_tools(tools)
        src = d / "loader.class"
        dst = d / "patched" / "loader.class"
        src.write_bytes(original)
        cp = ";".join(str(p) for p in (tools, ASM, TREE))
        proc = subprocess.run([
            str(JDK / "java.exe"),
            "-Xverify:all",
            "-cp", cp,
            "com.akashic.compatlab.tools.PatchThePatcherBeta11LoadingPlugin",
            str(src), str(dst),
        ], check=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=60)
        text = proc.stdout.decode("utf-8", errors="replace")
        if "PATCH_THEPATCHER_BETA11_LOADING_PLUGIN PASS" not in text:
            raise ValueError("loader proof missing: " + text[-2000:])
        return dst.read_bytes(), text.strip()

def patch_version():
    with tempfile.TemporaryDirectory(prefix="patcher-beta11-version-", dir=RUNS) as d:
        d = pathlib.Path(d)
        tools = d / "tools"
        tools.mkdir()
        compile_tools(tools)
        dst = d / "AkashicThePatcherMod.class"
        cp = ";".join(str(p) for p in (tools, ASM, TREE))
        proc = subprocess.run([
            str(JDK / "java.exe"),
            "-Xverify:all",
            "-cp", cp,
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

guard1 = compile_guard()
guard2 = compile_guard()
if guard1 != guard2:
    raise ValueError("non-deterministic Forestry guard compile")

loader1, loader_proof1 = patch_loader(base[LOADER_ENTRY])
loader2, loader_proof2 = patch_loader(base[LOADER_ENTRY])
if loader1 != loader2:
    raise ValueError("non-deterministic loader patch")

version1, version_proof1 = patch_version()
version2, version_proof2 = patch_version()
if version1 != version2:
    raise ValueError("non-deterministic version patch")

content = dict(base)
content.update(async_data)
content[GUARD_ENTRY] = guard1
content[LOADER_ENTRY] = loader1
content[MOD_ENTRY] = version1

manifest = content["META-INF/MANIFEST.MF"].decode("utf-8")
if "TweakClass:" in manifest or "MixinConfigs:" in manifest:
    raise ValueError("base manifest unexpectedly already contains Mixin bootstrap")
manifest = manifest.replace(
    "Implementation-Version: " + OLD_VERSION,
    "Implementation-Version: " + NEW_VERSION,
    1,
)
if "Implementation-Version: " + NEW_VERSION not in manifest:
    raise ValueError("manifest version replacement failed")
insert = (
    "TweakClass: org.spongepowered.asm.launch.MixinTweaker\r\n"
    "MixinConfigs: mixins.akashicasynccompat.json\r\n"
)
marker = "\r\n\r\n"
if marker not in manifest:
    marker = "\n\n"
    insert = insert.replace("\r\n", "\n")
manifest = manifest.replace(marker, "\r\n" + insert + "\r\n" if "\r\n" in marker else "\n" + insert + "\n", 1)
content["META-INF/MANIFEST.MF"] = manifest.encode("utf-8")

mcmod = content["mcmod.info"].decode("utf-8")
if mcmod.count(OLD_VERSION) != 1:
    raise ValueError("mcmod version shape changed")
mcmod = mcmod.replace(OLD_VERSION, NEW_VERSION, 1)
content["mcmod.info"] = mcmod.encode("utf-8")

if "dev/akashic/asynciconcompat/AkashicAsyncIconCompat.class" in content:
    raise ValueError("standalone AsyncCompat @Mod class leaked into merged jar")

base_classes = {n for n in base if n.endswith(".class")}
new_classes = {n for n in content if n.endswith(".class")}
changed_classes = sorted(n for n in base_classes if base[n] != content[n])
added_classes = sorted(new_classes - base_classes)

expected_changed = sorted([LOADER_ENTRY, MOD_ENTRY])
expected_added = sorted([
    GUARD_ENTRY,
    "dev/akashic/asynciconcompat/CompatHooks.class",
    "dev/akashic/asynciconcompat/CompatState.class",
    "dev/akashic/asynciconcompat/mixins/MixinTextureMapHybridAsync.class",
])
if changed_classes != expected_changed:
    raise ValueError("unexpected changed base classes: " + repr(changed_classes))
if added_classes != expected_added:
    raise ValueError("unexpected added classes: " + repr(added_classes))

for name in added_classes + changed_classes:
    if major(content[name]) > 52:
        raise ValueError("Java 8 target broken: " + name)

def archive_bytes():
    with tempfile.TemporaryDirectory(prefix="patcher-beta11-jar-", dir=RUNS) as d:
        p = pathlib.Path(d) / "candidate.jar"
        with zipfile.ZipFile(p, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as z:
            for name in sorted(content):
                info = zipfile.ZipInfo(name, (2026, 9, 24, 3, 0, 0))
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
    raise ValueError("non-deterministic beta.11 archive")

OUT.parent.mkdir(parents=True, exist_ok=True)
OUT.write_bytes(jar1)

report = {
    "schema": 1,
    "version": NEW_VERSION,
    "artifact": str(OUT.relative_to(ROOT)),
    "sha256": sha(OUT),
    "bytes": OUT.stat().st_size,
    "deterministicRebuild": True,
    "baseBeta8Sha256": BASE_SHA,
    "asyncComponentVersion": "0.1.0-alpha.2.3",
    "asyncComponentSha256": ASYNC_SHA,
    "requiredHodgepodgeConfig": "B:asyncIconLoading=false",
    "requiredJvmCompileCommand":
        "-XX:CompileCommand=exclude,forestry/core/genetics/Genome.getChromosomes",
    "forestryCrashEvidence": {
        "jre": "1.8.0_51-b16",
        "fatalThread": "C2 CompilerThread1",
        "compileTask": "forestry.core.genetics.Genome::getChromosomes (288 bytes)",
        "hsErr": "hs_err_pid2844.log",
    },
    "changedClasses": changed_classes,
    "addedClasses": added_classes,
    "addedResources": sorted([
        "mixins.akashicasynccompat.json",
        "mixins.akashicasynccompat.refmap.json",
    ]),
    "loaderPatchProof": loader_proof1,
    "loaderPatchDeterministic": loader_proof1 == loader_proof2,
    "versionPatchProof": version_proof1,
    "versionPatchDeterministic": version_proof1 == version_proof2,
    "forestryGuardSourceSha256": sha(GUARD_SRC),
    "scope": (
        "verified beta.8 baseline plus merged Akashic hybrid async icon scheduler "
        "alpha.2.3 (Chisel/Mekanism/MFR/CoFH main-thread fallbacks and abort cleanup) "
        "plus early Forestry Java 8u51 C2 compile-exclusion verification"
    ),
    "excludedExperiments": [
        "beta.9 MPS/Numina experiment",
        "beta.10 Cinematic/Angelica experiment",
    ],
    "productionInstalled": False,
    "productionApproved": False,
}

REPORT.parent.mkdir(parents=True, exist_ok=True)
REPORT.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")

print(
    "THEPATCHER_BETA11_BUILD PASS sha256="
    + report["sha256"]
    + " bytes="
    + str(report["bytes"])
    + " changed="
    + str(len(changed_classes))
    + " added="
    + str(len(added_classes))
)