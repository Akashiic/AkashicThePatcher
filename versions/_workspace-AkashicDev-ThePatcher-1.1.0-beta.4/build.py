from pathlib import Path
import hashlib, json, os, shutil, subprocess, sys, zipfile
ROOT = Path(__file__).resolve().parent
HOME = Path.home()
JAVA = Path(os.environ.get('AKASHIC_JDK', 'C:/Program Files/Eclipse Adoptium/jdk-21.0.11.10-hotspot/bin'))
BASE = ROOT/'base/!Akashic-ThePatcher-1.1.0-beta.3.jar'
EXPECTED = 'd39feb760fd74b3fd596b50832fd85f80d0d056ec47f33bfa48a244415bd7fb0'
MC = HOME/'AkashicDev/CubicChunks-Crucible/source/build/rfg/recompiled_minecraft-1.7.10.jar'
LIBS = list((HOME/'AkashicDev/CubicChunks-Crucible/bootstrap-probe/libraries').rglob('*.jar'))
ASM = sorted((ROOT/'lib').glob('asm*-9.8.jar'))
LIBS = [p for p in LIBS if not p.name.startswith('asm')]
CP = os.pathsep.join(map(str, ASM + [BASE, MC] + LIBS))
def run(args):
    completed = subprocess.run(list(map(str, args)), stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    print(completed.stdout, end='', flush=True)
    if completed.returncode: raise SystemExit(completed.returncode)
def main():
    assert hashlib.sha256(BASE.read_bytes()).hexdigest() == EXPECTED, 'Baseline hash mismatch'
    build = ROOT/'build'
    if build.exists(): shutil.rmtree(build)
    classes = build/'classes'; classes.mkdir(parents=True)
    tools = build/'tools'; tools.mkdir()
    sources = sorted((ROOT/'src/main/java').rglob('*.java'))
    args = ['--release','8','-Xlint:all,-path,-options','-Werror','-encoding','UTF-8','-cp',CP,'-d',str(classes)] + list(map(str,sources))
    argfile = build/'javac.args'; argfile.write_text('\n'.join('"'+x.replace('\\','/')+'"' for x in args), encoding='utf-8')
    run([JAVA/'javac.exe', '@'+str(argfile)])
    run([JAVA/'javac.exe','--release','8','-cp',os.pathsep.join(map(str,ASM)),'-d',tools,ROOT/'tools/Overlay.java'])
    with zipfile.ZipFile(BASE) as old: entries = {n: old.read(n) for n in old.namelist()}
    changed = ['com/akashic/thepatcher/core/AkashicThePatcherLoadingPlugin.class','com/akashic/thepatcher/AkashicThePatcherMod.class']
    for index, name in enumerate(changed):
        incoming = build/f'original-{index}.class'; outgoing = build/f'overlay-{index}.class'
        incoming.write_bytes(entries[name])
        run([JAVA/'java.exe','-cp',os.pathsep.join(map(str,[tools]+ASM)),'Overlay',incoming,outgoing])
        data = outgoing.read_bytes()
        if index == 1:
            assert b'1.1.0-beta.3' in data
            data = data.replace(b'1.1.0-beta.3',b'1.1.0-beta.4')
        entries[name] = data
    for name in ['META-INF/MANIFEST.MF','mcmod.info']:
        entries[name] = entries[name].replace(b'1.1.0-beta.3',b'1.1.0-beta.4')
    for f in classes.rglob('*.class'): entries[f.relative_to(classes).as_posix()] = f.read_bytes()
    output = build/'!Akashic-ThePatcher-1.1.0-beta.4.jar'
    with zipfile.ZipFile(output,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
        for name,data in sorted(entries.items()):
            info = zipfile.ZipInfo(name, (2026,9,8,0,0,0)); info.compress_type = zipfile.ZIP_DEFLATED
            z.writestr(info,data)
    result = {'path':str(output),'sha256':hashlib.sha256(output.read_bytes()).hexdigest(),'bytes':output.stat().st_size,
        'classes':sum(n.endswith('.class') for n in entries),'binary_base_sha256':EXPECTED}
    (build/'candidate.json').write_text(json.dumps(result,indent=2))
    print('BUILD_OK='+json.dumps(result),flush=True)
if __name__ == '__main__': main()
