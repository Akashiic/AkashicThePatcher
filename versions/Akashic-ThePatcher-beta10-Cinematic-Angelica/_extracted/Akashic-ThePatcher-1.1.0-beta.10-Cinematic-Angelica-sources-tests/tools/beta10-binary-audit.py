import hashlib, json, pathlib, struct, zipfile

ROOT=pathlib.Path(r'C:\Users\User\Desktop\Codex\Akashic-Compatibility-Lab')
BASE=ROOT/'releases/!Akashic-ThePatcher-1.1.0-beta.8.jar'
CAND=ROOT/'releases/!Akashic-ThePatcher-1.1.0-beta.10-Cinematic-Angelica.jar'
OUT=ROOT/'reports/thepatcher-beta10/final-binary-audit.json'
BASE_SHA='010c3b9f4fdd20528a772904f13238aa836c8af13759aebfe7184ed07beed44b'
CAND_SHA='3182e7e3d6b51854eae008ff8eb8f346a1d0a8b291740ceff8dab9c5fabc9dab'

def sha(p):
    return hashlib.sha256(p.read_bytes()).hexdigest()

if sha(BASE)!=BASE_SHA:
    raise ValueError('beta.8 identity changed')
if sha(CAND)!=CAND_SHA:
    raise ValueError('beta.10 identity changed')

with zipfile.ZipFile(BASE) as z:
    base={n:z.read(n) for n in z.namelist() if not n.endswith('/')}
with zipfile.ZipFile(CAND) as z:
    cand={n:z.read(n) for n in z.namelist() if not n.endswith('/')}

bn=set(base); cn=set(cand)
added=sorted(cn-bn); removed=sorted(bn-cn)
changed=sorted(n for n in bn&cn if base[n]!=cand[n])
expected_added=sorted([
 'com/akashic/thepatcher/module/cinematic/AngelicaVSyncGuard.class',
 'com/akashic/thepatcher/module/cinematic/CinematicAngelicaCompatTransformer.class',
 'com/akashic/thepatcher/module/cinematic/CinematicAngelicaStateFence.class',
])
expected_changed=sorted([
 'META-INF/MANIFEST.MF',
 'mcmod.info',
 'com/akashic/thepatcher/AkashicThePatcherMod.class',
 'com/akashic/thepatcher/core/AkashicThePatcherLoadingPlugin.class',
])
if added!=expected_added:
    raise AssertionError('added='+repr(added))
if removed:
    raise AssertionError('removed='+repr(removed))
if changed!=expected_changed:
    raise AssertionError('changed='+repr(changed))

majors={}
for name in added+changed:
    if not name.endswith('.class'):
        continue
    data=cand[name]
    if data[:4]!=b'\xca\xfe\xba\xbe':
        raise AssertionError('class magic '+name)
    major=struct.unpack_from('>H',data,6)[0]
    majors[name]=major
    if major>52:
        raise AssertionError('Java >8 '+name)

if b'1.1.0-beta.10' not in cand['META-INF/MANIFEST.MF']:
    raise AssertionError('manifest version')
if b'1.1.0-beta.10' not in cand['mcmod.info']:
    raise AssertionError('mcmod version')
unchanged=sorted(n for n in bn&cn if base[n]==cand[n])
report={
 'schema':1,
 'pass':True,
 'base':BASE.name,
 'baseSha256':BASE_SHA,
 'candidate':CAND.name,
 'candidateSha256':CAND_SHA,
 'baseEntries':len(bn),
 'candidateEntries':len(cn),
 'unchangedEntries':len(unchanged),
 'changedEntries':changed,
 'addedEntries':added,
 'removedEntries':removed,
 'classMajors':majors,
 'java8CompatibleChangedAndAddedClasses':True,
}
OUT.parent.mkdir(parents=True,exist_ok=True)
OUT.write_text(json.dumps(report,indent=2,sort_keys=True)+'\n',encoding='utf-8')
print('BETA10_BINARY_AUDIT PASS base=%d candidate=%d unchanged=%d changed=%d added=%d removed=%d sha256=%s'
      %(len(bn),len(cn),len(unchanged),len(changed),len(added),len(removed),CAND_SHA))
