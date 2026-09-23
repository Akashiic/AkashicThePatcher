from __future__ import print_function
import hashlib, json, pathlib, shutil, struct, subprocess, tempfile, zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
JDK = pathlib.Path(r'C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot\bin')
BASE = ROOT / 'releases/!Akashic-ThePatcher-1.1.0-beta.8.jar'
BASE_SHA = '010c3b9f4fdd20528a772904f13238aa836c8af13759aebfe7184ed07beed44b'
SRC = ROOT / 'sources/thepatcher-beta9/src/main/java'
OUTPUT = ROOT / 'releases/!Akashic-ThePatcher-1.1.0-beta.9.jar'
REPORT = ROOT / 'reports/thepatcher-beta9/build-audit.json'
RUNS = ROOT / 'runs'
RUNS.mkdir(parents=True, exist_ok=True)

LAUNCH = pathlib.Path(r'C:\Users\User\AppData\Roaming\.technic\libraries\net\minecraft\launchwrapper\1.12\launchwrapper-1.12.jar')
ASM = pathlib.Path(r'C:\Users\User\.gradle\caches\modules-2\files-2.1\org.ow2.asm\asm\9.8\dc19ecb3f7889b7860697215cae99c0f9b6f6b4b\asm-9.8.jar')
TREE = pathlib.Path(r'C:\Users\User\.gradle\caches\modules-2\files-2.1\org.ow2.asm\asm-tree\9.8\18419ca5b77a2f81097c741e7872e6ab8d2f40d\asm-tree-9.8.jar')
ANGELICA = pathlib.Path(r'C:\Users\User\AppData\Roaming\.technic\modpacks\forbidden-series\mods\angelica-2.2.18.jar')
GTNH = pathlib.Path(r'C:\Users\User\AppData\Roaming\.technic\modpacks\forbidden-series\mods\gtnhlib-0.11.51.jar')
LWJGL = pathlib.Path(r'C:\Users\User\AppData\Roaming\.technic\libraries\org\lwjgl\lwjgl\lwjgl\2.9.1\lwjgl-2.9.1.jar')
PATCH_TOOL = ROOT / 'tools/java/PatchThePatcherBeta9LoadingPlugin.java'

PINS = {
    LAUNCH: '57f402b626d16cc2705bf2a37add7adbb074f0ca3b3102fa6e23aa303dae682f',
    ASM: '876eab6a83daecad5ca67eb9fcabb063c97b5aeb8cf1fca7a989ecde17522051',
    TREE: '14b7880cb7c85eed101e2710432fc3ffb83275532a6a894dc4c4095d49ad59f1',
    ANGELICA: '563d907f3035aedc7761be0f882674d6e736b63c5860e126e7af7df0d9f15c50',
    GTNH: '9d31e3a5011b39a111536a43f1c2bf74e83a037f5789cf66da3d482e2686adf7',
    LWJGL: 'c118f8d84313980f79e1e7f7ae4b14b10c2f20c77ea4a1c29f6f0b02bc139077',
}

def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

if digest(BASE) != BASE_SHA:
    raise ValueError('Base beta.8 identity changed')
for path, expected in PINS.items():
    if not path.is_file():
        raise FileNotFoundError(path)
    actual = digest(path)
    if actual != expected:
        raise ValueError('Pinned dependency changed: %s expected=%s got=%s' % (path, expected, actual))

with zipfile.ZipFile(BASE) as archive:
    base = {name: archive.read(name) for name in archive.namelist() if not name.endswith('/')}
if len(base) != len(set(base)):
    raise ValueError('Duplicate base entries')

javafiles = sorted(SRC.rglob('*.java'))
expected_sources = ['MpsAngelica218StateCompat.java', 'MpsAngelicaCompatTransformer.java']
if [p.name for p in javafiles] != expected_sources:
    raise ValueError('Unexpected beta.9 source set: ' + str([p.name for p in javafiles]))

def compile_overlay():
    with tempfile.TemporaryDirectory(prefix='patcher-beta9-', dir=RUNS) as folder:
        folder = pathlib.Path(folder)
        cp = ';'.join(str(p) for p in [BASE, LAUNCH, ASM, TREE, ANGELICA, GTNH, LWJGL])
        command = [
            str(JDK / 'javac.exe'), '--release', '8', '-encoding', 'UTF-8',
            '-Xlint:all,-options,-classfile', '-Werror', '-cp', cp,
            '-d', str(folder)
        ] + [str(p) for p in javafiles]
        subprocess.run(command, check=True, timeout=60)
        result = {p.relative_to(folder).as_posix(): p.read_bytes()
                  for p in folder.rglob('*.class')}
        expected = {
            'com/akashic/thepatcher/module/mps/MpsAngelica218StateCompat.class',
            'com/akashic/thepatcher/module/mps/MpsAngelica218StateCompat$1.class',
            'com/akashic/thepatcher/module/mps/MpsAngelica218StateCompat$Snapshot.class',
            'com/akashic/thepatcher/module/mps/MpsAngelica218StateCompat$StateStack.class',
            'com/akashic/thepatcher/module/mps/MpsAngelicaCompatTransformer.class',
        }
        if set(result) != expected:
            raise ValueError('Unexpected compiled overlay: ' + str(sorted(result)))
        for name, data in result.items():
            if struct.unpack_from('>H', data, 6)[0] > 52:
                raise ValueError('Java 8 target broken: ' + name)
        return result

def patch_loader(base_loader):
    with tempfile.TemporaryDirectory(prefix='patcher-beta9-loader-', dir=RUNS) as folder:
        folder = pathlib.Path(folder)
        tool_classes = folder / 'tool-classes'
        tool_classes.mkdir()
        cp = ';'.join(str(p) for p in [ASM, TREE])
        subprocess.run([
            str(JDK / 'javac.exe'), '--release', '8', '-encoding', 'UTF-8',
            '-Xlint:all,-options', '-Werror', '-cp', cp,
            '-d', str(tool_classes), str(PATCH_TOOL)
        ], check=True, timeout=60)
        source = folder / 'AkashicThePatcherLoadingPlugin.class'
        target = folder / 'patched' / 'AkashicThePatcherLoadingPlugin.class'
        source.write_bytes(base_loader)
        run_cp = ';'.join(str(p) for p in [tool_classes, ASM, TREE])
        proc = subprocess.run([
            str(JDK / 'java.exe'), '-Xverify:all', '-cp', run_cp,
            'com.akashic.compatlab.tools.PatchThePatcherBeta9LoadingPlugin',
            str(source), str(target)
        ], check=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=60)
        text = proc.stdout.decode('utf-8', errors='replace')
        if 'PATCH_THEPATCHER_BETA9_LOADING_PLUGIN PASS' not in text:
            raise ValueError('Loading plugin patch proof missing: ' + text[-2000:])
        data = target.read_bytes()
        if struct.unpack_from('>H', data, 6)[0] > 52:
            raise ValueError('Patched loading plugin target broken')
        return data, text.strip()

first = compile_overlay()
second = compile_overlay()
first_hashes = {k: hashlib.sha256(v).hexdigest() for k, v in first.items()}
second_hashes = {k: hashlib.sha256(v).hexdigest() for k, v in second.items()}
if first_hashes != second_hashes:
    raise ValueError('Non-deterministic compiled overlay')

loader_name = 'com/akashic/thepatcher/core/AkashicThePatcherLoadingPlugin.class'
loader1, loader_proof1 = patch_loader(base[loader_name])
loader2, loader_proof2 = patch_loader(base[loader_name])
if loader1 != loader2:
    raise ValueError('Non-deterministic loading plugin patch')

content = dict(base)
content.update(first)
content[loader_name] = loader1

version_class = 'com/akashic/thepatcher/AkashicThePatcherMod.class'
old = b'1.1.0-beta.8'
new = b'1.1.0-beta.9'
if content[version_class].count(old) != 1:
    raise ValueError('Version class shape changed')
content[version_class] = content[version_class].replace(old, new, 1)
for name in ['META-INF/MANIFEST.MF', 'mcmod.info']:
    if content[name].count(old) != 1:
        raise ValueError('Version metadata shape changed: ' + name)
    content[name] = content[name].replace(old, new, 1)

base_classes = {n for n in base if n.endswith('.class')}
new_classes = {n for n in content if n.endswith('.class')}
changed = sorted(n for n in base_classes if base[n] != content[n])
added = sorted(new_classes - base_classes)
expected_changed = [
    'com/akashic/thepatcher/AkashicThePatcherMod.class',
    'com/akashic/thepatcher/core/AkashicThePatcherLoadingPlugin.class',
]
if changed != expected_changed:
    raise ValueError('Unexpected changed classes: ' + str(changed))
expected_added = sorted(first)
if added != expected_added:
    raise ValueError('Unexpected added classes: ' + str(added))

def archive_bytes():
    with tempfile.TemporaryDirectory(prefix='patcher-beta9-jar-', dir=RUNS) as folder:
        path = pathlib.Path(folder) / 'candidate.jar'
        with zipfile.ZipFile(path, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
            for name in sorted(content):
                info = zipfile.ZipInfo(name, (2026, 9, 23, 0, 0, 0))
                info.create_system = 3
                info.compress_type = zipfile.ZIP_DEFLATED
                info.external_attr = 0o644 << 16
                archive.writestr(info, content[name], compresslevel=9)
        with zipfile.ZipFile(path) as archive:
            if archive.testzip() is not None:
                raise ValueError('Archive CRC failed')
        return path.read_bytes()

jar1 = archive_bytes()
jar2 = archive_bytes()
if jar1 != jar2:
    raise ValueError('Non-deterministic beta.9 archive')
if OUTPUT.exists() and OUTPUT.read_bytes() != jar1:
    raise ValueError('Existing beta.9 artifact differs; automatic overwrite refused')
OUTPUT.parent.mkdir(parents=True, exist_ok=True)
if not OUTPUT.exists():
    OUTPUT.write_bytes(jar1)

report = {
    'schema': 1,
    'version': '1.1.0-beta.9',
    'artifact': str(OUTPUT.relative_to(ROOT)),
    'sha256': digest(OUTPUT),
    'bytes': OUTPUT.stat().st_size,
    'deterministicRebuild': True,
    'baseBeta8Sha256': BASE_SHA,
    'baseClasses': len(base_classes),
    'resultClasses': len(new_classes),
    'unchangedBaseClasses': len(base_classes) - len(changed),
    'changedClasses': changed,
    'addedClasses': added,
    'sourceSha256': {str(p.relative_to(ROOT)): digest(p) for p in javafiles},
    'dependencies': {str(p): digest(p) for p in PINS},
    'loaderPatchProof': loader_proof1,
    'loaderPatchDeterministic': loader_proof1 == loader_proof2,
    'angelicaTarget': '2.2.18 exact GLStateManager.class fingerprint',
    'numinaTarget': '0.4.1.105 exact RenderState.class fingerprint',
    'killSwitch': '-Dakashic.mpsAngelica218.disable=true',
    'scope': 'beta.8 plus narrow Numina RenderState bridge for Angelica 2.2.18 COW attrib-stack compatibility',
    'productionInstalled': False,
    'productionApproved': False,
}
REPORT.parent.mkdir(parents=True, exist_ok=True)
REPORT.write_text(json.dumps(report, indent=2, sort_keys=True) + '\n', encoding='utf-8')
print('THEPATCHER_BETA9_BUILD PASS sha256=' + report['sha256']
      + ' bytes=' + str(report['bytes'])
      + ' changed=' + str(len(changed))
      + ' added=' + str(len(added)))