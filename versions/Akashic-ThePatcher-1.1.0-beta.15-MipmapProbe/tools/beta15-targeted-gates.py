import hashlib, json, pathlib, struct, subprocess, zipfile

ROOT = pathlib.Path(r"C:\Users\User\Desktop\Codex\Akashic-ThePatcher-1.1.0-beta.15-MipmapProbe")
CAND = ROOT / "releases/!Akashic-ThePatcher-1.1.0-beta.15-MipmapProbe.jar"
BASE = ROOT / "base-beta14.jar"
ASYNC = ROOT / "asynccompat-alpha2.7.jar"
ANGELICA = pathlib.Path(r"C:\Users\User\AppData\Roaming\.technic\modpacks\forbidden-series\mods\angelica-2.2.18.jar")
JAVAP = pathlib.Path(r"C:\Program Files\Java\jdk-1.8\bin\javap.exe")

EXPECTED = "1e7008edf76156c07f19be7151554d87c677b675db723a7c61c89e3d5f09bd0d"
ANGELICA_SHA = "563d907f3035aedc7761be0f882674d6e736b63c5860e126e7af7df0d9f15c50"

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
assert sha(ANGELICA) == ANGELICA_SHA

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
    "dev/akashic/asynciconcompat/mixins/MixinAngelicaMipmapProfiler.class",
    "mixins.akashicasynccompat.json",
]:
    assert entry in data
    assert sha_bytes(data[entry]) == sha_bytes(async_data[entry]), entry

assert "dev/akashic/asynciconcompat/AkashicAsyncIconCompat.class" not in names

for entry in [
    "dev/akashic/asynciconcompat/CompatHooks.class",
    "dev/akashic/asynciconcompat/CompatState.class",
    "dev/akashic/asynciconcompat/mixins/MixinTextureMapHybridAsync.class",
    "dev/akashic/asynciconcompat/mixins/MixinStitcherLayoutCache.class",
    "dev/akashic/asynciconcompat/cache/StitchLayoutCache.class",
    "dev/akashic/asynciconcompat/cache/StitchLayoutCache$Entry.class",
    "dev/akashic/asynciconcompat/cache/StitchLayoutCache$Layout.class",
    "dev/akashic/asynciconcompat/mixins/MixinTextureUtilUploadProfiler.class",
    "com/akashic/thepatcher/module/forestry/ForestryC2RuntimeGuard.class",
    "com/akashic/thepatcher/core/AkashicThePatcherLoadingPlugin.class",
]:
    assert data[entry] == base[entry], entry

probe = data["dev/akashic/asynciconcompat/mixins/MixinAngelicaMipmapProfiler.class"]
for literal in [
    b"com.gtnewhorizons.angelica.glsm.GLStateManager",
    b"maybeGenerateMipmap",
    b"RenderBackend",
    b"generateMipmap",
    b"recordMipmapCheck",
    b"mipmapGenerateEnter",
    b"mipmapGenerateExit",
]:
    assert literal in probe, literal

prof = data["dev/akashic/asynciconcompat/profile/TextureUploadProfiler.class"]
for literal in [b"mipChecks=", b"mipGen=", b"mipGenMs="]:
    assert literal in prof, literal

mixin_json = data["mixins.akashicasynccompat.json"].decode("utf-8")
for name in [
    "MixinTextureMapHybridAsync",
    "MixinStitcherLayoutCache",
    "MixinTextureUtilUploadProfiler",
    "MixinAngelicaMipmapProfiler",
]:
    assert name in mixin_json

javap = subprocess.run([
    str(JAVAP), "-classpath", str(ANGELICA), "-c", "-p",
    "com.gtnewhorizons.angelica.glsm.GLStateManager",
], check=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=60)
javap_text = javap.stdout.decode("utf-8", errors="replace")
assert "private static void maybeGenerateMipmap(int, int);" in javap_text
assert "RenderBackend.generateMipmap:(I)V" in javap_text

changed = sorted(
    n for n in base if n.endswith(".class") and n in data and base[n] != data[n]
)
added = sorted(
    n for n in data if n.endswith(".class") and n not in base
)
assert changed == [
    "com/akashic/thepatcher/AkashicThePatcherMod.class",
    "dev/akashic/asynciconcompat/profile/TextureUploadProfiler.class",
]
assert added == [
    "dev/akashic/asynciconcompat/mixins/MixinAngelicaMipmapProfiler.class",
]

majors = {n: major(b) for n, b in data.items() if n.endswith(".class")}
assert max(majors.values()) <= 52

report = {
    "schema": 1,
    "allPassed": True,
    "candidate": CAND.name,
    "candidateSha256": sha(CAND),
    "angelicaSha256": sha(ANGELICA),
    "checks": {
        "zipCrc": "PASS",
        "alpha27MergedEntriesBitIdentity": "PASS",
        "beta14UploadProfilerCorePreserved": "PASS",
        "beta13StitchCachePreserved": "PASS",
        "forestryGuardUnchanged": "PASS",
        "angelicaTargetClassPinned": "PASS",
        "maybeGenerateMipmapMethodExists": "PASS",
        "generateMipmapInvokeExists": "PASS",
        "pseudoMixinTargetStrings": "PASS",
        "standaloneAsyncModAbsent": "PASS",
        "changedClasses": changed,
        "addedClasses": added,
        "maxClassMajor": max(majors.values()),
    },
}
(ROOT / "reports/targeted-gates.json").write_text(
    json.dumps(report, indent=2, sort_keys=True) + "\n",
    encoding="utf-8",
)
print("THEPATCHER_BETA15_TARGETED_GATES PASS sha256=" + sha(CAND))