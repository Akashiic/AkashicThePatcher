from __future__ import print_function
import hashlib, json, pathlib, struct, subprocess, tempfile, zipfile

ROOT = pathlib.Path(r"C:\Users\User\Desktop\Codex\Akashic-ThePatcher-1.1.0-beta.17-ForestryUniversalTransformer")
JDK = pathlib.Path(r"C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot\bin")
BASE = ROOT / "base-beta15.jar"
BASE_SHA = "1e7008edf76156c07f19be7151554d87c677b675db723a7c61c89e3d5f09bd0d"
FORESTRY = ROOT / "Forestry-4.11.36-pinned.jar"
FORESTRY_SHA = "78daf273c4d112324ea7fb3ad9fbac99ecd088c1b78982811deebd58da712d6f"
ASM = pathlib.Path(r"C:\Users\User\.gradle\caches\modules-2\files-2.1\org.ow2.asm\asm\9.8\dc19ecb3f7889b7860697215cae99c0f9b6f6b4b\asm-9.8.jar")
TREE = pathlib.Path(r"C:\Users\User\.gradle\caches\modules-2\files-2.1\org.ow2.asm\asm-tree\9.8\18419ca5b77a2f81097c741e7872e6ab8d2f40d\asm-tree-9.8.jar")
VERSION_TOOL = ROOT / "tools/PatchThePatcherVersion.java"
RUNS = ROOT / "runs"
OUT = ROOT / "releases/!Akashic-ThePatcher-1.1.0-beta.17-ForestryUniversalTransformer.jar"
REPORT = ROOT / "reports/final-build-audit.json"

OLD_VERSION = "1.1.0-beta.15"
NEW_VERSION = "1.1.0-beta.17"
MOD_ENTRY = "com/akashic/thepatcher/AkashicThePatcherMod.class"
LOADER_ENTRY = "com/akashic/thepatcher/core/AkashicThePatcherLoadingPlugin.class"
GUARD_ENTRY = "com/akashic/thepatcher/module/forestry/ForestryC2RuntimeGuard.class"
HELPER_ENTRY = "com/akashic/thepatcher/module/forestry/ForestryGenomeCompat.class"
TRANSFORMER_ENTRY = "com/akashic/thepatcher/module/forestry/ForestryGenomeC2Transformer.class"

COMPONENTS = {
    LOADER_ENTRY: ROOT / "runs/loading-plugin/patched/AkashicThePatcherLoadingPlugin.class",
    GUARD_ENTRY: ROOT / "compiled/com/akashic/thepatcher/module/forestry/ForestryC2RuntimeGuard.class",
    HELPER_ENTRY: ROOT / "compiled/com/akashic/thepatcher/module/forestry/ForestryGenomeCompat.class",
    TRANSFORMER_ENTRY: ROOT / "compiled/com/akashic/thepatcher/module/forestry/ForestryGenomeC2Transformer.class",
}

EXPECTED_COMPONENT_SHA = {
    LOADER_ENTRY: "eedebc0489ca8703bd61f6fe17e4325f36e79d8a9e167455d6ba4c682410ce1c",
    GUARD_ENTRY: "4d9f5b52ed623e8694134ebda243035085dbc239eed36c0e8cfabf5e54559a8a",
    HELPER_ENTRY: "a4a2e6df7a9e2dbf38a642855dbecc4db58ab8adfa9b2243646dcc7b65033c11",
    TRANSFORMER_ENTRY: "f3be0cb96de930ad53a836f5b93cd272e778e3c136757058d76df452a8ffafa9",
}

def sha_bytes(data):
    return hashlib.sha256(data).hexdigest()

def sha(path):
    return sha_bytes(path.read_bytes())

def major(data):
    return struct.unpack_from(">H", data, 6)[0]

if sha(BASE) != BASE_SHA:
    raise ValueError("beta.15 base identity changed")
if sha(FORESTRY) != FORESTRY_SHA:
    raise ValueError("Forestry 4.11.36 pin changed")

for entry, path in COMPONENTS.items():
    actual = sha(path)
    expected = EXPECTED_COMPONENT_SHA[entry]
    if actual != expected:
        raise ValueError("%s identity changed: %s" % (entry, actual))

with zipfile.ZipFile(BASE) as z:
    base = {n: z.read(n) for n in z.namelist() if not n.endswith("/")}

def compile_version_tool(folder):
    cp = ";".join(str(p) for p in (ASM, TREE))
    subprocess.run([
        str(JDK / "javac.exe"), "--release", "8", "-encoding", "UTF-8",
        "-Xlint:all,-options", "-Werror", "-cp", cp, "-d", str(folder),
        str(VERSION_TOOL),
    ], check=True, timeout=60)

def patch_version():
    with tempfile.TemporaryDirectory(prefix="patcher-beta17-version-", dir=RUNS) as d:
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
content[MOD_ENTRY] = version1
for entry, path in COMPONENTS.items():
    content[entry] = path.read_bytes()

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

expected_changed = sorted([
    MOD_ENTRY,
    LOADER_ENTRY,
    GUARD_ENTRY,
])
expected_added = sorted([
    HELPER_ENTRY,
    TRANSFORMER_ENTRY,
])
if changed_classes != expected_changed:
    raise ValueError("unexpected changed classes: " + repr(changed_classes))
if added_classes != expected_added:
    raise ValueError("unexpected added classes: " + repr(added_classes))
if removed_classes:
    raise ValueError("unexpected removed classes: " + repr(removed_classes))

changed_resources = sorted(
    n for n in base
    if not n.endswith(".class") and n in content and base[n] != content[n]
)
if changed_resources != ["META-INF/MANIFEST.MF", "mcmod.info"]:
    raise ValueError("unexpected changed resources: " + repr(changed_resources))

for name in result_classes:
    if major(content[name]) > 52:
        raise ValueError("Java 8 target broken: " + name)

if b"ForestryGenomeC2Transformer" not in content[LOADER_ENTRY]:
    raise ValueError("loader missing Forestry transformer registration")
if b"loadChromosomes" not in content[HELPER_ENTRY]:
    raise ValueError("Forestry helper method missing")
if b"Universal Genome.getChromosomes rewrite applied." not in content[TRANSFORMER_ENTRY]:
    raise ValueError("Forestry transformer proof string missing")
if b"bundled zero-config Genome.getChromosomes rewrite is armed" not in content[GUARD_ENTRY]:
    raise ValueError("Forestry guard zero-config message missing")

# Existing performance pipeline must remain byte-identical.
for entry in [
    "dev/akashic/asynciconcompat/CompatHooks.class",
    "dev/akashic/asynciconcompat/CompatState.class",
    "dev/akashic/asynciconcompat/mixins/MixinTextureMapHybridAsync.class",
    "dev/akashic/asynciconcompat/mixins/MixinStitcherLayoutCache.class",
    "dev/akashic/asynciconcompat/mixins/MixinTextureUtilUploadProfiler.class",
    "dev/akashic/asynciconcompat/mixins/MixinAngelicaMipmapProfiler.class",
    "dev/akashic/asynciconcompat/profile/TextureUploadProfiler.class",
    "dev/akashic/asynciconcompat/cache/StitchLayoutCache.class",
]:
    if content[entry] != base[entry]:
        raise ValueError("unrelated class changed: " + entry)

def archive_bytes():
    with tempfile.TemporaryDirectory(prefix="patcher-beta17-jar-", dir=RUNS) as d:
        p = pathlib.Path(d) / "candidate.jar"
        with zipfile.ZipFile(p, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as z:
            for name in sorted(content):
                info = zipfile.ZipInfo(name, (2026, 9, 24, 15, 10, 0))
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
    raise ValueError("non-deterministic beta.17 archive")

OUT.parent.mkdir(parents=True, exist_ok=True)
OUT.write_bytes(jar1)

report = {
    "schema": 1,
    "version": NEW_VERSION,
    "artifact": str(OUT.relative_to(ROOT)),
    "sha256": sha(OUT),
    "bytes": OUT.stat().st_size,
    "deterministicRebuild": True,
    "baseBeta15Sha256": BASE_SHA,
    "forestryVersion": "4.11.36",
    "forestrySha256": FORESTRY_SHA,
    "changedClasses": changed_classes,
    "addedClasses": added_classes,
    "removedClasses": removed_classes,
    "changedResources": changed_resources,
    "versionPatchProof": proof1,
    "versionPatchDeterministic": proof1 == proof2,
    "universalForestryProtection": {
        "target": "forestry.core.genetics.Genome.getChromosomes(NBTTagCompound, ISpeciesRoot)",
        "originalObservedBytecodeBytes": 288,
        "replacement": "4-opcode wrapper to ForestryGenomeCompat.loadChromosomes",
        "helperMaxObservedBytecodeOffset": 91,
        "runtimeCompileCommandRequired": False,
        "compileCommandStillSupportedAsDefenseInDepth": True,
        "failClosedFingerprint": True,
    },
    "scope": "beta.15 plus universal zero-config Forestry 4.11.36 C2 mitigation via LaunchWrapper transformer",
    "productionInstalled": False,
    "productionApproved": False,
}
REPORT.parent.mkdir(parents=True, exist_ok=True)
REPORT.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")

print(
    "THEPATCHER_BETA17_BUILD PASS sha256=%s bytes=%d changed=%d added=%d"
    % (report["sha256"], report["bytes"], len(changed_classes), len(added_classes))
)