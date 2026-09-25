from __future__ import print_function
import hashlib, pathlib, struct, subprocess, tempfile, zipfile

ROOT = pathlib.Path(r"C:\Users\User\Desktop\Codex\Akashic-ThePatcher-1.1.0-beta.34-ItemIconMainThread")
BASE = ROOT / "base-beta33.jar"
REOBF = ROOT / "build/libs/akashicasynccompat-NO-GIT-TAG-SET.jar"
OUT = ROOT / "releases/!Akashic-ThePatcher-1.1.0-beta.34-ItemIconMainThread.jar"
BASE_SHA = "6957c792cc4555c0de6eecf63d375799942269f976bfca1a2d396f116f31921d"
OLD, NEW = "1.1.0-beta.33", "1.1.0-beta.34"
MOD = "com/akashic/thepatcher/AkashicThePatcherMod.class"
COMPAT = "dev/akashic/asynciconcompat/CompatHooks.class"
JDK = pathlib.Path(r"C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot\bin")
ASM = pathlib.Path(r"C:\Users\User\.gradle\caches\modules-2\files-2.1\org.ow2.asm\asm\9.8\dc19ecb3f7889b7860697215cae99c0f9b6f6b4b\asm-9.8.jar")
TREE = pathlib.Path(r"C:\Users\User\.gradle\caches\modules-2\files-2.1\org.ow2.asm\asm-tree\9.8\18419ca5b77a2f81097c741e7872e6ab8d2f40d\asm-tree-9.8.jar")
TOOL = ROOT / "tools/PatchThePatcherVersion.java"

def sha(p): return hashlib.sha256(p.read_bytes()).hexdigest()
def readzip(p):
    with zipfile.ZipFile(p) as z:
        assert z.testzip() is None
        return {n:z.read(n) for n in z.namelist() if not n.endswith("/")}
def major(b): return struct.unpack_from(">H", b, 6)[0]

assert sha(BASE) == BASE_SHA
base, reb = readzip(BASE), readzip(REOBF)
assert COMPAT in reb and major(reb[COMPAT]) <= 52
with tempfile.TemporaryDirectory(prefix="beta34-ver-", dir=str(ROOT/"runs")) as wd:
    w = pathlib.Path(wd)
    cp = ";".join([str(ASM), str(TREE)])
    subprocess.run([str(JDK/"javac.exe"),"--release","8","-cp",cp,"-d",str(w),str(TOOL)],
                   check=True, timeout=60)
    out = w / "AkashicThePatcherMod.class"
    proc = subprocess.run([
        str(JDK/"java.exe"),"-Xverify:all","-cp",";".join([str(w),cp]),
        "com.akashic.compatlab.tools.PatchThePatcherVersion",
        str(BASE),OLD,NEW,str(out)],
        check=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,timeout=60)
    assert b"THEPATCHER_VERSION_PATCH PASS" in proc.stdout
    mod_bytes = out.read_bytes()

content = dict(base)
content[COMPAT] = reb[COMPAT]
content[MOD] = mod_bytes

manifest = content["META-INF/MANIFEST.MF"].decode("utf-8")
assert manifest.count("Implementation-Version: "+OLD) == 1
content["META-INF/MANIFEST.MF"] = manifest.replace(
    "Implementation-Version: "+OLD, "Implementation-Version: "+NEW, 1).encode("utf-8")
mcmod = content["mcmod.info"].decode("utf-8")
assert mcmod.count(OLD) == 1
content["mcmod.info"] = mcmod.replace(OLD,NEW,1).encode("utf-8")

changed_classes = sorted(n for n in base if n.endswith(".class") and base[n] != content[n])
assert changed_classes == sorted([COMPAT, MOD]), changed_classes
for n in base:
    if n.endswith(".class") and n not in (COMPAT,MOD):
        assert base[n] == content[n], n
changed_resources = sorted(
    n for n in base
    if not n.endswith(".class") and base[n] != content[n])
assert changed_resources == ["META-INF/MANIFEST.MF","mcmod.info"], changed_resources

blob = content[COMPAT]
for lit in [b"akashic.asyncItemIcons", b"false", b"Item icon registration forced to the client thread"]:
    assert lit in blob, lit

def make_bytes():
    with tempfile.TemporaryDirectory(prefix="beta34-jar-", dir=str(ROOT/"runs")) as wd:
        target = pathlib.Path(wd) / "candidate.jar"
        with zipfile.ZipFile(target,"w",zipfile.ZIP_DEFLATED,compresslevel=9) as z:
            for name in sorted(content):
                info=zipfile.ZipInfo(name,(2026,9,25,20,30,0))
                info.create_system=3
                info.compress_type=zipfile.ZIP_DEFLATED
                info.external_attr=0o644 << 16
                z.writestr(info,content[name],compress_type=zipfile.ZIP_DEFLATED,compresslevel=9)
        return target.read_bytes()

a,b = make_bytes(), make_bytes()
assert a == b
OUT.parent.mkdir(parents=True,exist_ok=True)
OUT.write_bytes(a)
print("THEPATCHER_BETA34_BUILD PASS sha256=%s bytes=%d changed=%s" %
      (sha(OUT),OUT.stat().st_size,",".join(changed_classes)))
