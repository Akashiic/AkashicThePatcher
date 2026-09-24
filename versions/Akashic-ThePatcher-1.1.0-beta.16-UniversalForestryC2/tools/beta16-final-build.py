from __future__ import print_function
import hashlib, json, pathlib, struct, subprocess, tempfile, zipfile

ROOT = pathlib.Path(r"C:\Users\User\Desktop\Codex\Akashic-ThePatcher-1.1.0-beta.16-UniversalForestryC2")
JDK = pathlib.Path(r"C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot\bin")
BASE = ROOT / "base-beta15.jar"
BASE_SHA = "1e7008edf76156c07f19be7151554d87c677b675db723a7c61c89e3d5f09bd0d"
ASYNC = ROOT / "asynccompat-alpha2.8.jar"
ASYNC_SHA = "dcc0bf8e1a92be1e11dc82fac833bf06446a19ae8239a6478f765994daca302e"
GUARD = ROOT / "compiled/com/akashic/thepatcher/module/forestry/ForestryC2RuntimeGuard.class"
GUARD_SHA = "1dc223830644824ba3dfa00808cac8118a828f5af8342284729f495989c0be8d"
FORESTRY = pathlib.Path(r"C:\Users\User\Desktop\Codex\Akashic-Hodgepodge-AsyncCompat\libs\Forestry-4.11.36.jar")
FORESTRY_SHA = "78daf273c4d112324ea7fb3ad9fbac99ecd088c1b78982811deebd58da712d6f"
GENOME_CLASS_SHA = "fe62518c6126cc715610ef3ced8685edac0e5aeb0c0315a5c1d2d284d55f2683"

ASM = pathlib.Path(r"C:\Users\User\.gradle\caches\modules-2\files-2.1\org.ow2.asm\asm\9.8\dc19ecb3f7889b7860697215cae99c0f9b6f6b4b\asm-9.8.jar")
TREE = pathlib.Path(r"C:\Users\User\.gradle\caches\modules-2\files-2.1\org.ow2.asm\asm-tree\9.8\18419ca5b77a2f81097c741e7872e6ab8d2f40d\asm-tree-9.8.jar")
VERSION_TOOL = ROOT / "tools/PatchThePatcherVersion.java"
RUNS = ROOT / "runs"
OUT = ROOT / "releases/!Akashic-ThePatcher-1.1.0-beta.16-UniversalForestryC2.jar"
REPORT = ROOT / "reports/final-build-audit.json"

OLD_VERSION = "1.1.0-beta.15"
NEW_VERSION = "1.1.0-beta.16"
MOD_ENTRY = "com/akashic/thepatcher/AkashicThePatcherMod.class"
GUARD_ENTRY = "com/akashic/thepatcher/module/forestry/ForestryC2RuntimeGuard.class"

MERGE_ENTRIES = {
    "dev/akashic/asynciconcompat/CompatHooks.class",
    "dev/akashic/asynciconcompat/CompatState.class",
    "dev/akashic/asynciconcompat/mixins/MixinTextureMapHybridAsync.class",
    "dev/akashic/asynciconcompat/mixins/MixinStitcherLayoutCache.class",
    "dev/akashic/asynciconcompat/cache/StitchLayoutCache.class",
    "dev/akashic/asynciconcompat/cache/StitchLayoutCache$Entry.class",
    "dev/akashic/asynciconcompat/cache/StitchLayoutCache$Layout.class",
    "dev/akashic/asynciconcompat/profile/TextureUploadProfiler.class",
    "dev/akashic/asynciconcompat/mixins/MixinTextureUtilUploadProfiler.class",
    "dev/akashic/asynciconcompat/mixins/MixinAngelicaMipmapProfiler.class",
    "dev/akashic/asynciconcompat/forestry/ForestryGenomeC2Shim.class",
    "dev/akashic/asynciconcompat/mixins/MixinForestryGenomeC2.class",
    "mixins.akashicasynccompat.json",
    "mixins.akashicasynccompat.refmap.json",
}

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def sha_bytes(data):
    return hashlib.sha256(data).hexdigest()

def major(data):
    return struct.unpack_from(">H", data, 6)[0]

if sha(BASE) != BASE_SHA:
    raise ValueError("beta.15 base identity changed")
if sha(ASYNC) != ASYNC_SHA:
    raise ValueError("async alpha2.8 identity changed")
if sha(GUARD) != GUARD_SHA:
    raise ValueError("compiled guard identity changed")
if sha(FORESTRY) != FORESTRY_SHA:
    raise ValueError("Forestry 4.11.36 dependency identity changed")

with zipfile.ZipFile(FORESTRY) as z:
    genome_class = z.read("forestry/core/genetics/Genome.class")
    if sha_bytes(genome_class) != GENOME_CLASS_SHA:
        raise ValueError("Forestry Genome.class identity changed")

with zipfile.ZipFile(BASE) as z:
    base = {n: z.read(n) for n in z.namelist() if not n.endswith("/")}
with zipfile.ZipFile(ASYNC) as z:
    async_names = set(z.namelist())
    missing = MERGE_ENTRIES - async_names
    if missing:
        raise ValueError("alpha2.8 missing merge entries: " + repr(sorted(missing)))
    if any(n.startswith("forestry/") for n in async_names):
        raise ValueError("Forestry dependency leaked into alpha2.8 artifact")
    async_data = {n: z.read(n) for n in MERGE_ENTRIES}

def compile_version_tool(folder):
    cp = ";".join(str(p) for p in (ASM, TREE))
    subprocess.run([
        str(JDK / "javac.exe"), "--release", "8", "-encoding", "UTF-8",
        "-Xlint:all,-options", "-Werror", "-cp", cp, "-d", str(folder),
        str(VERSION_TOOL),
    ], check=True, timeout=60)

def patch_version():
    with tempfile.TemporaryDirectory(prefix="patcher-beta16-version-", dir=RUNS) as d:
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
content.update(async_data)
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

if "dev/akashic/asynciconcompat/AkashicAsyncIconCompat.class" in content:
    raise ValueError("standalone AsyncCompat @Mod leaked into beta.16")
if any(n.startswith("forestry/") for n in content):
    raise ValueError("Forestry classes leaked into beta.16")

base_classes = {n for n in base if n.endswith(".class")}
result_classes = {n for n in content if n.endswith(".class")}
changed_classes = sorted(
    n for n in base_classes if n in content and base[n] != content[n]
)
added_classes = sorted(result_classes - base_classes)
removed_classes = sorted(base_classes - result_classes)

expected_changed = sorted([
    MOD_ENTRY,
    GUARD_ENTRY,
])
expected_added = sorted([
    "dev/akashic/asynciconcompat/forestry/ForestryGenomeC2Shim.class",
    "dev/akashic/asynciconcompat/mixins/MixinForestryGenomeC2.class",
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
expected_resources = [
    "META-INF/MANIFEST.MF",
    "mcmod.info",
    "mixins.akashicasynccompat.json",
]
if changed_resources != expected_resources:
    raise ValueError("unexpected changed resources: " + repr(changed_resources))

for name in result_classes:
    if major(content[name]) > 52:
        raise ValueError("Java 8 target broken: " + name)

# Hard structural gate: the overwrite class must be a tiny delegation.
overwrite = content[
    "dev/akashic/asynciconcompat/mixins/MixinForestryGenomeC2.class"
]
if b"ForestryGenomeC2Shim" not in overwrite:
    raise ValueError("Forestry overwrite does not delegate to shim")

shim = content[
    "dev/akashic/asynciconcompat/forestry/ForestryGenomeC2Shim.class"
]
for literal in [
    b"loadStoredChromosomes",
    b"repairAndValidateChromosome",
    b"fillMissingChromosomes",
    b"ForestryGenomeC2Shim ACTIVE",
    b"akashic.forestryC2Shim.executed",
]:
    if literal not in shim:
        raise ValueError("missing shim structural marker: " + repr(literal))

guard = content[GUARD_ENTRY]
for literal in [
    b"Universal Genome shim bundled",
    b"akashic.forestryC2Guard.universalShim",
    b"akashic.forestryC2Guard.compilerOracle",
]:
    if literal not in guard:
        raise ValueError("missing guard marker: " + repr(literal))

def archive_bytes():
    with tempfile.TemporaryDirectory(prefix="patcher-beta16-jar-", dir=RUNS) as d:
        p = pathlib.Path(d) / "candidate.jar"
        with zipfile.ZipFile(p, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as z:
            for name in sorted(content):
                info = zipfile.ZipInfo(name, (2026, 9, 24, 14, 40, 0))
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
    raise ValueError("non-deterministic beta.16 archive")

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
    "asyncComponentVersion": "0.1.0-alpha.2.8",
    "asyncComponentSha256": ASYNC_SHA,
    "forestryJarSha256": FORESTRY_SHA,
    "forestryGenomeClassSha256": GENOME_CLASS_SHA,
    "guardClassSha256": GUARD_SHA,
    "changedClasses": changed_classes,
    "addedClasses": added_classes,
    "removedClasses": removed_classes,
    "changedResources": changed_resources,
    "versionPatchProof": proof1,
    "versionPatchDeterministic": proof1 == proof2,
    "universalForestryC2": {
        "target": "forestry.core.genetics.Genome.getChromosomes(NBTTagCompound, ISpeciesRoot)",
        "originalBytecodeLengthObserved": 288,
        "strategy": "Mixin @Overwrite -> split ForestryGenomeC2Shim",
        "compileCommandRequired": False,
        "compileCommandStillSupportedAsFallback": True,
        "forestryDependencyBundled": False,
        "runtimeExecutionProperty": "akashic.forestryC2Shim.executed",
    },
    "scope": "beta.15 plus universal client-side Forestry 4.11.36 Java 8u51 C2 mitigation",
    "productionInstalled": False,
    "productionApproved": False,
}
REPORT.parent.mkdir(parents=True, exist_ok=True)
REPORT.write_text(
    json.dumps(report, indent=2, sort_keys=True) + "\n",
    encoding="utf-8")

print(
    "THEPATCHER_BETA16_BUILD PASS sha256=%s bytes=%d changed=%d added=%d"
    % (
        report["sha256"],
        report["bytes"],
        len(changed_classes),
        len(added_classes),
    )
)