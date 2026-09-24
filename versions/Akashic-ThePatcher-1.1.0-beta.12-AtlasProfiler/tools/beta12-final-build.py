from __future__ import print_function
import hashlib, json, pathlib, struct, subprocess, tempfile, zipfile

ROOT = pathlib.Path(r"C:\Users\User\Desktop\Codex\Akashic-ThePatcher-1.1.0-beta.12-AtlasProfiler")
JDK = pathlib.Path(r"C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot\bin")
BASE = ROOT / "base-beta11.jar"
BASE_SHA = "1fb3947f88442e4f9ebe6bdc8526e7ed288d31bbfa7f0f04ad8da220fa3a152d"
ASYNC = ROOT / "asynccompat-alpha2.4.jar"
ASYNC_SHA = "7576f6bb71353d9804e319737b1bd1bc3bca7e97be480e1e2dc0058640994c7e"
ASM = pathlib.Path(r"C:\Users\User\.gradle\caches\modules-2\files-2.1\org.ow2.asm\asm\9.8\dc19ecb3f7889b7860697215cae99c0f9b6f6b4b\asm-9.8.jar")
TREE = pathlib.Path(r"C:\Users\User\.gradle\caches\modules-2\files-2.1\org.ow2.asm\asm-tree\9.8\18419ca5b77a2f81097c741e7872e6ab8d2f40d\asm-tree-9.8.jar")
VERSION_TOOL = ROOT / "tools/PatchThePatcherVersion.java"
RUNS = ROOT / "runs"
OUT = ROOT / "releases/!Akashic-ThePatcher-1.1.0-beta.12-AtlasProfiler.jar"
REPORT = ROOT / "reports/final-build-audit.json"

OLD_VERSION = "1.1.0-beta.11"
NEW_VERSION = "1.1.0-beta.12"

ASYNC_ENTRIES = {
    "dev/akashic/asynciconcompat/CompatHooks.class",
    "dev/akashic/asynciconcompat/CompatState.class",
    "dev/akashic/asynciconcompat/mixins/MixinTextureMapHybridAsync.class",
    "mixins.akashicasynccompat.json",
    "mixins.akashicasynccompat.refmap.json",
}
MOD_ENTRY = "com/akashic/thepatcher/AkashicThePatcherMod.class"

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def major(data):
    return struct.unpack_from(">H", data, 6)[0]

if sha(BASE) != BASE_SHA:
    raise ValueError("beta.11 base identity changed")
if sha(ASYNC) != ASYNC_SHA:
    raise ValueError("async alpha2.4 identity changed")

with zipfile.ZipFile(BASE) as z:
    base = {n: z.read(n) for n in z.namelist() if not n.endswith("/")}
with zipfile.ZipFile(ASYNC) as z:
    names = set(z.namelist())
    missing = ASYNC_ENTRIES - names
    if missing:
        raise ValueError("async alpha2.4 missing entries: " + repr(sorted(missing)))
    async_data = {n: z.read(n) for n in ASYNC_ENTRIES}

def compile_tool(folder):
    cp = ";".join(str(p) for p in (ASM, TREE))
    subprocess.run([
        str(JDK / "javac.exe"), "--release", "8", "-encoding", "UTF-8",
        "-Xlint:all,-options", "-Werror", "-cp", cp, "-d", str(folder),
        str(VERSION_TOOL),
    ], check=True, timeout=60)

def patch_version():
    with tempfile.TemporaryDirectory(prefix="patcher-beta12-version-", dir=RUNS) as d:
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
manifest = manifest.replace(old_line, new_line, 1)
content["META-INF/MANIFEST.MF"] = manifest.encode("utf-8")

mcmod = content["mcmod.info"].decode("utf-8")
if mcmod.count(OLD_VERSION) != 1:
    raise ValueError("mcmod version shape changed")
mcmod = mcmod.replace(OLD_VERSION, NEW_VERSION, 1)
content["mcmod.info"] = mcmod.encode("utf-8")

if "dev/akashic/asynciconcompat/AkashicAsyncIconCompat.class" in content:
    raise ValueError("standalone AsyncCompat @Mod leaked into beta.12")

base_classes = {n for n in base if n.endswith(".class")}
result_classes = {n for n in content if n.endswith(".class")}
changed_classes = sorted(
    n for n in base_classes
    if n in content and base[n] != content[n]
)
added_classes = sorted(result_classes - base_classes)
removed_classes = sorted(base_classes - result_classes)

expected_changed = sorted([
    MOD_ENTRY,
    "dev/akashic/asynciconcompat/mixins/MixinTextureMapHybridAsync.class",
])
if changed_classes != expected_changed:
    raise ValueError("unexpected changed classes: " + repr(changed_classes))
if added_classes:
    raise ValueError("unexpected added classes: " + repr(added_classes))
if removed_classes:
    raise ValueError("unexpected removed classes: " + repr(removed_classes))

changed_resources = sorted(
    n for n in base
    if not n.endswith(".class") and n in content and base[n] != content[n]
)
allowed_resources = sorted([
    "META-INF/MANIFEST.MF",
    "mcmod.info",
    "mixins.akashicasynccompat.refmap.json",
])
if changed_resources != allowed_resources:
    raise ValueError("unexpected changed resources: " + repr(changed_resources))

for name in result_classes:
    if major(content[name]) > 52:
        raise ValueError("Java 8 target broken: " + name)

def archive_bytes():
    with tempfile.TemporaryDirectory(prefix="patcher-beta12-jar-", dir=RUNS) as d:
        p = pathlib.Path(d) / "candidate.jar"
        with zipfile.ZipFile(p, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as z:
            for name in sorted(content):
                info = zipfile.ZipInfo(name, (2026, 9, 24, 4, 0, 0))
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
    raise ValueError("non-deterministic beta.12 archive")

OUT.parent.mkdir(parents=True, exist_ok=True)
OUT.write_bytes(jar1)

report = {
    "schema": 1,
    "version": NEW_VERSION,
    "artifact": str(OUT.relative_to(ROOT)),
    "sha256": sha(OUT),
    "bytes": OUT.stat().st_size,
    "deterministicRebuild": True,
    "baseBeta11Sha256": BASE_SHA,
    "asyncComponentVersion": "0.1.0-alpha.2.4",
    "asyncComponentSha256": ASYNC_SHA,
    "changedClasses": changed_classes,
    "changedResources": changed_resources,
    "addedClasses": added_classes,
    "removedClasses": removed_classes,
    "versionPatchProof": proof1,
    "versionPatchDeterministic": proof1 == proof2,
    "scope": (
        "beta.11 Async/Forestry plus read-only TextureMap atlas profiler: "
        "total, icon registration, ImageIO decode, Stitcher.doStitch, GL allocation, "
        "GL upload, Forge pre/post stitch hooks and residual time"
    ),
    "productionInstalled": False,
    "productionApproved": False,
}
REPORT.parent.mkdir(parents=True, exist_ok=True)
REPORT.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")

print(
    "THEPATCHER_BETA12_BUILD PASS sha256=%s bytes=%d changedClasses=%d"
    % (report["sha256"], report["bytes"], len(changed_classes))
)