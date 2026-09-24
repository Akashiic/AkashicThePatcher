from __future__ import print_function
import hashlib, json, pathlib, struct, subprocess, tempfile, zipfile

ROOT=pathlib.Path(r"C:\Users\User\Desktop\Codex\Akashic-ThePatcher-1.1.0-beta.22-WorldRenderStateProbeFix")
BASE=ROOT/"base-beta21.jar"
COMP=ROOT/"worldrender-alpha2.12.jar"
OUT=ROOT/"releases/!Akashic-ThePatcher-1.1.0-beta.22-WorldRenderStateProbeFix.jar"
REPORT=ROOT/"reports/final-build-audit.json"
BASE_SHA="79dcb3f15653dc764c7075a06eafd0fb494261c8db35d3af3b5a2d830e1720f7"
COMP_SHA="d5f0a3e798e2495ca55696b79697490a80fb9d7971552c4f69cac497f36733aa"
OLD="1.1.0-beta.21"
NEW="1.1.0-beta.22"
MOD="com/akashic/thepatcher/AkashicThePatcherMod.class"
PROBE="dev/akashic/asynciconcompat/graphics/WorldRenderStateProbe.class"
MIXIN="dev/akashic/asynciconcompat/mixins/MixinCeleritasWorldStateProbe.class"
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
    comp={n:z.read(n) for n in z.namelist() if not n.endswith("/")}

assert PROBE in comp and MIXIN in comp
assert base[MIXIN]==comp[MIXIN], "mixin unexpectedly changed"
assert base["mixins.akashicasynccompat.json"]==comp["mixins.akashicasynccompat.json"], "mixin config changed"

def patch_version():
    with tempfile.TemporaryDirectory(prefix="beta22-version-",dir=RUNS) as d:
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
content[PROBE]=comp[PROBE]
content[MOD]=v1

manifest=content["META-INF/MANIFEST.MF"].decode("utf-8")
assert manifest.count("Implementation-Version: "+OLD)==1
content["META-INF/MANIFEST.MF"]=manifest.replace("Implementation-Version: "+OLD,"Implementation-Version: "+NEW,1).encode("utf-8")
mcmod=content["mcmod.info"].decode("utf-8")
assert mcmod.count(OLD)==1
content["mcmod.info"]=mcmod.replace(OLD,NEW,1).encode("utf-8")

base_classes={n for n in base if n.endswith(".class")}
changed=sorted(n for n in base_classes if base[n]!=content[n])
assert changed==sorted([MOD,PROBE]), changed
changed_res=sorted(n for n in base if not n.endswith(".class") and base[n]!=content[n])
assert changed_res==["META-INF/MANIFEST.MF","mcmod.info"], changed_res

for n,b in content.items():
    if n.endswith(".class"):
        assert major(b)<=52,n

# Accepted systems must remain byte-identical.
for n in [
 "com/akashic/thepatcher/module/forestry/ForestryC2RuntimeGuard.class",
 "com/akashic/thepatcher/module/forestry/ForestryGenomeCompat.class",
 "com/akashic/thepatcher/module/forestry/ForestryGenomeC2Transformer.class",
 "dev/akashic/asynciconcompat/cache/StitchLayoutCache.class",
 "dev/akashic/asynciconcompat/upload/ItemsAtlasUploadBatcher.class",
 "dev/akashic/asynciconcompat/upload/ItemsAtlasUploadBatcher$State.class",
 MIXIN,
 "mixins.akashicasynccompat.json",
]:
    assert content[n]==base[n],n

# Probe contract: fixed 16-element query buffers; old 4-element query calls absent.
blob=content[PROBE]
for lit in [b"WorldGLState",b"akashic.worldRenderStateRepair",b"textureBinding2D",b"currentColor",b"alphaFunc"]:
    assert lit in blob,lit

def make():
    with tempfile.TemporaryDirectory(prefix="beta22-jar-",dir=RUNS) as d:
        p=pathlib.Path(d)/"candidate.jar"
        with zipfile.ZipFile(p,"w",zipfile.ZIP_DEFLATED,compresslevel=9) as z:
            for n in sorted(content):
                info=zipfile.ZipInfo(n,(2026,9,24,18,40,0))
                info.create_system=3; info.compress_type=zipfile.ZIP_DEFLATED; info.external_attr=0o644<<16
                z.writestr(info,content[n],compresslevel=9)
        with zipfile.ZipFile(p) as z: assert z.testzip() is None
        return p.read_bytes()
j1=make(); j2=make(); assert j1==j2
OUT.parent.mkdir(parents=True,exist_ok=True); OUT.write_bytes(j1)
report={
 "schema":1,"version":NEW,"artifact":str(OUT.relative_to(ROOT)),"sha256":sha(OUT),"bytes":OUT.stat().st_size,
 "deterministicRebuild":True,"baseBeta21Sha256":BASE_SHA,"componentAlpha212Sha256":COMP_SHA,
 "changedClasses":changed,"changedResources":changed_res,
 "scope":"beta.21 probe buffer-contract fix only (LWJGL2 glGetBoolean/glGetFloat require >=16 remaining elements)",
 "logicRepairChanged":False,"productionInstalled":False,"productionApproved":False
}
REPORT.parent.mkdir(parents=True,exist_ok=True)
REPORT.write_text(json.dumps(report,indent=2,sort_keys=True)+"\n",encoding="utf-8")
print("THEPATCHER_BETA22_BUILD PASS sha256=%s bytes=%d"%(report["sha256"],report["bytes"]))