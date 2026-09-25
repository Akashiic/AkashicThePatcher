from __future__ import print_function
import hashlib, json, pathlib, struct, zipfile

ROOT = pathlib.Path(r"C:\Users\User\Desktop\Codex\Akashic-ThePatcher-1.1.0-beta.29-StartupAudit")
BASE = ROOT / "base-beta28.jar"
CAND = ROOT / "releases/!Akashic-ThePatcher-1.1.0-beta.29-StartupAudit.jar"
REPORT = ROOT / "reports/final-build-audit.json"
BASE_SHA = "c84d21a316879b8afe9a50e928caef7788acf769c3d75926cdff1a21fd038443"
MOD = "com/akashic/thepatcher/AkashicThePatcherMod.class"
MIXIN_CFG = "mixins.akashicasynccompat.json"
ALLOWED_RES = {"META-INF/MANIFEST.MF", "mcmod.info", MIXIN_CFG}

def sha(p): return hashlib.sha256(p.read_bytes()).hexdigest()
def major(data): return struct.unpack_from(">H", data, 6)[0]
def readzip(p):
    with zipfile.ZipFile(p) as z:
        assert z.testzip() is None
        return {n:z.read(n) for n in z.namelist() if not n.endswith("/")}

assert sha(BASE) == BASE_SHA
assert CAND.is_file()
base=readzip(BASE); cand=readzip(CAND)
report=json.loads(REPORT.read_text(encoding="utf-8"))
assert report["sha256"] == sha(CAND)
assert report["passiveOnly"] is True
assert report["inheritedBeta28ClassesBitIdentical"] is True
assert report["refmapInheritedBitIdentical"] is True

base_cls={n for n in base if n.endswith(".class")}
cand_cls={n for n in cand if n.endswith(".class")}
added=sorted(cand_cls-base_cls)
removed=sorted(base_cls-cand_cls)
changed=sorted(n for n in base_cls & cand_cls if base[n]!=cand[n])
assert removed == []
assert changed == [MOD], changed
assert added == report["addedClasses"], (added, report["addedClasses"])
assert all(n.startswith("dev/akashic/asynciconcompat/") for n in added)

for n in base:
    if n.endswith(".class") and n != MOD:
        assert cand[n] == base[n], "inherited class changed: "+n
    if not n.endswith(".class") and n not in ALLOWED_RES:
        assert cand[n] == base[n], "unexpected resource changed: "+n

assert cand["mixins.akashicasynccompat.refmap.json"] == base["mixins.akashicasynccompat.refmap.json"]
base_cfg=json.loads(base[MIXIN_CFG].decode("utf-8"))
cand_cfg=json.loads(cand[MIXIN_CFG].decode("utf-8"))
assert cand_cfg["package"] == base_cfg["package"]
assert cand_cfg.get("refmap") == base_cfg.get("refmap")
assert cand_cfg["client"][:len(base_cfg["client"])] == base_cfg["client"]
new_mixins=cand_cfg["client"][len(base_cfg["client"]):]
expected=[
    "MixinTextureMapStartupAudit","MixinTextureUtilStartupAudit",
    "MixinLoadControllerStartupAudit","MixinNEIClientHandlerStartupAudit",
    "MixinMobRecipeLoaderStartupAudit","MixinMobHandlerStartupAudit"
]
assert new_mixins == expected, new_mixins

for n in cand_cls:
    assert major(cand[n]) <= 52, (n,major(cand[n]))

# No target-class replacement/shadowing.
for prefix in [
    "net/minecraft/", "cpw/mods/fml/", "codechicken/nei/",
    "com/kuba6000/mobsinfo/", "com/gtnewhorizons/angelica/",
    "org/embeddedt/embeddium/"
]:
    assert not any(n.startswith(prefix) for n in added), prefix

# Passive injection policy.
for n in added:
    if "/mixins/" not in n: continue
    b=cand[n]
    for bad in [b"cancel",b"setReturnValue",b"WrapOperation",b"Redirect",b"ModifyVariable",b"ModifyArg"]:
        assert bad not in b, (n,bad)

# Required observability and no accidental batching implementation.
blob=b"\n".join(cand[n] for n in added)
for req in [
    b"BlockAtlasEligibility BEGIN", b"BlockAtlasEligibility END",
    b"FMLLifecycle slow", b"FMLLifecycle SUMMARY",
    b"StartupSpan name=", b"NEI.ClientHandler.loadPluginsList",
    b"MobsInfo.generateMobRecipeMap", b"MobsInfo.processMobRecipeMap",
    b"MobsInfo.MobHandler.sortCachedRecipes"
]:
    assert req in blob, req
for forbidden in [b"glTexSubImage2D", b"glTexImage2D", b"ItemsAtlasBatch", b"ThreadPoolExecutor"]:
    assert forbidden not in blob, forbidden

print("THEPATCHER_BETA29_REGRESSION PASS")
print(" candidateSha256="+sha(CAND))
print(" inheritedClasses=%d added=%d changed=%d removed=%d" % (len(base_cls)-1,len(added),len(changed),len(removed)))
print(" passiveMixins="+",".join(new_mixins))
