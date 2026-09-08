from pathlib import Path
import subprocess, zipfile, hashlib, json, os, runpy, shutil, sys
r=Path(__file__).resolve().parent
home=Path.home()
mods=home/'AppData/Roaming/.technic/modpacks/forbidden-series/mods'
b=runpy.run_path(str(r/'build.py'))
jdk=b['JAVA']; dd=mods/'DimensionalDoors-2.2.6-1.7.10.jar';base=b['BASE']
candidate=r/'build/!Akashic-ThePatcher-1.1.0-beta.4.jar'
testclasses=r/'test-classes'
srg=home/'AkashicDev/CubicChunks-Crucible/source/build/rfg/srg_merged_minecraft.jar'
nativecp=os.pathsep.join(map(str,[testclasses,candidate,srg,dd,b['MC']]+b['ASM']+b['LIBS']))
def execute(label,args):
    result=subprocess.run(list(map(str,args)),capture_output=True,text=True,cwd=r)
    (r/'evidence'/(label+'.log')).write_text(result.stdout+result.stderr,encoding='utf-8')
    print((result.stdout+result.stderr)[-6500:]);print(label+'_EXIT='+str(result.returncode),flush=True)
    return result.returncode
print('PATCHER_WORKSPACE_READY',r)
