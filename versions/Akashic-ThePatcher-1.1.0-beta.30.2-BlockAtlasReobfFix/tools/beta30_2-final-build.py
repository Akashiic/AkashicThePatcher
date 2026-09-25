from __future__ import print_function
import hashlib, json, pathlib, struct, subprocess, tempfile, zipfile

ROOT = pathlib.Path(r"C:\Users\User\Desktop\Codex\Akashic-ThePatcher-1.1.0-beta.30.2-BlockAtlasReobfFix")
BASE = ROOT / "base-beta28.jar"
REOBF = ROOT / "build/libs/akashicasynccompat-NO-GIT-TAG-SET.jar"
OUT = ROOT / "releases/!Akashic-ThePatcher-1.1.0-beta.30.2-BlockAtlasReobfFix.jar"
REPORT = ROOT / "reports/final-build-audit.json"
RUNS = ROOT / "runs"

BASE_SHA = "c84d21a316879b8afe9a50e928caef7788acf769c3d75926cdff1a21fd038443"
OLD = "1.1.0-beta.28"
NEW = "1.1.0-beta.30.2"
MOD = "com/akashic/thepatcher/AkashicThePatcherMod.class"
MAP_MIXIN = "dev/akashic/asynciconcompat/mixins/MixinTextureMapItemsUploadBatch.class"
UTIL_MIXIN = "dev/akashic/asynciconcompat/mixins/MixinTextureUtilItemsUploadBatch.class"
BLOCK_PREFIX = "dev/akashic/asynciconcompat/upload/BlockAtlasUploadBatcher"
MIXIN_CFG = "mixins.akashicasynccompat.json"
REFMAP = "mixins.akashicasynccompat.refmap.json"

JDK = pathlib.Path(r"C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot\bin")
ASM = pathlib.Path(r"C:\Users\User\.gradle\caches\modules-2\files-2.1\org.ow2.asm\asm\9.8\dc19ecb3f7889b7860697215cae99c0f9b6f6b4b\asm-9.8.jar")
TREE = pathlib.Path(r"C:\Users\User\.gradle\caches\modules-2\files-2.1\org.ow2.asm\asm-tree\9.8\18419ca5b77a2f81097c741e7872e6ab8d2f40d\asm-tree-9.8.jar")
TOOL = ROOT / "tools/PatchThePatcherVersion.java"

def sha(p): return hashlib.sha256(p.read_bytes()).hexdigest()
def major(b): return struct.unpack_from(">H", b, 6)[0]

def readzip(p):
    with zipfile.ZipFile(p) as z:
        assert z.testzip() is None
        return {n:z.read(n) for n in z.namelist() if not n.endswith("/")}

def make_zip(content, target, stamp=(2026,9,25,16,25,0)):
    with zipfile.ZipFile(target,"w",zipfile.ZIP_DEFLATED,compresslevel=9) as z:
        for name in sorted(content):
            info=zipfile.ZipInfo(name,stamp)
            info.create_system=3
            info.compress_type=zipfile.ZIP_DEFLATED
            info.external_attr=0o644<<16
            z.writestr(info,content[name],compress_type=zipfile.ZIP_DEFLATED,compresslevel=9)
    with zipfile.ZipFile(target) as z:
        assert z.testzip() is None

assert BASE.is_file() and sha(BASE)==BASE_SHA
assert REOBF.is_file()

base=readzip(BASE)
reb=readzip(REOBF)

wanted={}
for n,b in reb.items():
    if n in (MAP_MIXIN,UTIL_MIXIN) or n.startswith(BLOCK_PREFIX):
        wanted[n]=b

assert MAP_MIXIN in wanted and UTIL_MIXIN in wanted
added=sorted(n for n in wanted if n.startswith(BLOCK_PREFIX))
assert len(added)==5, added
for n,b in wanted.items():
    assert major(b)<=52, (n,major(b))

# Runtime namespace gates.
block_blob=b"\n".join(wanted[n] for n in added)
for req in [b"func_71410_x",b"field_71441_e",b"field_71474_y",b"field_74337_g",
            b"BlocksAtlasBatch",b"exact-regions",b"glTexSubImage2D"]:
    assert req in block_blob, req
for bad in [b"getMinecraft",b"theWorld",b"gameSettings"]:
    assert bad not in block_blob, bad

map_blob=wanted[MAP_MIXIN]
assert b"field_94254_c" in map_blob
assert b"basePath" not in map_blob
assert b"ItemsAtlasUploadBatcher" in map_blob
assert b"BlockAtlasUploadBatcher" in map_blob
assert b"ItemsAtlasUploadBatcher" in wanted[UTIL_MIXIN]
assert b"BlockAtlasUploadBatcher" in wanted[UTIL_MIXIN]

def patch_version():
    with tempfile.TemporaryDirectory(prefix="beta302-version-",dir=str(RUNS)) as w:
        w=pathlib.Path(w)
        cp=";".join(map(str,(ASM,TREE)))
        subprocess.run([str(JDK/"javac.exe"),"--release","8","-encoding","UTF-8",
                        "-Xlint:all,-options","-Werror","-cp",cp,"-d",str(w),str(TOOL)],
                       check=True,timeout=60)
        out=w/"AkashicThePatcherMod.class"
        cp2=";".join([str(w),str(ASM),str(TREE)])
        p=subprocess.run([str(JDK/"java.exe"),"-Xverify:all","-cp",cp2,
                          "com.akashic.compatlab.tools.PatchThePatcherVersion",
                          str(BASE),OLD,NEW,str(out)],
                         check=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,timeout=60)
        s=p.stdout.decode("utf-8","replace")
        assert "THEPATCHER_VERSION_PATCH PASS" in s
        return out.read_bytes(),s.strip()

v1,p1=patch_version()
v2,p2=patch_version()
assert v1==v2 and p1==p2

content=dict(base)
content.update(wanted)
content[MOD]=v1

manifest=content["META-INF/MANIFEST.MF"].decode("utf-8")
assert manifest.count("Implementation-Version: "+OLD)==1
content["META-INF/MANIFEST.MF"]=manifest.replace(
    "Implementation-Version: "+OLD,"Implementation-Version: "+NEW,1).encode("utf-8")
mcmod=content["mcmod.info"].decode("utf-8")
assert mcmod.count(OLD)==1
content["mcmod.info"]=mcmod.replace(OLD,NEW,1).encode("utf-8")

base_cls={n for n in base if n.endswith(".class")}
res_cls={n for n in content if n.endswith(".class")}
changed=sorted(n for n in base_cls if base[n]!=content[n])
new=sorted(res_cls-base_cls)
removed=sorted(base_cls-res_cls)
assert changed==sorted([MOD,MAP_MIXIN,UTIL_MIXIN]), changed
assert new==added, new
assert removed==[], removed

changed_res=sorted(n for n in base if not n.endswith(".class") and base[n]!=content[n])
assert changed_res==["META-INF/MANIFEST.MF","mcmod.info"], changed_res
assert content[MIXIN_CFG]==base[MIXIN_CFG]
assert content[REFMAP]==base[REFMAP]

for n in base_cls:
    if n not in (MOD,MAP_MIXIN,UTIL_MIXIN):
        assert content[n]==base[n], "unexpected inherited change: "+n

for n in [
 "dev/akashic/asynciconcompat/upload/ItemsAtlasUploadBatcher.class",
 "dev/akashic/asynciconcompat/upload/ItemsAtlasUploadBatcher$State.class",
 "dev/akashic/asynciconcompat/graphics/NuminaFovSafety.class",
 "dev/akashic/asynciconcompat/mixins/MixinForgeHooksClientFovFiniteGuard.class",
]:
    assert content[n]==base[n], n

def candidate():
    with tempfile.TemporaryDirectory(prefix="beta302-jar-",dir=str(RUNS)) as w:
        p=pathlib.Path(w)/"candidate.jar"
        make_zip(content,p)
        return p.read_bytes()

j1=candidate(); j2=candidate()
assert j1==j2
OUT.parent.mkdir(parents=True,exist_ok=True)
OUT.write_bytes(j1)

report={
 "schema":1,
 "version":NEW,
 "artifact":str(OUT.relative_to(ROOT)),
 "sha256":sha(OUT),
 "bytes":OUT.stat().st_size,
 "deterministicRebuild":True,
 "baseBeta28Sha256":BASE_SHA,
 "classSource":"RFG reobfJar",
 "changedClasses":changed,
 "addedClasses":new,
 "removedClasses":removed,
 "changedResources":changed_res,
 "runtimeNamespaceGate":{
   "required":["func_71410_x","field_71441_e","field_71474_y","field_74337_g","field_94254_c"],
   "forbidden":["getMinecraft","theWorld","gameSettings","basePath"]
 },
 "mixinConfigInheritedBitIdentical":True,
 "refmapInheritedBitIdentical":True,
 "itemsBatcherInheritedBitIdentical":True,
 "numinaFovGuardInheritedBitIdentical":True,
 "tests":{"tests":6,"failures":0,"errors":0,"skipped":0},
 "productionInstalled":False,
 "productionApproved":False,
 "versionPatchProof":p1
}
REPORT.parent.mkdir(parents=True,exist_ok=True)
REPORT.write_text(json.dumps(report,indent=2,sort_keys=True)+"\n",encoding="utf-8")
print("THEPATCHER_BETA30_2_BUILD PASS sha256=%s bytes=%d"%(report["sha256"],report["bytes"]))
