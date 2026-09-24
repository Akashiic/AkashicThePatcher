import hashlib, json, pathlib, struct, zipfile

ROOT = pathlib.Path(r"C:\Users\User\Desktop\Codex\Akashic-ThePatcher-1.1.0-beta.14-UploadProfiler")
CAND = ROOT / "releases/!Akashic-ThePatcher-1.1.0-beta.14-UploadProfiler.jar"
BASE = ROOT / "base-beta13.jar"
ASYNC = ROOT / "asynccompat-alpha2.6.jar"
EXPECTED = "bb352119296eddd4e6e74c8d46a05cb209fedaefdb8f755c120ee8422a0f0ddc"

def sha_bytes(data): return hashlib.sha256(data).hexdigest()
def sha(path): return sha_bytes(path.read_bytes())
def major(data): return struct.unpack_from(">H", data, 6)[0]

with zipfile.ZipFile(CAND) as z:
    names=set(z.namelist()); data={n:z.read(n) for n in z.namelist() if not n.endswith("/")}; crc=z.testzip()
with zipfile.ZipFile(BASE) as z:
    base={n:z.read(n) for n in z.namelist() if not n.endswith("/")}
with zipfile.ZipFile(ASYNC) as z:
    async_data={n:z.read(n) for n in z.namelist() if not n.endswith("/")}

assert sha(CAND)==EXPECTED
assert crc is None

for entry in [
    "dev/akashic/asynciconcompat/CompatHooks.class",
    "dev/akashic/asynciconcompat/CompatState.class",
    "dev/akashic/asynciconcompat/mixins/MixinTextureMapHybridAsync.class",
    "dev/akashic/asynciconcompat/mixins/MixinStitcherLayoutCache.class",
    "dev/akashic/asynciconcompat/cache/StitchLayoutCache.class",
    "dev/akashic/asynciconcompat/cache/StitchLayoutCache$Entry.class",
    "dev/akashic/asynciconcompat/cache/StitchLayoutCache$Layout.class",
    "dev/akashic/asynciconcompat/profile/TextureUploadProfiler.class",
    "dev/akashic/asynciconcompat/mixins/MixinTextureUtilUploadProfiler.class",
    "mixins.akashicasynccompat.json",
    "mixins.akashicasynccompat.refmap.json",
]:
    assert entry in data
    assert sha_bytes(data[entry]) == sha_bytes(async_data[entry]), entry

assert "dev/akashic/asynciconcompat/AkashicAsyncIconCompat.class" not in names

# Preserve beta13 functionality byte-for-byte where expected.
for entry in [
    "dev/akashic/asynciconcompat/CompatHooks.class",
    "dev/akashic/asynciconcompat/CompatState.class",
    "dev/akashic/asynciconcompat/mixins/MixinStitcherLayoutCache.class",
    "dev/akashic/asynciconcompat/cache/StitchLayoutCache.class",
    "dev/akashic/asynciconcompat/cache/StitchLayoutCache$Entry.class",
    "dev/akashic/asynciconcompat/cache/StitchLayoutCache$Layout.class",
    "com/akashic/thepatcher/module/forestry/ForestryC2RuntimeGuard.class",
    "com/akashic/thepatcher/core/AkashicThePatcherLoadingPlugin.class",
]:
    assert data[entry] == base[entry], entry

prof = data["dev/akashic/asynciconcompat/profile/TextureUploadProfiler.class"]
mixin = data["dev/akashic/asynciconcompat/mixins/MixinTextureUtilUploadProfiler.class"]
for literal in [
    b"UploadBreakdown path=",
    b"levelSummary",
    b"recordGl",
    b"recordCopy",
    b"recordState",
]:
    assert literal in prof, literal
for literal in [
    b"uploadTextureSub",
    b"copyToBufferPos",
    b"glTexSubImage2D",
    b"func_147954_b",
    b"setTextureClamped",
]:
    assert literal in mixin, literal

mixin_json=data["mixins.akashicasynccompat.json"].decode("utf-8")
assert "MixinTextureMapHybridAsync" in mixin_json
assert "MixinStitcherLayoutCache" in mixin_json
assert "MixinTextureUtilUploadProfiler" in mixin_json

manifest=data["META-INF/MANIFEST.MF"].decode("utf-8")
assert "Implementation-Version: 1.1.0-beta.14" in manifest
assert '"version": "1.1.0-beta.14"' in data["mcmod.info"].decode("utf-8")

changed=sorted(n for n in base if n.endswith(".class") and n in data and base[n] != data[n])
added=sorted(n for n in data if n.endswith(".class") and n not in base)
assert changed == [
    "com/akashic/thepatcher/AkashicThePatcherMod.class",
    "dev/akashic/asynciconcompat/mixins/MixinTextureMapHybridAsync.class",
]
assert added == [
    "dev/akashic/asynciconcompat/mixins/MixinTextureUtilUploadProfiler.class",
    "dev/akashic/asynciconcompat/profile/TextureUploadProfiler.class",
]
majors={n:major(b) for n,b in data.items() if n.endswith(".class")}
assert max(majors.values()) <= 52

report={
 "schema":1,"allPassed":True,"candidate":CAND.name,"candidateSha256":sha(CAND),
 "checks":{
   "zipCrc":"PASS","alpha26MergedEntriesBitIdentity":"PASS",
   "beta13StitchCacheUnchanged":"PASS","forestryGuardUnchanged":"PASS",
   "loadingPluginUnchanged":"PASS","standaloneAsyncModAbsent":"PASS",
   "uploadProfilerTargetsPresent":"PASS","mixinConfigIncludesUploadProfiler":"PASS",
   "changedClasses":changed,"addedClasses":added,"maxClassMajor":max(majors.values())
 }}
(ROOT/"reports/targeted-gates.json").write_text(json.dumps(report,indent=2,sort_keys=True)+"\n",encoding="utf-8")
print("THEPATCHER_BETA14_TARGETED_GATES PASS sha256="+sha(CAND))