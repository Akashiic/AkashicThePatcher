import hashlib
import json
import pathlib
import struct
import zipfile

ROOT = pathlib.Path(r"C:\Users\User\Desktop\Codex\Akashic-ThePatcher-1.1.0-beta.11-Async-Forestry")
CAND = ROOT / "releases/!Akashic-ThePatcher-1.1.0-beta.11-Async-Forestry.jar"
ASYNC = pathlib.Path(r"C:\Users\User\Desktop\Codex\Akashic-Hodgepodge-AsyncCompat\build\libs\akashicasynccompat-0.1.0-alpha.2.3.jar")
BASE = pathlib.Path(r"C:\Users\User\AppData\Roaming\.technic\modpacks\forbidden-series\mods\!Akashic-ThePatcher-1.1.0-beta.8-BASELINE.jar")

def sha_bytes(data):
    return hashlib.sha256(data).hexdigest()

def sha(path):
    return sha_bytes(path.read_bytes())

def major(data):
    return struct.unpack_from(">H", data, 6)[0]

with zipfile.ZipFile(CAND) as z:
    names = set(z.namelist())
    data = {n: z.read(n) for n in z.namelist() if not n.endswith("/")}

with zipfile.ZipFile(ASYNC) as z:
    async_names = set(z.namelist())
    async_data = {n: z.read(n) for n in z.namelist() if not n.endswith("/")}

with zipfile.ZipFile(BASE) as z:
    base_data = {n: z.read(n) for n in z.namelist() if not n.endswith("/")}

selected = [
    "dev/akashic/asynciconcompat/CompatHooks.class",
    "dev/akashic/asynciconcompat/CompatState.class",
    "dev/akashic/asynciconcompat/mixins/MixinTextureMapHybridAsync.class",
    "mixins.akashicasynccompat.json",
    "mixins.akashicasynccompat.refmap.json",
]

checks = {}

checks["candidate_sha"] = sha(CAND)
assert checks["candidate_sha"] == "1fb3947f88442e4f9ebe6bdc8526e7ed288d31bbfa7f0f04ad8da220fa3a152d"

checks["zip_crc"] = None
with zipfile.ZipFile(CAND) as z:
    checks["zip_crc"] = z.testzip()
assert checks["zip_crc"] is None

manifest = data["META-INF/MANIFEST.MF"].decode("utf-8", errors="strict")
checks["manifest_mixin_tweaker"] = "TweakClass: org.spongepowered.asm.launch.MixinTweaker" in manifest
checks["manifest_mixin_config"] = "MixinConfigs: mixins.akashicasynccompat.json" in manifest
checks["manifest_version"] = "Implementation-Version: 1.1.0-beta.11" in manifest
assert all([
    checks["manifest_mixin_tweaker"],
    checks["manifest_mixin_config"],
    checks["manifest_version"],
])

mcmod = data["mcmod.info"].decode("utf-8", errors="strict")
checks["mcmod_version"] = '"version": "1.1.0-beta.11"' in mcmod
assert checks["mcmod_version"]

entry_matches = {}
for entry in selected:
    assert entry in data
    assert entry in async_data
    entry_matches[entry] = sha_bytes(data[entry]) == sha_bytes(async_data[entry])
assert all(entry_matches.values())
checks["async_entries_bit_identical"] = entry_matches

checks["standalone_mod_absent"] = (
    "dev/akashic/asynciconcompat/AkashicAsyncIconCompat.class" not in names
)
checks["standalone_mcmod_not_merged"] = (
    data["mcmod.info"] != async_data["mcmod.info"]
)
assert checks["standalone_mod_absent"]
assert checks["standalone_mcmod_not_merged"]

hooks = data["dev/akashic/asynciconcompat/CompatHooks.class"]
mixin = data["dev/akashic/asynciconcompat/mixins/MixinTextureMapHybridAsync.class"]
checks["cofh_root_present"] = b"cofh." in hooks
checks["mfr_root_present"] = b"powercrystals.minefactoryreloaded." in hooks
checks["chisel_root_present"] = b"team.chisel." in hooks
checks["mekanism_root_present"] = b"mekanism." in hooks
checks["shutdown_now_present"] = b"shutdownNow" in mixin
checks["complete_exceptionally_present"] = b"completeExceptionally" in mixin
checks["stale_cleanup_present"] = b"Cleaning stale hybrid icon executor" in mixin
assert all([
    checks["cofh_root_present"],
    checks["mfr_root_present"],
    checks["chisel_root_present"],
    checks["mekanism_root_present"],
    checks["shutdown_now_present"],
    checks["complete_exceptionally_present"],
    checks["stale_cleanup_present"],
])

guard_entry = "com/akashic/thepatcher/module/forestry/ForestryC2RuntimeGuard.class"
loader_entry = "com/akashic/thepatcher/core/AkashicThePatcherLoadingPlugin.class"
assert guard_entry in data
checks["forestry_guard_flag_literal"] = (
    b"-XX:CompileCommand=exclude,forestry/core/genetics/Genome.getChromosomes"
    in data[guard_entry]
)
checks["forestry_guard_crash_literal"] = (
    b"Java 8u51 previously crashed in C2" in data[guard_entry]
)
checks["loader_references_guard"] = (
    b"com/akashic/thepatcher/module/forestry/ForestryC2RuntimeGuard"
    in data[loader_entry]
)
assert all([
    checks["forestry_guard_flag_literal"],
    checks["forestry_guard_crash_literal"],
    checks["loader_references_guard"],
])

checks["beta9_mps_absent"] = not any(
    "module/mps/" in n for n in names
)
checks["beta10_cinematic_absent"] = not any(
    "module/cinematic/" in n for n in names
)
assert checks["beta9_mps_absent"]
assert checks["beta10_cinematic_absent"]

changed_base_classes = sorted(
    n for n, b in base_data.items()
    if n.endswith(".class") and n in data and data[n] != b
)
added_classes = sorted(
    n for n in data
    if n.endswith(".class") and n not in base_data
)
checks["changed_base_classes"] = changed_base_classes
checks["added_classes"] = added_classes
assert changed_base_classes == [
    "com/akashic/thepatcher/AkashicThePatcherMod.class",
    "com/akashic/thepatcher/core/AkashicThePatcherLoadingPlugin.class",
]
assert added_classes == [
    "com/akashic/thepatcher/module/forestry/ForestryC2RuntimeGuard.class",
    "dev/akashic/asynciconcompat/CompatHooks.class",
    "dev/akashic/asynciconcompat/CompatState.class",
    "dev/akashic/asynciconcompat/mixins/MixinTextureMapHybridAsync.class",
]

class_majors = {
    n: major(b)
    for n, b in data.items()
    if n.endswith(".class")
}
checks["max_class_major"] = max(class_majors.values())
assert checks["max_class_major"] <= 52

report = {
    "schema": 1,
    "allPassed": True,
    "candidate": CAND.name,
    "candidateSha256": sha(CAND),
    "checks": checks,
}
(ROOT / "reports/targeted-gates.json").write_text(
    json.dumps(report, indent=2, sort_keys=True) + "\n",
    encoding="utf-8",
)
print(
    "THEPATCHER_BETA11_TARGETED_GATES PASS checks=%d sha256=%s"
    % (len(checks), sha(CAND))
)