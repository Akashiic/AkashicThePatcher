import hashlib, json, pathlib, struct, zipfile

ROOT = pathlib.Path(r"C:\Users\User\Desktop\Codex\Akashic-ThePatcher-1.1.0-beta.16-UniversalForestryC2")
CAND = ROOT / "releases/!Akashic-ThePatcher-1.1.0-beta.16-UniversalForestryC2.jar"
BASE = ROOT / "base-beta15.jar"
ASYNC = ROOT / "asynccompat-alpha2.8.jar"
FORESTRY = pathlib.Path(r"C:\Users\User\Desktop\Codex\Akashic-Hodgepodge-AsyncCompat\libs\Forestry-4.11.36.jar")
EXPECTED = "8ec2e72b2fb9488ac759e24705c22a1a05181afb107b861ca67a4c8139a4a483"
FORESTRY_SHA = "78daf273c4d112324ea7fb3ad9fbac99ecd088c1b78982811deebd58da712d6f"
GENOME_SHA = "fe62518c6126cc715610ef3ced8685edac0e5aeb0c0315a5c1d2d284d55f2683"

def sha_bytes(data): return hashlib.sha256(data).hexdigest()
def sha(path): return sha_bytes(path.read_bytes())
def major(data): return struct.unpack_from(">H", data, 6)[0]

with zipfile.ZipFile(CAND) as z:
    names=set(z.namelist()); data={n:z.read(n) for n in z.namelist() if not n.endswith("/")}; crc=z.testzip()
with zipfile.ZipFile(BASE) as z:
    base={n:z.read(n) for n in z.namelist() if not n.endswith("/")}
with zipfile.ZipFile(ASYNC) as z:
    async_data={n:z.read(n) for n in z.namelist() if not n.endswith("/")}
with zipfile.ZipFile(FORESTRY) as z:
    genome=z.read("forestry/core/genetics/Genome.class")

assert sha(CAND)==EXPECTED
assert crc is None
assert sha(FORESTRY)==FORESTRY_SHA
assert sha_bytes(genome)==GENOME_SHA
assert not any(n.startswith("forestry/") for n in names)
assert "dev/akashic/asynciconcompat/AkashicAsyncIconCompat.class" not in names

# Exact alpha2.8 merge identity.
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
    "dev/akashic/asynciconcompat/forestry/ForestryGenomeC2Shim.class",
    "dev/akashic/asynciconcompat/mixins/MixinForestryGenomeC2.class",
    "mixins.akashicasynccompat.json",
    "mixins.akashicasynccompat.refmap.json",
]:
    assert data[entry] == async_data[entry], entry

# All beta.15 performance components remain byte-identical.
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
    "com/akashic/thepatcher/core/AkashicThePatcherLoadingPlugin.class",
]:
    assert data[entry] == base[entry], entry

overwrite=data["dev/akashic/asynciconcompat/mixins/MixinForestryGenomeC2.class"]
shim=data["dev/akashic/asynciconcompat/forestry/ForestryGenomeC2Shim.class"]
guard=data["com/akashic/thepatcher/module/forestry/ForestryC2RuntimeGuard.class"]

assert len(overwrite) < 1500
for literal in [b"ForestryGenomeC2Shim", b"getChromosomes"]:
    assert literal in overwrite

for literal in [
    b"loadStoredChromosomes",
    b"repairAndValidateChromosome",
    b"fillMissingChromosomes",
    b"ForestryGenomeC2Shim ACTIVE",
    b"akashic.forestryC2Shim.executed",
]:
    assert literal in shim

for literal in [
    b"Universal Genome shim bundled",
    b"JVM CompileCommand not required",
    b"akashic.forestryC2Guard.universalShim",
    b"akashic.forestryC2Guard.compilerOracle",
]:
    assert literal in guard

mixin_json=data["mixins.akashicasynccompat.json"].decode("utf-8")
assert "MixinForestryGenomeC2" in mixin_json
assert "1.1.0-beta.16" in data["mcmod.info"].decode("utf-8")
assert "Implementation-Version: 1.1.0-beta.16" in data["META-INF/MANIFEST.MF"].decode("utf-8")

changed=sorted(n for n in base if n.endswith(".class") and n in data and base[n] != data[n])
added=sorted(n for n in data if n.endswith(".class") and n not in base)
assert changed == [
    "com/akashic/thepatcher/AkashicThePatcherMod.class",
    "com/akashic/thepatcher/module/forestry/ForestryC2RuntimeGuard.class",
]
assert added == [
    "dev/akashic/asynciconcompat/forestry/ForestryGenomeC2Shim.class",
    "dev/akashic/asynciconcompat/mixins/MixinForestryGenomeC2.class",
]

majors={n:major(b) for n,b in data.items() if n.endswith(".class")}
assert max(majors.values()) <= 52

report={
 "schema":1,"allPassed":True,"candidate":CAND.name,"candidateSha256":sha(CAND),
 "forestryJarSha256":sha(FORESTRY),"forestryGenomeClassSha256":sha_bytes(genome),
 "checks":{
   "zipCrc":"PASS",
   "alpha28MergeIdentity":"PASS",
   "beta15PerformanceCorePreserved":"PASS",
   "forestryDependencyNotBundled":"PASS",
   "standaloneAsyncModAbsent":"PASS",
   "overwriteTinyDelegate":"PASS",
   "shimSplitMarkers":"PASS",
   "guardUniversalModeMarkers":"PASS",
   "changedClasses":changed,
   "addedClasses":added,
   "maxClassMajor":max(majors.values())
 }}
(ROOT/"reports/targeted-gates.json").write_text(json.dumps(report,indent=2,sort_keys=True)+"\n",encoding="utf-8")
print("THEPATCHER_BETA16_TARGETED_GATES PASS sha256="+sha(CAND))