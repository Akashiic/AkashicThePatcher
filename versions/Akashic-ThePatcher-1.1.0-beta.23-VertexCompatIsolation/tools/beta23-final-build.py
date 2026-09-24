from __future__ import print_function
import hashlib,json,pathlib,struct,subprocess,tempfile,zipfile

ROOT=pathlib.Path(r"C:\Users\User\Desktop\Codex\Akashic-ThePatcher-1.1.0-beta.23-VertexCompatIsolation")
BASE=ROOT/"base-beta22.jar"
OUT=ROOT/"releases/!Akashic-ThePatcher-1.1.0-beta.23-VertexCompatIsolation.jar"
REPORT=ROOT/"reports/final-build-audit.json"
BASE_SHA="1843eec1e4c0a98b67fcbf74d98f55cef8a51ac33eadff95c07b3010d72f1fff"
OLD="1.1.0-beta.22"
NEW="1.1.0-beta.23"
MOD="com/akashic/thepatcher/AkashicThePatcherMod.class"
PROBE="dev/akashic/asynciconcompat/graphics/WorldRenderStateProbe.class"
MIXIN="dev/akashic/asynciconcompat/mixins/MixinCeleritasWorldStateProbe.class"
CFG="mixins.akashicasynccompat.json"
JDK=pathlib.Path(r"C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot\bin")
ASM=pathlib.Path(r"C:\Users\User\.gradle\caches\modules-2\files-2.1\org.ow2.asm\asm\9.8\dc19ecb3f7889b7860697215cae99c0f9b6f6b4b\asm-9.8.jar")
TREE=pathlib.Path(r"C:\Users\User\.gradle\caches\modules-2\files-2.1\org.ow2.asm\asm-tree\9.8\18419ca5b77a2f81097c741e7872e6ab8d2f40d\asm-tree-9.8.jar")
TOOL=ROOT/"tools/PatchThePatcherVersion.java"
RUNS=ROOT/"runs"

def sha(p): return hashlib.sha256(p.read_bytes()).hexdigest()
def major(b): return struct.unpack_from(">H",b,6)[0]
assert sha(BASE)==BASE_SHA
with zipfile.ZipFile(BASE) as z:
    base={n:z.read(n) for n in z.namelist() if not n.endswith("/")}
assert PROBE in base and MIXIN in base

def patch_version():
    with tempfile.TemporaryDirectory(prefix="beta23-ver-",dir=RUNS) as d:
        d=pathlib.Path(d); cp=";".join(map(str,[ASM,TREE]))
        subprocess.run([str(JDK/"javac.exe"),"--release","8","-encoding","UTF-8","-Xlint:all,-options","-Werror","-cp",cp,"-d",str(d),str(TOOL)],check=True,timeout=60)
        out=d/"AkashicThePatcherMod.class"; cp2=";".join(map(str,[d,ASM,TREE]))
        proc=subprocess.run([str(JDK/"java.exe"),"-Xverify:all","-cp",cp2,"com.akashic.compatlab.tools.PatchThePatcherVersion",str(BASE),OLD,NEW,str(out)],check=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,timeout=60)
        txt=proc.stdout.decode("utf-8","replace"); assert "THEPATCHER_VERSION_PATCH PASS" in txt
        return out.read_bytes(),txt.strip()
v1,p1=patch_version(); v2,p2=patch_version(); assert v1==v2

content=dict(base)
del content[PROBE]; del content[MIXIN]
cfg=json.loads(content[CFG].decode("utf-8"))
cfg["client"]=[x for x in cfg["client"] if x!="MixinCeleritasWorldStateProbe"]
content[CFG]=(json.dumps(cfg,indent=2)+"\n").encode("utf-8")
content[MOD]=v1
manifest=content["META-INF/MANIFEST.MF"].decode("utf-8")
content["META-INF/MANIFEST.MF"]=manifest.replace("Implementation-Version: "+OLD,"Implementation-Version: "+NEW,1).encode("utf-8")
mcmod=content["mcmod.info"].decode("utf-8")
content["mcmod.info"]=mcmod.replace(OLD,NEW,1).encode("utf-8")

base_classes={n for n in base if n.endswith(".class")}
result_classes={n for n in content if n.endswith(".class")}
changed=sorted(n for n in base_classes & result_classes if base[n]!=content[n])
removed=sorted(base_classes-result_classes); added=sorted(result_classes-base_classes)
assert changed==[MOD],changed
assert removed==sorted([PROBE,MIXIN]),removed
assert added==[],added

for n,b in content.items():
    if n.endswith(".class"): assert major(b)<=52,n

for n in [
"com/akashic/thepatcher/module/forestry/ForestryC2RuntimeGuard.class",
"com/akashic/thepatcher/module/forestry/ForestryGenomeCompat.class",
"com/akashic/thepatcher/module/forestry/ForestryGenomeC2Transformer.class",
"dev/akashic/asynciconcompat/cache/StitchLayoutCache.class",
"dev/akashic/asynciconcompat/upload/ItemsAtlasUploadBatcher.class",
"dev/akashic/asynciconcompat/upload/ItemsAtlasUploadBatcher$State.class",
"dev/akashic/asynciconcompat/mixins/MixinTextureMapItemsUploadBatch.class",
"dev/akashic/asynciconcompat/mixins/MixinTextureUtilItemsUploadBatch.class",
]:
    assert content[n]==base[n],n
assert b"MixinCeleritasWorldStateProbe" not in content[CFG]

def make():
    with tempfile.TemporaryDirectory(prefix="beta23-jar-",dir=RUNS) as d:
        p=pathlib.Path(d)/"candidate.jar"
        with zipfile.ZipFile(p,"w",zipfile.ZIP_DEFLATED,compresslevel=9) as z:
            for n in sorted(content):
                i=zipfile.ZipInfo(n,(2026,9,24,19,15,0)); i.create_system=3; i.compress_type=zipfile.ZIP_DEFLATED; i.external_attr=0o644<<16
                z.writestr(i,content[n],compresslevel=9)
        with zipfile.ZipFile(p) as z: assert z.testzip() is None
        return p.read_bytes()
j1=make(); j2=make(); assert j1==j2
OUT.parent.mkdir(parents=True,exist_ok=True); OUT.write_bytes(j1)
report={"schema":1,"version":NEW,"sha256":sha(OUT),"bytes":OUT.stat().st_size,"baseBeta22Sha256":BASE_SHA,"changedClasses":changed,"removedClasses":removed,"addedClasses":added,"scope":"remove WorldGLState probe for clean vertex-format A/B; no renderer behavior patch","productionInstalled":False}
REPORT.parent.mkdir(parents=True,exist_ok=True); REPORT.write_text(json.dumps(report,indent=2,sort_keys=True)+"\n",encoding="utf-8")
print("THEPATCHER_BETA23_BUILD PASS sha256=%s bytes=%d"%(report["sha256"],report["bytes"]))