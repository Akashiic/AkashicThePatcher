from __future__ import print_function
import hashlib, json, pathlib, struct, subprocess, tempfile, zipfile

ROOT=pathlib.Path(r"C:\Users\User\Desktop\Codex\Akashic-ThePatcher-1.1.0-beta.24-TerrainStagingFallback")
BASE=ROOT/"base-beta23.jar"
COMP=ROOT/"terrain-staging-alpha2.13.jar"
OUT=ROOT/"releases/!Akashic-ThePatcher-1.1.0-beta.24-TerrainStagingFallback.jar"
REPORT=ROOT/"reports/final-build-audit.json"
BASE_SHA="5a3c563b61f26a24d84b3b4fe89e626afa82e87f35e1ccd51bd9e39acd6097c3"
COMP_SHA="7d56918ea263416d2a84a8c10d620902037af07f13af9133e755e492fbf91c30"
OLD="1.1.0-beta.23"
NEW="1.1.0-beta.24"
MOD="com/akashic/thepatcher/AkashicThePatcherMod.class"
NEW_MIXIN="dev/akashic/asynciconcompat/mixins/MixinRenderRegionManagerStagingFallback.class"
MIXIN_CFG="mixins.akashicasynccompat.json"
JDK=pathlib.Path(r"C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot\bin")
ASM=pathlib.Path(r"C:\Users\User\.gradle\caches\modules-2\files-2.1\org.ow2.asm\asm\9.8\dc19ecb3f7889b7860697215cae99c0f9b6f6b4b\asm-9.8.jar")
TREE=pathlib.Path(r"C:\Users\User\.gradle\caches\modules-2\files-2.1\org.ow2.asm\asm-tree\9.8\18419ca5b77a2f81097c741e7872e6ab8d2f40d\asm-tree-9.8.jar")
TOOL=ROOT/"tools/PatchThePatcherVersion.java"
RUNS=ROOT/"runs"

def sha(p): return hashlib.sha256(p.read_bytes()).hexdigest()
def major(b): return struct.unpack_from(">H",b,6)[0]

assert sha(BASE)==BASE_SHA
assert sha(COMP)==COMP_SHA

with zipfile.ZipFile(BASE) as z:
    base={n:z.read(n) for n in z.namelist() if not n.endswith("/")}
with zipfile.ZipFile(COMP) as z:
    assert NEW_MIXIN in z.namelist()
    mixin_bytes=z.read(NEW_MIXIN)
    cfg=z.read(MIXIN_CFG)
    refmap=z.read("mixins.akashicasynccompat.refmap.json")

assert refmap==base["mixins.akashicasynccompat.refmap.json"], "refmap unexpectedly changed"
assert b"MixinRenderRegionManagerStagingFallback" in cfg
assert b"MixinCeleritasWorldStateProbe" not in cfg

def patch_version():
    with tempfile.TemporaryDirectory(prefix="beta24-version-",dir=RUNS) as d:
        d=pathlib.Path(d)
        cp=";".join(str(x) for x in (ASM,TREE))
        subprocess.run([str(JDK/"javac.exe"),"--release","8","-encoding","UTF-8","-Xlint:all,-options","-Werror","-cp",cp,"-d",str(d),str(TOOL)],check=True,timeout=60)
        out=d/"AkashicThePatcherMod.class"
        cp2=";".join([str(d),str(ASM),str(TREE)])
        proc=subprocess.run([str(JDK/"java.exe"),"-Xverify:all","-cp",cp2,"com.akashic.compatlab.tools.PatchThePatcherVersion",str(BASE),OLD,NEW,str(out)],check=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,timeout=60)
        txt=proc.stdout.decode("utf-8","replace")
        assert "THEPATCHER_VERSION_PATCH PASS" in txt
        return out.read_bytes(),txt.strip()

v1,p1=patch_version(); v2,p2=patch_version(); assert v1==v2

content=dict(base)
content[NEW_MIXIN]=mixin_bytes
content[MIXIN_CFG]=cfg
content[MOD]=v1

manifest=content["META-INF/MANIFEST.MF"].decode("utf-8")
assert manifest.count("Implementation-Version: "+OLD)==1
content["META-INF/MANIFEST.MF"]=manifest.replace("Implementation-Version: "+OLD,"Implementation-Version: "+NEW,1).encode("utf-8")
mcmod=content["mcmod.info"].decode("utf-8")
assert mcmod.count(OLD)==1
content["mcmod.info"]=mcmod.replace(OLD,NEW,1).encode("utf-8")

base_classes={n for n in base if n.endswith(".class")}
result_classes={n for n in content if n.endswith(".class")}
changed=sorted(n for n in base_classes if base[n]!=content[n])
added=sorted(result_classes-base_classes)
removed=sorted(base_classes-result_classes)
assert changed==[MOD],changed
assert added==[NEW_MIXIN],added
assert removed==[],removed

changed_res=sorted(n for n in base if not n.endswith(".class") and base[n]!=content[n])
assert changed_res==["META-INF/MANIFEST.MF","mcmod.info",MIXIN_CFG],changed_res

for n,b in content.items():
    if n.endswith(".class"): assert major(b)<=52,n

# Accepted behavior remains bit-identical to beta23.
preserved=[
 "com/akashic/thepatcher/module/forestry/ForestryC2RuntimeGuard.class",
 "com/akashic/thepatcher/module/forestry/ForestryGenomeCompat.class",
 "com/akashic/thepatcher/module/forestry/ForestryGenomeC2Transformer.class",
 "dev/akashic/asynciconcompat/CompatHooks.class",
 "dev/akashic/asynciconcompat/CompatState.class",
 "dev/akashic/asynciconcompat/cache/StitchLayoutCache.class",
 "dev/akashic/asynciconcompat/mixins/MixinTextureMapHybridAsync.class",
 "dev/akashic/asynciconcompat/mixins/MixinStitcherLayoutCache.class",
 "dev/akashic/asynciconcompat/mixins/MixinTextureUtilUploadProfiler.class",
 "dev/akashic/asynciconcompat/mixins/MixinAngelicaMipmapProfiler.class",
 "dev/akashic/asynciconcompat/upload/ItemsAtlasUploadBatcher.class",
 "dev/akashic/asynciconcompat/upload/ItemsAtlasUploadBatcher$State.class",
 "dev/akashic/asynciconcompat/mixins/MixinTextureMapItemsUploadBatch.class",
 "dev/akashic/asynciconcompat/mixins/MixinTextureUtilItemsUploadBatch.class",
]
for n in preserved: assert content[n]==base[n],n

assert "dev/akashic/asynciconcompat/graphics/WorldRenderStateProbe.class" not in content
assert "dev/akashic/asynciconcompat/mixins/MixinCeleritasWorldStateProbe.class" not in content

for lit in [b"TerrainStaging",b"mappedSupported",b"forceFallback",b"FallbackStagingBuffer",b"akashic.forceTerrainFallbackStaging"]:
    assert lit in mixin_bytes,lit

# No third-party classes may be bundled by the merge.
for prefix in ["org/embeddedt/embeddium/","com/gtnewhorizons/angelica/","com/gtnewhorizon/gtnhlib/"]:
    assert not any(n.startswith(prefix) for n in content),prefix

def make():
    with tempfile.TemporaryDirectory(prefix="beta24-jar-",dir=RUNS) as d:
        p=pathlib.Path(d)/"candidate.jar"
        with zipfile.ZipFile(p,"w",zipfile.ZIP_DEFLATED,compresslevel=9) as z:
            for n in sorted(content):
                info=zipfile.ZipInfo(n,(2026,9,24,20,20,0))
                info.create_system=3; info.compress_type=zipfile.ZIP_DEFLATED; info.external_attr=0o644<<16
                z.writestr(info,content[n],compresslevel=9)
        with zipfile.ZipFile(p) as z: assert z.testzip() is None
        return p.read_bytes()
j1=make(); j2=make(); assert j1==j2
OUT.parent.mkdir(parents=True,exist_ok=True); OUT.write_bytes(j1)
report={
 "schema":1,
 "version":NEW,
 "artifact":str(OUT.relative_to(ROOT)),
 "sha256":sha(OUT),
 "bytes":OUT.stat().st_size,
 "deterministicRebuild":True,
 "baseBeta23Sha256":BASE_SHA,
 "componentAlpha213Sha256":COMP_SHA,
 "changedClasses":changed,
 "addedClasses":added,
 "removedClasses":removed,
 "changedResources":changed_res,
 "scope":"force Celeritas terrain chunk staging from persistent MappedStagingBuffer to official FallbackStagingBuffer only when mapped staging is supported",
 "defaultEnabled":True,
 "disableProperty":"-Dakashic.forceTerrainFallbackStaging=false",
 "worldGlStateProbeRemoved":True,
 "productionInstalled":False,
 "productionApproved":False
}
REPORT.parent.mkdir(parents=True,exist_ok=True)
REPORT.write_text(json.dumps(report,indent=2,sort_keys=True)+"\n",encoding="utf-8")
print("THEPATCHER_BETA24_BUILD PASS sha256=%s bytes=%d"%(report["sha256"],report["bytes"]))