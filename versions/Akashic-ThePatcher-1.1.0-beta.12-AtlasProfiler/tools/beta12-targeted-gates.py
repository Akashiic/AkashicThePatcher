import hashlib, json, pathlib, struct, zipfile

ROOT = pathlib.Path(r"C:\Users\User\Desktop\Codex\Akashic-ThePatcher-1.1.0-beta.12-AtlasProfiler")
CAND = ROOT / "releases/!Akashic-ThePatcher-1.1.0-beta.12-AtlasProfiler.jar"
BASE = ROOT / "base-beta11.jar"
ASYNC = ROOT / "asynccompat-alpha2.4.jar"
EXPECTED = "ec498ac793af3dfc755a71b71b15b7bb85fae742b5727d1085c41975d5e274ed"

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
    "mixins.akashicasynccompat.json",
    "mixins.akashicasynccompat.refmap.json",
]
for entry in selected:
    assert sha_bytes(data[entry]) == sha_bytes(async_data[entry]), entry

mixin = data["dev/akashic/asynciconcompat/mixins/MixinTextureMapHybridAsync.class"]
required_literals = [
    b"AtlasProfile path=",
    b"loadTextureAtlas",
    b"ImageIO",
    b"doStitch",
    b"allocateTextureImpl",
    b"uploadTextureMipmap",
    b"onTextureStitchedPre",
    b"onTextureStitchedPost",
    b"shutdownNow",
    b"completeExceptionally",
]
for literal in required_literals:
    assert literal in mixin, literal

assert "dev/akashic/asynciconcompat/AkashicAsyncIconCompat.class" not in names
assert data["com/akashic/thepatcher/module/forestry/ForestryC2RuntimeGuard.class"] == base["com/akashic/thepatcher/module/forestry/ForestryC2RuntimeGuard.class"]
assert data["com/akashic/thepatcher/core/AkashicThePatcherLoadingPlugin.class"] == base["com/akashic/thepatcher/core/AkashicThePatcherLoadingPlugin.class"]

manifest = data["META-INF/MANIFEST.MF"].decode("utf-8")
assert "Implementation-Version: 1.1.0-beta.12" in manifest
assert "TweakClass: org.spongepowered.asm.launch.MixinTweaker" in manifest
assert "MixinConfigs: mixins.akashicasynccompat.json" in manifest
assert '"version": "1.1.0-beta.12"' in data["mcmod.info"].decode("utf-8")

changed_classes = sorted(
    n for n in base if n.endswith(".class") and n in data and base[n] != data[n]
)
assert changed_classes == [
    "com/akashic/thepatcher/AkashicThePatcherMod.class",
    "dev/akashic/asynciconcompat/mixins/MixinTextureMapHybridAsync.class",
]

changed_resources = sorted(
    n for n in base if not n.endswith(".class") and n in data and base[n] != data[n]
)
assert changed_resources == [
    "META-INF/MANIFEST.MF",
    "mcmod.info",
    "mixins.akashicasynccompat.refmap.json",
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
        "asyncAlpha24BitIdentity": "PASS",
        "atlasProfilerTargets": "PASS",
        "forestryGuardUnchanged": "PASS",
        "loadingPluginUnchanged": "PASS",
        "standaloneAsyncModAbsent": "PASS",
        "manifestMixinBootstrap": "PASS",
        "changedClasses": changed_classes,
        "changedResources": changed_resources,
        "maxClassMajor": max(majors.values()),
    },
}
(ROOT / "reports/targeted-gates.json").write_text(
    json.dumps(report, indent=2, sort_keys=True) + "\n",
    encoding="utf-8",
)
print("THEPATCHER_BETA12_TARGETED_GATES PASS sha256=" + sha(CAND))