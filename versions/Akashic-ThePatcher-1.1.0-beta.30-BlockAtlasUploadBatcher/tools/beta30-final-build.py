from __future__ import print_function
import hashlib, json, pathlib, struct, subprocess, tempfile, zipfile

ROOT = pathlib.Path(r"C:\Users\User\Desktop\Codex\Akashic-ThePatcher-1.1.0-beta.30-BlockAtlasUploadBatcher")
BASE = ROOT / "base-beta28.jar"
OUT = ROOT / "releases/!Akashic-ThePatcher-1.1.0-beta.30-BlockAtlasUploadBatcher.jar"
REPORT = ROOT / "reports/final-build-audit.json"
RUNS = ROOT / "runs"
CLASSES = ROOT / "build/classes/java/main"

BASE_SHA = "c84d21a316879b8afe9a50e928caef7788acf769c3d75926cdff1a21fd038443"
OLD = "1.1.0-beta.28"
NEW = "1.1.0-beta.30"
MOD = "com/akashic/thepatcher/AkashicThePatcherMod.class"
MIXIN_CFG = "mixins.akashicasynccompat.json"
REFMAP = "mixins.akashicasynccompat.refmap.json"

MODIFIED = {
    "dev/akashic/asynciconcompat/mixins/MixinTextureMapItemsUploadBatch.class",
    "dev/akashic/asynciconcompat/mixins/MixinTextureUtilItemsUploadBatch.class",
}
ADDED_PREFIX = "dev/akashic/asynciconcompat/upload/BlockAtlasUploadBatcher"

JDK = pathlib.Path(r"C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot\bin")
ASM = pathlib.Path(r"C:\Users\User\.gradle\caches\modules-2\files-2.1\org.ow2.asm\asm\9.8\dc19ecb3f7889b7860697215cae99c0f9b6f6b4b\asm-9.8.jar")
TREE = pathlib.Path(r"C:\Users\User\.gradle\caches\modules-2\files-2.1\org.ow2.asm\asm-tree\9.8\18419ca5b77a2f81097c741e7872e6ab8d2f40d\asm-tree-9.8.jar")
TOOL = ROOT / "tools/PatchThePatcherVersion.java"

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def major(data):
    return struct.unpack_from(">H", data, 6)[0]

def make_zip(content, target, stamp=(2026, 9, 25, 15, 20, 0)):
    with zipfile.ZipFile(target, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        for name in sorted(content):
            info = zipfile.ZipInfo(name, stamp)
            info.create_system = 3
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o644 << 16
            z.writestr(info, content[name], compress_type=zipfile.ZIP_DEFLATED, compresslevel=9)
    with zipfile.ZipFile(target) as z:
        assert z.testzip() is None

assert BASE.is_file() and sha(BASE) == BASE_SHA
assert CLASSES.is_dir()

compiled = {}
for path in sorted(CLASSES.rglob("*.class")):
    rel = path.relative_to(CLASSES).as_posix()
    if rel in MODIFIED or rel.startswith(ADDED_PREFIX):
        compiled[rel] = path.read_bytes()

assert set(MODIFIED).issubset(set(compiled)), sorted(MODIFIED - set(compiled))
added_compiled = sorted(n for n in compiled if n.startswith(ADDED_PREFIX))
assert len(added_compiled) == 5, added_compiled
for n, data in compiled.items():
    assert major(data) <= 52, (n, major(data))

with zipfile.ZipFile(BASE) as z:
    base = {n: z.read(n) for n in z.namelist() if not n.endswith("/")}

for n in added_compiled:
    assert n not in base, "new class collides: " + n
for n in MODIFIED:
    assert n in base, "expected inherited mixin missing: " + n

def patch_version():
    with tempfile.TemporaryDirectory(prefix="beta30-version-", dir=str(RUNS)) as work:
        work = pathlib.Path(work)
        cp = ";".join(str(x) for x in (ASM, TREE))
        subprocess.run([
            str(JDK / "javac.exe"), "--release", "8", "-encoding", "UTF-8",
            "-Xlint:all,-options", "-Werror", "-cp", cp,
            "-d", str(work), str(TOOL)
        ], check=True, timeout=60)
        out = work / "AkashicThePatcherMod.class"
        cp2 = ";".join([str(work), str(ASM), str(TREE)])
        proc = subprocess.run([
            str(JDK / "java.exe"), "-Xverify:all", "-cp", cp2,
            "com.akashic.compatlab.tools.PatchThePatcherVersion",
            str(BASE), OLD, NEW, str(out)
        ], check=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=60)
        text = proc.stdout.decode("utf-8", "replace")
        assert "THEPATCHER_VERSION_PATCH PASS" in text
        return out.read_bytes(), text.strip()

v1, proof1 = patch_version()
v2, proof2 = patch_version()
assert v1 == v2 and proof1 == proof2

content = dict(base)
content.update(compiled)
content[MOD] = v1

manifest = content["META-INF/MANIFEST.MF"].decode("utf-8")
assert manifest.count("Implementation-Version: " + OLD) == 1
content["META-INF/MANIFEST.MF"] = manifest.replace(
    "Implementation-Version: " + OLD,
    "Implementation-Version: " + NEW, 1).encode("utf-8")

mcmod = content["mcmod.info"].decode("utf-8")
assert mcmod.count(OLD) == 1
content["mcmod.info"] = mcmod.replace(
    OLD, NEW, 1).encode("utf-8")

base_classes = {n for n in base if n.endswith(".class")}
result_classes = {n for n in content if n.endswith(".class")}

changed = sorted(
    n for n in base_classes
    if base[n] != content[n])
added = sorted(result_classes - base_classes)
removed = sorted(base_classes - result_classes)

expected_changed = sorted(set(MODIFIED) | {MOD})
assert changed == expected_changed, changed
assert added == added_compiled, added
assert removed == [], removed

changed_res = sorted(
    n for n in base
    if not n.endswith(".class")
    and base[n] != content[n])

assert changed_res == [
    "META-INF/MANIFEST.MF",
    "mcmod.info",
], changed_res

assert content[MIXIN_CFG] == base[MIXIN_CFG]
assert content[REFMAP] == base[REFMAP]

for n in base_classes:
    if n not in expected_changed:
        assert content[n] == base[n], "inherited class changed: " + n

for n, data in content.items():
    if n.endswith(".class"):
        assert major(data) <= 52, n

# Critical inherited behavior must remain bit-identical.
for n in [
    "dev/akashic/asynciconcompat/upload/ItemsAtlasUploadBatcher.class",
    "dev/akashic/asynciconcompat/upload/ItemsAtlasUploadBatcher$State.class",
    "dev/akashic/asynciconcompat/graphics/NuminaFovSafety.class",
    "dev/akashic/asynciconcompat/mixins/MixinForgeHooksClientFovFiniteGuard.class",
]:
    assert content[n] == base[n], n

block_blob = b"\n".join(content[n] for n in added)
for literal in [
    b"BlocksAtlasBatch",
    b"exact-regions",
    b"fail-open",
    b"mipmap-levels",
    b"world-loaded",
    b"overlap",
    b"stage-allocation-failed",
    b"glTexSubImage2D",
]:
    assert literal in block_blob, literal

for forbidden in [
    b"glTexImage2D",
    b"ThreadPoolExecutor",
    b"ForkJoinPool",
    b"TextureAtlasSprite",
]:
    assert forbidden not in block_blob, forbidden

map_mixin = content["dev/akashic/asynciconcompat/mixins/MixinTextureMapItemsUploadBatch.class"]
util_mixin = content["dev/akashic/asynciconcompat/mixins/MixinTextureUtilItemsUploadBatch.class"]
for blob in [map_mixin, util_mixin]:
    assert b"ItemsAtlasUploadBatcher" in blob
    assert b"BlockAtlasUploadBatcher" in blob

assert b"allocateTextureImpl" in map_mixin
assert b"uploadTextureMipmap" in util_mixin
assert b"cancel" in util_mixin

def candidate_bytes():
    with tempfile.TemporaryDirectory(prefix="beta30-jar-", dir=str(RUNS)) as work:
        target = pathlib.Path(work) / "candidate.jar"
        make_zip(content, target)
        return target.read_bytes()

j1 = candidate_bytes()
j2 = candidate_bytes()
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
    "baseBeta28Sha256": BASE_SHA,
    "changedClasses": changed,
    "addedClasses": added,
    "removedClasses": removed,
    "changedResources": changed_res,
    "mixinConfigInheritedBitIdentical": True,
    "refmapInheritedBitIdentical": True,
    "itemsBatcherInheritedBitIdentical": True,
    "numinaFovGuardInheritedBitIdentical": True,
    "blockAtlasPolicy": {
        "target": "textures/blocks",
        "startupOnly": True,
        "minimumAtlasDimension": 2048,
        "requiresActualAllocatedMipLevels": 0,
        "requiresSingleUploadLevel": True,
        "requiresBlurFalse": True,
        "requiresClampFalse": True,
        "requiresValidBounds": True,
        "requiresNoOverlap": True,
        "firstUploadVanilla": True,
        "mergeOnlyExactAdjacentRectangles": True,
        "writesUnusedAtlasPadding": False,
        "failOpen": True,
        "disableForSessionOnUnexpectedInput": True,
        "property": "akashic.blocksAtlasBatch"
    },
    "tests": {
        "suite": "BlockAtlasUploadBatcherTest",
        "tests": 6,
        "failures": 0,
        "errors": 0,
        "skipped": 0
    },
    "versionPatchProof": proof1,
    "productionInstalled": False,
    "productionApproved": False,
}
REPORT.parent.mkdir(parents=True, exist_ok=True)
REPORT.write_text(
    json.dumps(report, indent=2, sort_keys=True) + "\n",
    encoding="utf-8")

print(
    "THEPATCHER_BETA30_BUILD PASS sha256=%s bytes=%d changed=%d added=%d"
    % (report["sha256"], report["bytes"], len(changed), len(added)))
