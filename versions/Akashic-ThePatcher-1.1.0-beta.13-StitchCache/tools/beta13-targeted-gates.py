import hashlib, json, pathlib, struct, zipfile

ROOT = pathlib.Path(r"C:\Users\User\Desktop\Codex\Akashic-ThePatcher-1.1.0-beta.13-StitchCache")
CAND = ROOT / "releases/!Akashic-ThePatcher-1.1.0-beta.13-StitchCache.jar"
BASE = ROOT / "base-beta12.jar"
ASYNC = ROOT / "asynccompat-alpha2.5.jar"
EXPECTED = "4ea949e6962e09cee40cedb2da0bdc862e58e3cd77fede3e09334fedf95a570d"

def sha_bytes(data):
    return hashlib.sha256(data).hexdigest()

def sha(path):
    return sha_bytes(path.read_bytes())

def major(data):
    return struct.unpack_from(">H", data, 6)[0]

with zipfile.ZipFile(CAND) as z:
    names = set(z.namelist())
    data = {n: z.read(n) for n in z.namelist() if not n.endswith("/")}
    crc = z.testzip()
with zipfile.ZipFile(BASE) as z:
    base = {n: z.read(n) for n in z.namelist() if not n.endswith("/")}
with zipfile.ZipFile(ASYNC) as z:
    async_data = {n: z.read(n) for n in z.namelist() if not n.endswith("/")}

assert sha(CAND) == EXPECTED
assert crc is None

selected = [
    "dev/akashic/asynciconcompat/CompatHooks.class",
    "dev/akashic/asynciconcompat/CompatState.class",
    "dev/akashic/asynciconcompat/mixins/MixinTextureMapHybridAsync.class",
    "dev/akashic/asynciconcompat/mixins/MixinStitcherLayoutCache.class",
    "dev/akashic/asynciconcompat/cache/StitchLayoutCache.class",
    "dev/akashic/asynciconcompat/cache/StitchLayoutCache$Entry.class",
    "dev/akashic/asynciconcompat/cache/StitchLayoutCache$Layout.class",
    "mixins.akashicasynccompat.json",
    "mixins.akashicasynccompat.refmap.json",
]
for entry in selected:
    assert entry in data
    assert sha_bytes(data[entry]) == sha_bytes(async_data[entry]), entry

assert "dev/akashic/asynciconcompat/AkashicAsyncIconCompat.class" not in names

mixin_json = data["mixins.akashicasynccompat.json"].decode("utf-8")
assert "MixinTextureMapHybridAsync" in mixin_json
assert "MixinStitcherLayoutCache" in mixin_json

cache = data["dev/akashic/asynciconcompat/cache/StitchLayoutCache.class"]
mixin = data["dev/akashic/asynciconcompat/mixins/MixinStitcherLayoutCache.class"]
for literal in [
    b"SHA-256",
    b"CRC mismatch in stitch cache",
    b"stitch-layout-v1",
    b"akashic.stitchLayoutCache",
]:
    assert literal in cache, literal
for literal in [
    b"StitchLayoutCache HIT",
    b"StitchLayoutCache MISS",
    b"StitchLayoutCache SEEDED",
    b"falling back to normal stitch",
]:
    assert literal in mixin, literal

# beta12 profiler and Forestry guard must remain byte-identical
assert data["dev/akashic/asynciconcompat/mixins/MixinTextureMapHybridAsync.class"] == base["dev/akashic/asynciconcompat/mixins/MixinTextureMapHybridAsync.class"]
assert data["com/akashic/thepatcher/module/forestry/ForestryC2RuntimeGuard.class"] == base["com/akashic/thepatcher/module/forestry/ForestryC2RuntimeGuard.class"]
assert data["com/akashic/thepatcher/core/AkashicThePatcherLoadingPlugin.class"] == base["com/akashic/thepatcher/core/AkashicThePatcherLoadingPlugin.class"]

manifest = data["META-INF/MANIFEST.MF"].decode("utf-8")
assert "Implementation-Version: 1.1.0-beta.13" in manifest
assert "TweakClass: org.spongepowered.asm.launch.MixinTweaker" in manifest
assert "MixinConfigs: mixins.akashicasynccompat.json" in manifest
assert '"version": "1.1.0-beta.13"' in data["mcmod.info"].decode("utf-8")

changed_classes = sorted(
    n for n in base if n.endswith(".class") and n in data and base[n] != data[n]
)
added_classes = sorted(
    n for n in data if n.endswith(".class") and n not in base
)
assert changed_classes == [
    "com/akashic/thepatcher/AkashicThePatcherMod.class",
]
assert added_classes == [
    "dev/akashic/asynciconcompat/cache/StitchLayoutCache$Entry.class",
    "dev/akashic/asynciconcompat/cache/StitchLayoutCache$Layout.class",
    "dev/akashic/asynciconcompat/cache/StitchLayoutCache.class",
    "dev/akashic/asynciconcompat/mixins/MixinStitcherLayoutCache.class",
]

majors = {n: major(b) for n, b in data.items() if n.endswith(".class")}
assert max(majors.values()) <= 52

report = {
    "schema": 1,
    "allPassed": True,
    "candidate": CAND.name,
    "candidateSha256": sha(CAND),
    "checks": {
        "zipCrc": "PASS",
        "alpha25MergedEntriesBitIdentity": "PASS",
        "stitchCacheFingerprintAndCrcLiterals": "PASS",
        "stitchCacheHitMissSeedFallbackLiterals": "PASS",
        "beta12AtlasProfilerUnchanged": "PASS",
        "forestryGuardUnchanged": "PASS",
        "loadingPluginUnchanged": "PASS",
        "standaloneAsyncModAbsent": "PASS",
        "mixinConfigIncludesStitchCache": "PASS",
        "changedClasses": changed_classes,
        "addedClasses": added_classes,
        "maxClassMajor": max(majors.values()),
    },
}
(ROOT / "reports/targeted-gates.json").write_text(
    json.dumps(report, indent=2, sort_keys=True) + "\n",
    encoding="utf-8",
)
print("THEPATCHER_BETA13_TARGETED_GATES PASS sha256=" + sha(CAND))