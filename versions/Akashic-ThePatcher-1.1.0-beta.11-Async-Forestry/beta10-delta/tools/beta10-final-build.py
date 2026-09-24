from __future__ import print_function
import hashlib, json, pathlib, struct, subprocess, tempfile, zipfile

ROOT=pathlib.Path(r'C:\Users\User\Desktop\Codex\Akashic-Compatibility-Lab')
JDK=pathlib.Path(r'C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot\bin')
BASE=ROOT/'releases/!Akashic-ThePatcher-1.1.0-beta.8.jar'
BASE_SHA='010c3b9f4fdd20528a772904f13238aa836c8af13759aebfe7184ed07beed44b'
SRC=ROOT/'sources/thepatcher-beta10-final/src/main/java'
OUT=ROOT/'releases/!Akashic-ThePatcher-1.1.0-beta.10-Cinematic-Angelica.jar'
REPORT=ROOT/'reports/thepatcher-beta10/final-build-audit.json'
RUNS=ROOT/'runs'

LAUNCH=pathlib.Path(r'C:\Users\User\AppData\Roaming\.technic\libraries\net\minecraft\launchwrapper\1.12\launchwrapper-1.12.jar')
ASM=pathlib.Path(r'C:\Users\User\.gradle\caches\modules-2\files-2.1\org.ow2.asm\asm\9.8\dc19ecb3f7889b7860697215cae99c0f9b6f6b4b\asm-9.8.jar')
TREE=pathlib.Path(r'C:\Users\User\.gradle\caches\modules-2\files-2.1\org.ow2.asm\asm-tree\9.8\18419ca5b77a2f81097c741e7872e6ab8d2f40d\asm-tree-9.8.jar')
CINEMATIC=pathlib.Path(r'C:\Users\User\AppData\Roaming\.technic\modpacks\forbidden-series\mods\AkashicCinematicMenu-0.7.0-alpha.4.jar')
ANGELICA=pathlib.Path(r'C:\Users\User\AppData\Roaming\.technic\modpacks\forbidden-series\mods\angelica-2.2.18.jar')
LOADER_TOOL=ROOT/'tools/java/PatchThePatcherBeta10LoadingPlugin.java'
VERSION_TOOL=ROOT/'tools/java/PatchThePatcherVersion.java'
PINS={
    LAUNCH:'57f402b626d16cc2705bf2a37add7adbb074f0ca3b3102fa6e23aa303dae682f',
    ASM:'876eab6a83daecad5ca67eb9fcabb063c97b5aeb8cf1fca7a989ecde17522051',
    TREE:'14b7880cb7c85eed101e2710432fc3ffb83275532a6a894dc4c4095d49ad59f1',
    CINEMATIC:'7dbf5177c4e26edea3c0d5caceafac6d4ec013eee6d543d2dbe80066d4c0fb4a',
    ANGELICA:'563d907f3035aedc7761be0f882674d6e736b63c5860e126e7af7df0d9f15c50',
}

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

if sha(BASE)!=BASE_SHA:
    raise ValueError('beta.8 base identity changed')
for p,e in PINS.items():
    if not p.is_file():
        raise FileNotFoundError(p)
    a=sha(p)
    if a!=e:
        raise ValueError('pinned dependency changed: %s expected=%s got=%s'%(p,e,a))

with zipfile.ZipFile(BASE) as z:
    base={n:z.read(n) for n in z.namelist() if not n.endswith('/')}
if len(base)!=len(set(base)):
    raise ValueError('duplicate base entries')

javafiles=sorted(SRC.rglob('*.java'))
expected_sources=[
    'AngelicaVSyncGuard.java',
    'CinematicAngelicaCompatTransformer.java',
    'CinematicAngelicaStateFence.java',
]
if [p.name for p in javafiles]!=expected_sources:
    raise ValueError('unexpected beta.10 sources: '+repr([p.name for p in javafiles]))
def compile_overlay():
    with tempfile.TemporaryDirectory(prefix='patcher-beta10-',dir=RUNS) as d:
        d=pathlib.Path(d)
        cp=';'.join(str(p) for p in [BASE,LAUNCH,ASM,TREE])
        cmd=[str(JDK/'javac.exe'),'--release','8','-encoding','UTF-8',
             '-Xlint:all,-options','-Werror','-cp',cp,'-d',str(d)]
        cmd += [str(p) for p in javafiles]
        subprocess.run(cmd,check=True,timeout=60)
        result={p.relative_to(d).as_posix():p.read_bytes()
                for p in d.rglob('*.class')}
        expected={
            'com/akashic/thepatcher/module/cinematic/AngelicaVSyncGuard.class',
            'com/akashic/thepatcher/module/cinematic/CinematicAngelicaCompatTransformer.class',
            'com/akashic/thepatcher/module/cinematic/CinematicAngelicaStateFence.class',
        }
        if set(result)!=expected:
            raise ValueError('unexpected overlay classes: '+repr(sorted(result)))
        for name,data in result.items():
            if struct.unpack_from('>H',data,6)[0]>52:
                raise ValueError('Java 8 target broken: '+name)
        return result

def compile_tools(folder):
    cp=';'.join(str(p) for p in [ASM,TREE])
    subprocess.run([
        str(JDK/'javac.exe'),'--release','8','-encoding','UTF-8',
        '-Xlint:all,-options','-Werror','-cp',cp,'-d',str(folder),
        str(LOADER_TOOL),str(VERSION_TOOL)
    ],check=True,timeout=60)

def patch_loader(original):
    with tempfile.TemporaryDirectory(prefix='patcher-beta10-loader-',dir=RUNS) as d:
        d=pathlib.Path(d); tools=d/'tools'; tools.mkdir()
        compile_tools(tools)
        src=d/'loader.class'; dst=d/'patched'/'loader.class'
        src.write_bytes(original)
        cp=';'.join(str(p) for p in [tools,ASM,TREE])
        proc=subprocess.run([
            str(JDK/'java.exe'),'-Xverify:all','-cp',cp,
            'com.akashic.compatlab.tools.PatchThePatcherBeta10LoadingPlugin',
            str(src),str(dst)
        ],check=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,timeout=60)
        text=proc.stdout.decode('utf-8',errors='replace')
        if 'PATCH_THEPATCHER_BETA10_LOADING_PLUGIN PASS' not in text:
            raise ValueError('loader proof missing: '+text[-2000:])
        return dst.read_bytes(),text.strip()

def patch_version():
    with tempfile.TemporaryDirectory(prefix='patcher-beta10-version-',dir=RUNS) as d:
        d=pathlib.Path(d); tools=d/'tools'; tools.mkdir()
        compile_tools(tools)
        dst=d/'AkashicThePatcherMod.class'
        cp=';'.join(str(p) for p in [tools,ASM,TREE])
        proc=subprocess.run([
            str(JDK/'java.exe'),'-Xverify:all','-cp',cp,
            'com.akashic.compatlab.tools.PatchThePatcherVersion',
            str(BASE),'1.1.0-beta.8','1.1.0-beta.10',str(dst)
        ],check=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,timeout=60)
        text=proc.stdout.decode('utf-8',errors='replace')
        if 'THEPATCHER_VERSION_PATCH PASS' not in text:
            raise ValueError('version proof missing: '+text[-2000:])
        data=dst.read_bytes()
        if struct.unpack_from('>H',data,6)[0]>52:
            raise ValueError('version class target broken')
        return data,text.strip()

first=compile_overlay()
second=compile_overlay()
if {k:hashlib.sha256(v).hexdigest() for k,v in first.items()} != {k:hashlib.sha256(v).hexdigest() for k,v in second.items()}:
    raise ValueError('non-deterministic overlay compile')
loader_name='com/akashic/thepatcher/core/AkashicThePatcherLoadingPlugin.class'
version_name='com/akashic/thepatcher/AkashicThePatcherMod.class'

loader1,loader_proof1=patch_loader(base[loader_name])
loader2,loader_proof2=patch_loader(base[loader_name])
if loader1!=loader2:
    raise ValueError('non-deterministic loader patch')

version1,version_proof1=patch_version()
version2,version_proof2=patch_version()
if version1!=version2:
    raise ValueError('non-deterministic version patch')

content=dict(base)
content.update(first)
content[loader_name]=loader1
content[version_name]=version1

old=b'1.1.0-beta.8'
new=b'1.1.0-beta.10'
for name in ['META-INF/MANIFEST.MF','mcmod.info']:
    if content[name].count(old)!=1:
        raise ValueError('metadata version shape changed: '+name)
    content[name]=content[name].replace(old,new,1)

base_classes={n for n in base if n.endswith('.class')}
new_classes={n for n in content if n.endswith('.class')}
changed=sorted(n for n in base_classes if base[n]!=content[n])
added=sorted(new_classes-base_classes)
expected_changed=sorted([version_name,loader_name])
if changed!=expected_changed:
    raise ValueError('unexpected changed classes: '+repr(changed))
if added!=sorted(first):
    raise ValueError('unexpected added classes: '+repr(added))
def archive_bytes():
    with tempfile.TemporaryDirectory(prefix='patcher-beta10-jar-',dir=RUNS) as d:
        p=pathlib.Path(d)/'candidate.jar'
        with zipfile.ZipFile(p,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
            for name in sorted(content):
                info=zipfile.ZipInfo(name,(2026,9,23,0,0,0))
                info.create_system=3
                info.compress_type=zipfile.ZIP_DEFLATED
                info.external_attr=0o644<<16
                z.writestr(info,content[name],compresslevel=9)
        with zipfile.ZipFile(p) as z:
            if z.testzip() is not None:
                raise ValueError('archive CRC failed')
        return p.read_bytes()

jar1=archive_bytes()
jar2=archive_bytes()
if jar1!=jar2:
    raise ValueError('non-deterministic beta.10 archive')
if OUT.exists() and OUT.read_bytes()!=jar1:
    raise ValueError('existing beta.10 differs; refusing overwrite')
OUT.parent.mkdir(parents=True,exist_ok=True)
if not OUT.exists():
    OUT.write_bytes(jar1)

report={
    'schema':1,
    'version':'1.1.0-beta.10',
    'artifact':str(OUT.relative_to(ROOT)),
    'sha256':sha(OUT),
    'bytes':OUT.stat().st_size,
    'deterministicRebuild':True,
    'baseBeta8Sha256':BASE_SHA,
    'baseClasses':len(base_classes),
    'resultClasses':len(new_classes),
    'unchangedBaseClasses':len(base_classes)-len(changed),
    'changedClasses':changed,
    'addedClasses':added,
    'sourceSha256':{str(p.relative_to(ROOT)):sha(p) for p in javafiles},
    'dependencies':{str(p):sha(p) for p in PINS},
    'loaderPatchProof':loader_proof1,
    'loaderPatchDeterministic':loader_proof1==loader_proof2,
    'versionPatchProof':version_proof1,
    'versionPatchDeterministic':version_proof1==version_proof2,
    'rawScopeTargetSha256':'43922d54b50dfc6c2ce9ac8bb052e07172a46369123143dc8c1371790a988d53',
    'angelicaVSyncBaseSha256':'6819de7d6ded5753c0d7645304a37ecd4091353b8896a5fdb2806fa9f79993b1',
    'angelicaVSyncJava17Sha256':'01d8ae492ae60102a34894744eb98d22c09eacf709e1faeb5715ef7c474ecc7c',
    'killSwitches':[
        '-Dakashic.cinematicAngelicaStateFence.disable=true',
        '-Dakashic.angelicaVSyncGuard.disable=true'
    ],
    'scope':'beta.8 plus exact Akashic Cinematic RawScope/Angelica GLSM state fence and Angelica 2.2.18 LWJGL2 VSync fail-soft guard',
    'productionInstalled':False,
    'productionApproved':False,
}
REPORT.parent.mkdir(parents=True,exist_ok=True)
REPORT.write_text(json.dumps(report,indent=2,sort_keys=True)+'\n',encoding='utf-8')
print('THEPATCHER_BETA10_BUILD PASS sha256='+report['sha256']
      +' bytes='+str(report['bytes'])
      +' changed='+str(len(changed))
      +' added='+str(len(added)))
