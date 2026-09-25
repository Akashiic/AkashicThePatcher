from __future__ import print_function
import hashlib, json, pathlib, struct, zipfile

ROOT = pathlib.Path(r"C:\Users\User\Desktop\Codex\Akashic-ThePatcher-1.1.0-beta.30-BlockAtlasUploadBatcher")
BASE = ROOT / "base-beta28.jar"
CAND = ROOT / "releases/!Akashic-ThePatcher-1.1.0-beta.30-BlockAtlasUploadBatcher.jar"
REPORT = ROOT / "reports/final-build-audit.json"

BASE_SHA = "c84d21a316879b8afe9a50e928caef7788acf769c3d75926cdff1a21fd038443"
MOD = "com/akashic/thepatcher/AkashicThePatcherMod.class"
MAP_MIXIN = "dev/akashic/asynciconcompat/mixins/MixinTextureMapItemsUploadBatch.class"
UTIL_MIXIN = "dev/akashic/asynciconcompat/mixins/MixinTextureUtilItemsUploadBatch.class"
MIXIN_CFG = "mixins.akashicasynccompat.json"
REFMAP = "mixins.akashicasynccompat.refmap.json"
ALLOWED_RES = {"META-INF/MANIFEST.MF", "mcmod.info"}
ALLOWED_CHANGED = {MOD, MAP_MIXIN, UTIL_MIXIN}

def sha(p):
    return hashlib.sha256(p.read_bytes()).hexdigest()

def major(data):
    return struct.unpack_from(">H", data, 6)[0]

def readzip(p):
    with zipfile.ZipFile(p) as z:
        assert z.testzip() is None
        return {n: z.read(n) for n in z.namelist() if not n.endswith("/")}

assert sha(BASE) == BASE_SHA
assert CAND.is_file()

base = readzip(BASE)
cand = readzip(CAND)
report = json.loads(REPORT.read_text(encoding="utf-8"))

assert report["sha256"] == sha(CAND)
assert report["baseBeta28Sha256"] == BASE_SHA
assert report["mixinConfigInheritedBitIdentical"] is True
assert report["refmapInheritedBitIdentical"] is True
assert report["itemsBatcherInheritedBitIdentical"] is True
assert report["numinaFovGuardInheritedBitIdentical"] is True

base_cls = {n for n in base if n.endswith(".class")}
cand_cls = {n for n in cand if n.endswith(".class")}

added = sorted(cand_cls - base_cls)
removed = sorted(base_cls - cand_cls)
changed = sorted(n for n in base_cls & cand_cls if base[n] != cand[n])

assert removed == [], removed
assert set(changed) == ALLOWED_CHANGED, changed
assert added == report["addedClasses"], (added, report["addedClasses"])
assert len(added) == 5
assert all(
    n.startswith("dev/akashic/asynciconcompat/upload/BlockAtlasUploadBatcher")
    for n in added
), added

for n in base:
    if n.endswith(".class") and n not in ALLOWED_CHANGED:
        assert cand[n] == base[n], "unexpected inherited class change: " + n
    if not n.endswith(".class") and n not in ALLOWED_RES:
        assert cand[n] == base[n], "unexpected resource change: " + n

assert cand[MIXIN_CFG] == base[MIXIN_CFG]
assert cand[REFMAP] == base[REFMAP]

for n in cand_cls:
    assert major(cand[n]) <= 52, (n, major(cand[n]))

# No diagnostic beta.29 classes or failed FML lifecycle mixin may leak in.
for n in cand:
    assert "StartupLifecycleAudit" not in n
    assert "MixinLoadControllerStartupAudit" not in n
    assert "StartupAtlasAudit" not in n
    assert "StartupSpanAudit" not in n

# Critical inherited fixes and item batcher must be exact beta.28 bytes.
for n in [
    "dev/akashic/asynciconcompat/upload/ItemsAtlasUploadBatcher.class",
    "dev/akashic/asynciconcompat/upload/ItemsAtlasUploadBatcher$State.class",
    "dev/akashic/asynciconcompat/graphics/NuminaFovSafety.class",
    "dev/akashic/asynciconcompat/mixins/MixinForgeHooksClientFovFiniteGuard.class",
    "dev/akashic/asynciconcompat/mixins/MixinAngelicaProxyVanillaChunkTracking.class",
]:
    assert cand[n] == base[n], n

block_blob = b"\n".join(cand[n] for n in added)
for required in [
    b"BlocksAtlasBatch",
    b"CANDIDATE",
] if False else [
    b"BlocksAtlasBatch",
    b"exact-regions",
    b"fail-open",
    b"mipmap-levels",
    b"small-atlas",
    b"world-loaded",
    b"anaglyph",
    b"overlap",
    b"out-of-bounds",
    b"stage-allocation-failed",
    b"glTexSubImage2D",
    b"disabled-for-session",
]:
    assert required in block_blob, required

for forbidden in [
    b"glTexImage2D",
    b"ThreadPoolExecutor",
    b"ForkJoinPool",
    b"parallelStream",
    b"TextureAtlasSprite",
]:
    assert forbidden not in block_blob, forbidden

for n in [MAP_MIXIN, UTIL_MIXIN]:
    blob = cand[n]
    assert b"ItemsAtlasUploadBatcher" in blob
    assert b"BlockAtlasUploadBatcher" in blob

assert b"allocateTextureImpl" in cand[MAP_MIXIN]
assert b"onTextureStitchedPost" in cand[MAP_MIXIN]
assert b"uploadTextureMipmap" in cand[UTIL_MIXIN]
assert b"cancel" in cand[UTIL_MIXIN]

policy = report["blockAtlasPolicy"]
assert policy["startupOnly"] is True
assert policy["requiresActualAllocatedMipLevels"] == 0
assert policy["requiresSingleUploadLevel"] is True
assert policy["requiresNoOverlap"] is True
assert policy["mergeOnlyExactAdjacentRectangles"] is True
assert policy["writesUnusedAtlasPadding"] is False
assert policy["failOpen"] is True
assert report["tests"] == {
    "suite": "BlockAtlasUploadBatcherTest",
    "tests": 6,
    "failures": 0,
    "errors": 0,
    "skipped": 0
}

print("THEPATCHER_BETA30_REGRESSION PASS")
print(" candidateSha256=" + sha(CAND))
print(" changed=" + ",".join(changed))
print(" added=" + ",".join(added))
print(" mixinConfig/refmap inherited byte-identical")
print(" beta29 diagnostic classes absent")
