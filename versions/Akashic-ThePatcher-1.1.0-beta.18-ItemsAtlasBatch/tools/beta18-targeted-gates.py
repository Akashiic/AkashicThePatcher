import hashlib, json, pathlib, struct, zipfile

ROOT = pathlib.Path(r"C:\Users\User\Desktop\Codex\Akashic-ThePatcher-1.1.0-beta.18-ItemsAtlasBatch")
CAND = ROOT / "releases/!Akashic-ThePatcher-1.1.0-beta.18-ItemsAtlasBatch.jar"
BASE = ROOT / "base-beta17.jar"
EXPECTED = "12f00211e9f8cd66cad9d9a90da7f809d4e1987a72d31461dfbe42edf5169262"

def sha_bytes(data):
    return hashlib.sha256(data).hexdigest()

def sha(path):
    return sha_bytes(path.read_bytes())

def major(data):
    return struct.unpack_from(">H", data, 6)[0]

assert sha(CAND) == EXPECTED

with zipfile.ZipFile(CAND) as z:
    assert z.testzip() is None
    data = {n: z.read(n) for n in z.namelist() if not n.endswith("/")}
with zipfile.ZipFile(BASE) as z:
    base = {n: z.read(n) for n in z.namelist() if not n.endswith("/")}

changed = sorted(
    n for n in base if n.endswith(".class") and n in data and base[n] != data[n]
)
added = sorted(
    n for n in data if n.endswith(".class") and n not in base
)
removed = sorted(
    n for n in base if n.endswith(".class") and n not in data
)

assert changed == ["com/akashic/thepatcher/AkashicThePatcherMod.class"]
assert added == sorted([
    "dev/akashic/asynciconcompat/upload/ItemsAtlasUploadBatcher.class",
    "dev/akashic/asynciconcompat/mixins/MixinTextureMapItemsUploadBatch.class",
    "dev/akashic/asynciconcompat/mixins/MixinTextureUtilItemsUploadBatch.class",
])
assert not removed

preserved = [
    "com/akashic/thepatcher/core/AkashicThePatcherLoadingPlugin.class",
    "com/akashic/thepatcher/module/forestry/ForestryC2RuntimeGuard.class",
    "com/akashic/thepatcher/module/forestry/ForestryGenomeCompat.class",
    "com/akashic/thepatcher/module/forestry/ForestryGenomeC2Transformer.class",
    "dev/akashic/asynciconcompat/CompatHooks.class",
    "dev/akashic/asynciconcompat/CompatState.class",
    "dev/akashic/asynciconcompat/mixins/MixinTextureMapHybridAsync.class",
    "dev/akashic/asynciconcompat/mixins/MixinStitcherLayoutCache.class",
    "dev/akashic/asynciconcompat/mixins/MixinTextureUtilUploadProfiler.class",
    "dev/akashic/asynciconcompat/mixins/MixinAngelicaMipmapProfiler.class",
    "dev/akashic/asynciconcompat/profile/TextureUploadProfiler.class",
    "dev/akashic/asynciconcompat/cache/StitchLayoutCache.class",
]
for entry in preserved:
    assert data[entry] == base[entry], entry

for name, blob in data.items():
    if name.endswith(".class"):
        assert major(blob) <= 52, name

cfg = data["mixins.akashicasynccompat.json"].decode("utf-8")
assert "MixinTextureMapItemsUploadBatch" in cfg
assert "MixinTextureUtilItemsUploadBatch" in cfg
assert "MixinForestryGenomeC2" not in cfg

refmap = data["mixins.akashicasynccompat.refmap.json"].decode("utf-8")
assert "MixinTextureMapItemsUploadBatch" in refmap
assert "MixinTextureUtilItemsUploadBatch" in refmap

batch = data["dev/akashic/asynciconcompat/upload/ItemsAtlasUploadBatcher.class"]
for literal in [
    b"textures/items",
    b"akashic.itemsAtlasBatch",
    b"already-consumed",
    b"world-loaded",
    b"mipmap-levels",
    b"small-atlas",
    b"anaglyph",
    b"ItemsAtlasBatch FLUSH",
    b"fail-open",
]:
    assert literal in batch, literal

map_mixin = data[
    "dev/akashic/asynciconcompat/mixins/MixinTextureMapItemsUploadBatch.class"
]
for literal in [
    b"allocateTextureImpl",
    b"onTextureStitchedPost",
    b"flushIfActive",
    b"abortIfActive",
]:
    assert literal in map_mixin, literal

util_mixin = data[
    "dev/akashic/asynciconcompat/mixins/MixinTextureUtilItemsUploadBatch.class"
]
for literal in [
    b"uploadTextureMipmap",
    b"ItemsAtlasUploadBatcher",
    b"stage",
]:
    assert literal in util_mixin, literal

# For a 4096x4096 atlas, BUFFER_INTS=1,048,576 => 256 rows => 16 batched GL calls.
buffer_ints = 1_048_576
width = 4096
height = 4096
rows_per = max(1, buffer_ints // width)
batch_calls = (height + rows_per - 1) // rows_per
assert rows_per == 256
assert batch_calls == 16

report = {
    "schema": 1,
    "allPassed": True,
    "candidate": CAND.name,
    "candidateSha256": sha(CAND),
    "checks": {
        "zipCrc": "PASS",
        "strictClassDelta": "PASS",
        "java8ClassMajor": "PASS",
        "forestryUniversalBitIdentity": "PASS",
        "stitchCacheBitIdentity": "PASS",
        "hybridAsyncBitIdentity": "PASS",
        "uploadProfilerBitIdentity": "PASS",
        "rejectedForestryMixinAbsent": "PASS",
        "newMixinsInConfigAndRefmap": "PASS",
        "itemsOnlyGuardMarkers": "PASS",
        "oneShotStartupGuardMarkers": "PASS",
        "failOpenMarkers": "PASS",
        "expected4096RowsPerBatch": rows_per,
        "expected4096BatchGlCalls": batch_calls,
    },
    "changedClasses": changed,
    "addedClasses": added,
}
(ROOT / "reports/targeted-gates.json").write_text(
    json.dumps(report, indent=2, sort_keys=True) + "\n",
    encoding="utf-8",
)
print("THEPATCHER_BETA18_TARGETED_GATES PASS sha256=" + sha(CAND))