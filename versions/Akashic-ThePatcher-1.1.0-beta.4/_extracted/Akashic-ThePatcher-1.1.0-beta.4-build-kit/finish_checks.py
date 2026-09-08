from pathlib import Path
import subprocess,runpy,os,json,hashlib,shutil,zipfile,struct,sys
r=Path(__file__).resolve().parent;b=runpy.run_path(str(r/'build.py'));java=b['JAVA'];home=Path.home()
e=r/'evidence';e.mkdir(exist_ok=True);results=[]
def run(label,args,required=True):
    p=subprocess.run(list(map(str,args)),cwd=r,capture_output=True,text=True)
    (e/(label+'.log')).write_text(p.stdout+p.stderr,encoding='utf-8')
    results.append({'label':label,'exit':p.returncode,'log':label+'.log'})
    (e/'checks-current.json').write_text(json.dumps(results,indent=2))
    print(label+': exit='+str(p.returncode),flush=True)
    if p.returncode and required:raise RuntimeError(label+': '+(p.stdout+p.stderr)[-4000:])
    return p.stdout+p.stderr
try:
    run('release-build-1',[sys.executable,r/'build.py'])
    jar=r/'build/!Akashic-ThePatcher-1.1.0-beta.4.jar';sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
    first=sha(jar)
    tc=r/'test-classes';tc.mkdir(exist_ok=True)
    asm=os.pathsep.join(map(str,b['ASM']));dd=home/'AppData/Roaming/.technic/modpacks/forbidden-series/mods/DimensionalDoors-2.2.6-1.7.10.jar'
    assert sha(dd)=='59aa3de8614074d7d03ac10b2771537843d4b2af1141e3b7b3dd6bc101920e64'
    corecp=os.pathsep.join(map(str,[jar]+b['ASM']+[b['MC']]+b['LIBS']))
    run('compile-standard-tests',[java/'javac.exe','--release','8','-Xlint:-options','-cp',corecp,'-d',tc,
        r/'src/test/java/QueueSelfTest.java',r/'src/test/java/TransformerSelfTest.java',r/'src/test/java/SideScopeTest.java',r/'src/test/java/NativeBaseline.java'])
    run('compile-world-fixture',[java/'javac.exe','--release','8','-cp',asm,'-d',tc,r/'tools/WorldFixture.java'])
    run('generate-world-fixture',[java/'java.exe','-cp',str(tc)+os.pathsep+asm,'WorldFixture',tc])
    f=r/'test-platform';f.mkdir(exist_ok=True)
    run('compile-side-fixtures',[java/'javac.exe','--release','8','-cp',corecp,'-d',f,r/'stubs/FMLCommonHandler.java',r/'stubs/FMLLaunchHandler.java'])
    source=r/'src/test/java/NativeReplay.java';s=source.read_text()
    s=s.replace('world.field_72995_K=true;', 'field(world,World.class,"field_72995_K",true);')
    s=s.replace('tile.field_145850_b=world;', 'field(tile,Class.forName("net.minecraft.tileentity.TileEntity"),"field_145850_b",world);')
    s=s.replace('static NBTTagCompound tag(int x,int y,int z,int orientation) {','static NBTTagCompound tag(int x,int y,int z,int orientation) throws Exception {')
    s=s.replace('new ClientLinkData(new Point4D(x,y,z,0),null).writeToNBT(linkTag);',
        'Constructor<?> ctor=Arrays.stream(ClientLinkData.class.getDeclaredConstructors()).filter(c -> c.getParameterTypes().length==3).findFirst().get();\n        ctor.setAccessible(true);((ClientLinkData)ctor.newInstance(new Point4D(x,y,z,0),LinkType.CLIENT,null)).writeToNBT(linkTag);')
    source.write_text(s)
    srg=home/'AkashicDev/CubicChunks-Crucible/source/build/rfg/srg_merged_minecraft.jar'
    nativecp=os.pathsep.join(map(str,[f,tc,jar,srg,dd,b['MC']]+b['ASM']+b['LIBS']))
    run('compile-native-replay',[java/'javac.exe','--release','8','-Xlint:-options','-cp',nativecp,'-d',tc,source])
    run('release-build-2',[sys.executable,r/'build.py']);assert sha(jar)==first,'Non-deterministic build'
    pkg='com.akashic.thepatcher.module.dimdoors.'
    cp=str(tc)+os.pathsep+corecp
    run('queue-final',[java/'java.exe','-Xverify:all','-cp',cp,pkg+'QueueSelfTest'])
    run('transformers-final',[java/'java.exe','-Xverify:all','-cp',cp,pkg+'TransformerSelfTest',dd,r/'test-transformed',r/'test-instance'])
    for side,value in [('CLIENT','true'),('CLIENT','false'),('CLIENT','invalid'),('SERVER','true')]:
        label='scope-'+side+'-'+value
        run(label,[java/'java.exe','-Xverify:all','-Dfixture.side='+side,'-cp',str(f)+os.pathsep+cp,pkg+'SideScopeTest',r/'test-instances'/label,value])
    run('native-baseline-final',[java/'java.exe','-Xverify:all','-cp',nativecp,pkg+'NativeReplay','baseline'])
    fixedcp=str(r/'test-transformed')+os.pathsep+nativecp
    run('native-fixed-final',[java/'java.exe','-Xverify:all','-cp',fixedcp,pkg+'NativeReplay'])
    j8=home/'AppData/Roaming/.technic/runtimes/jre-legacy/bin/java.exe'
    assert j8.is_file(),'Client Java8 runtime unavailable'
    run('java8-queue-final',[j8,'-Xverify:all','-cp',cp,pkg+'QueueSelfTest'])
    run('java8-native-final',[j8,'-Xverify:all','-cp',fixedcp,pkg+'NativeReplay'])
    with zipfile.ZipFile(jar) as z,zipfile.ZipFile(b['BASE']) as old:
        names=z.namelist();classes=[n for n in names if n.endswith('.class')]
        assert z.testzip() is None and len(names)==len(set(names))
        assert all(n.startswith('com/akashic/') for n in classes)
        assert all(struct.unpack('>H',z.read(n)[6:8])[0]==52 for n in classes)
        assert not any('SelfTest' in n or 'fixture/' in n or n.startswith('cpw/') for n in names)
        original=[n for n in old.namelist() if n.endswith('.class')]
        unchanged=[n for n in original if z.read(n)==old.read(n)]
        changed=[n for n in original if n not in unchanged]
        assert set(changed)=={'com/akashic/thepatcher/core/AkashicThePatcherLoadingPlugin.class','com/akashic/thepatcher/AkashicThePatcherMod.class'}
        assert json.loads(z.read('mcmod.info'))[0]['version']=='1.1.0-beta.4'
    active=home/'AppData/Roaming/.technic/modpacks/forbidden-series/mods/!Akashic-ThePatcher-1.1.0-beta.3.jar'
    assert sha(active)==b['EXPECTED'],'Active baseline changed'
    audit={'version':'1.1.0-beta.4','sha256':sha(jar),'bytes':jar.stat().st_size,'classes':len(classes),
        'base_classes_unchanged':len(unchanged),'base_classes_total':len(original),'changed_base_classes':changed,
        'deterministic_rebuild':True,'class_major':52,'checks':results,'active_modpack_unchanged':True,
        'actual_dimdoors_jar_sha256':sha(dd),'graphical_client_tested':False,'production_approved':False,
        'native_test_scope':'actual DimDoors/NBT/S35/Compactor/watcher with controlled World and FML-side fixtures',
        'build_model':'binary-preserving overlay of pinned beta.3; not a fresh compilation of every historical module'}
    (e/'final-audit.json').write_text(json.dumps(audit,indent=2),encoding='utf-8')
    result=subprocess.run([sys.executable,str(r/'package_release.py')],cwd=r,capture_output=True,text=True)
    print(result.stdout+result.stderr,flush=True)
    if result.returncode:raise RuntimeError('Packaging failed')
except Exception as error:
    (e/'FINISH-FAILED.txt').write_text(str(error),encoding='utf-8')
    print('VALIDATION_NOT_COMPLETE: '+str(error),flush=True);sys.exit(1)
