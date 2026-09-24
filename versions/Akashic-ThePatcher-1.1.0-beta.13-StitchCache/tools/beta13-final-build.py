from __future__ import print_function
import hashlib, json, pathlib, struct, subprocess, tempfile, zipfile

ROOT = pathlib.Path(r"C:\Users\User\Desktop\Codex\Akashic-ThePatcher-1.1.0-beta.13-StitchCache")
JDK = pathlib.Path(r"C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot\bin")
BASE = ROOT / "base-beta12.jar"
BASE_SHA = "ec498ac793af3dfc755a71b71b15b7bb85fae742b5727d1085c41975d5e274ed"
ASYNC = ROOT / "asynccompat-alpha2.5.jar"
ASYNC_SHA = "6c963dd1ff81cdb81d229c9447da05d36ac923914e42279d32921e82aee6d34b"
ASM = pathlib.Path(r"C:\Users\User\.gradle\caches\modules-2\files-2.1\org.ow2.asm\asm\9.8\dc19ecb3f7889b7860697215cae99c0f9b6f6b4b\asm-9.8.jar")
TREE = pathlib.Path(r"C:\Users\User\.gradle\caches\modules-2\files-2.1\org.ow2.asm\asm-tree\9.8\18419ca5b77a2f81097c741e7872e6ab8d2f40d\asm-tree-9.8.jar")
VERSION_TOOL = ROOT / "tools/PatchThePatcherVersion.java"
RUNS = ROOT / "runs"
OUT = ROOT / "releases/!Akashic-ThePatcher-1.1.0-beta.13-StitchCache.jar"
REPORT = ROOT / "reports/final-build-audit.json"

OLD_VERSION = "1.1.0-beta.12"
NEW_VERSION = "1.1.0-beta.13"
MOD_ENTRY = "com/akashic/thepatcher/AkashicThePatcherMod.class"

MERGE_ENTRIES = {
    "dev/akashic/asynciconcompat/CompatHooks.class",
    "dev/akashic/asynciconcompat/CompatState.class",
    "dev/akashic/asynciconcompat/mixins/MixinTextureMapHybridAsync.class",
    "dev/akashic/asynciconcompat/mixins/MixinStitcherLayoutCache.class",
    "dev/akashic/asynciconcompat/cache/StitchLayoutCache.class",
    "dev/akashic/asynciconcompat/cache/StitchLayoutCache$Entry.class",
    "dev/akashic/asynciconcompat/cache/StitchLayoutCache$Layout.class",
    "mixins.akashicasynccompat.json",
    "mixins.akashicasynccompat.refmap.json",
}

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def major(data):
    return struct.unpack_from(">H", data, 6)[0]

if sha(BASE) != BASE_SHA:
    raise ValueError("beta.12 base identity changed")
if sha(ASYNC) != ASYNC_SHA:
    raise ValueError("async alpha2.5 identity changed")

with zipfile.ZipFile(BASE) as z:
    base = {n: z.read(n) for n in z.namelist() if not n.endswith("/")}
with zipfile.ZipFile(ASYNC) as z:
    names = set(z.namelist())
    missing = MERGE_ENTRIES - names
    if missing:
        raise ValueError("alpha2.5 missing merge entries: " + repr(sorted(missing)))
    async_data = {n: z.read(n) for n in MERGE_ENTRIES}

def compile_tool(folder):
    cp = ";".join(str(p) for p in (ASM, TREE))
    subprocess.run([
        str(JDK / "javac.exe"), "--release", "8", "-encoding", "UTF-8",
        "-Xlint:all,-options", "-Werror", "-cp", cp, "-d", str(folder),
        str(VERSION_TOOL),
    ], check=True, timeout=60)

def patch_version():
    with tempfile.TemporaryDirectory(prefix="patcher-beta13-version-", dir=RUNS) as d:
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
    raise ValueError("standalone AsyncCompat @Mod leaked into beta.13")

base_classes = {n for n in base if n.endswith(".class")}
result_classes = {n for n in content if n.endswith(".class")}
changed_classes = sorted(
    n for n in base_classes if n in content and base[n] != content[n]
)
added_classes = sorted(result_classes - base_classes)
removed_classes = sorted(base_classes - result_classes)

expected_changed = [MOD_ENTRY]
expected_added = sorted([
    "dev/akashic/asynciconcompat/cache/StitchLayoutCache$Entry.class",
    "dev/akashic/asynciconcompat/cache/StitchLayoutCache$Layout.class",
    "dev/akashic/asynciconcompat/cache/StitchLayoutCache.class",
    "dev/akashic/asynciconcompat/mixins/MixinStitcherLayoutCache.class",
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
    "mixins.akashicasynccompat.refmap.json",
]
if changed_resources != expected_resources:
    raise ValueError("unexpected changed resources: " + repr(changed_resources))

for name in result_classes:
    if major(content[name]) > 52:
        raise ValueError("Java 8 target broken: " + name)

def archive_bytes():
    with tempfile.TemporaryDirectory(prefix="patcher-beta13-jar-", dir=RUNS) as d:
        p = pathlib.Path(d) / "candidate.jar"
        with zipfile.ZipFile(p, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as z:
            for name in sorted(content):
                info = zipfile.ZipInfo(name, (2026, 9, 24, 4, 30, 0))
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
    raise ValueError("non-deterministic beta.13 archive")

OUT.parent.mkdir(parents=True, exist_ok=True)
OUT.write_bytes(jar1)

report = {
    "schema": 1,
    "version": NEW_VERSION,
    "artifact": str(OUT.relative_to(ROOT)),
    "sha256": sha(OUT),
    "bytes": OUT.stat().st_size,
    "deterministicRebuild": True,
    "baseBeta12Sha256": BASE_SHA,
    "asyncComponentVersion": "0.1.0-alpha.2.5",
    "asyncComponentSha256": ASYNC_SHA,
    "changedClasses": changed_classes,
    "addedClasses": added_classes,
    "removedClasses": removed_classes,
    "changedResources": changed_resources,
    "versionPatchProof": proof1,
    "versionPatchDeterministic": proof1 == proof2,
    "stitchCache": {
        "formatVersion": 1,
        "checksum": "CRC32 payload",
        "fingerprint": "SHA-256 over Stitcher parameters and sorted sprite names/effective dimensions/initial rotation",
        "firstBoot": "MISS then normal doStitch then seed",
        "subsequentIdenticalBoot": "HIT then reconstruct cached Stitcher.Slot leaves",
        "fallback": "any fingerprint/cache/validation/application mismatch runs normal Stitcher",
        "disableProperty": "-Dakashic.stitchLayoutCache=false",
    },
    "scope": "beta.12 AtlasProfiler plus fail-closed persistent Stitcher geometry cache",
    "productionInstalled": False,
    "productionApproved": False,
}
REPORT.parent.mkdir(parents=True, exist_ok=True)
REPORT.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")

print(
    "THEPATCHER_BETA13_BUILD PASS sha256=%s bytes=%d changed=%d added=%d"
    % (report["sha256"], report["bytes"], len(changed_classes), len(added_classes))
)