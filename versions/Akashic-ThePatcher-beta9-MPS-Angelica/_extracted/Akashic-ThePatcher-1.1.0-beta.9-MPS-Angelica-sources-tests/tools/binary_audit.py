from __future__ import print_function
import hashlib, json, pathlib, struct, zipfile

ROOT = pathlib.Path(__file__).resolve().parents[2]
BASE = ROOT / 'releases/!Akashic-ThePatcher-1.1.0-beta.8.jar'
CAND = ROOT / 'releases/!Akashic-ThePatcher-1.1.0-beta.9-FINAL-CANDIDATE.jar'
OUT = ROOT / 'reports/thepatcher-beta9/final-binary-audit.json'

BASE_SHA = '010c3b9f4fdd20528a772904f13238aa836c8af13759aebfe7184ed07beed44b'
CAND_SHA = 'cefc4f7b13e9c9bdba5f3c2c3c4a534d75a7d22096c6ea012737ef671e885e70'

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

if sha(BASE) != BASE_SHA:
    raise ValueError('beta.8 identity changed')
if sha(CAND) != CAND_SHA:
    raise ValueError('final candidate identity changed')

with zipfile.ZipFile(BASE) as z:
    base = {n: z.read(n) for n in z.namelist() if not n.endswith('/')}
with zipfile.ZipFile(CAND) as z:
    cand = {n: z.read(n) for n in z.namelist() if not n.endswith('/')}
base_names = set(base)
cand_names = set(cand)
added = sorted(cand_names - base_names)
removed = sorted(base_names - cand_names)
changed = sorted(n for n in base_names & cand_names if base[n] != cand[n])

expected_added = sorted([
    'com/akashic/thepatcher/module/mps/MpsAngelica218StateCompat$1.class',
    'com/akashic/thepatcher/module/mps/MpsAngelica218StateCompat$Snapshot.class',
    'com/akashic/thepatcher/module/mps/MpsAngelica218StateCompat$StateStack.class',
    'com/akashic/thepatcher/module/mps/MpsAngelica218StateCompat.class',
    'com/akashic/thepatcher/module/mps/MpsAngelicaCompatTransformer.class',
])
expected_changed = sorted([
    'META-INF/MANIFEST.MF',
    'mcmod.info',
    'com/akashic/thepatcher/AkashicThePatcherMod.class',
    'com/akashic/thepatcher/core/AkashicThePatcherLoadingPlugin.class',
])

if added != expected_added:
    raise AssertionError('unexpected added entries: ' + repr(added))
if removed:
    raise AssertionError('unexpected removed entries: ' + repr(removed))
if changed != expected_changed:
    raise AssertionError('unexpected changed entries: ' + repr(changed))
class_majors = {}
for name in added + changed:
    if not name.endswith('.class'):
        continue
    data = cand[name]
    if data[:4] != b'\xca\xfe\xba\xbe':
        raise AssertionError('bad class magic: ' + name)
    major = struct.unpack_from('>H', data, 6)[0]
    class_majors[name] = major
    if major > 52:
        raise AssertionError('class newer than Java 8: %s major=%d' % (name, major))

manifest = cand['META-INF/MANIFEST.MF']
mcmod = cand['mcmod.info']
if b'Implementation-Version: 1.1.0-beta.9' not in manifest:
    raise AssertionError('manifest version missing')
if b'1.1.0-beta.9' not in mcmod:
    raise AssertionError('mcmod version missing')

unchanged = sorted(n for n in base_names & cand_names if base[n] == cand[n])
report = {
    'schema': 1,
    'base': BASE.name,
    'baseSha256': BASE_SHA,
    'candidate': CAND.name,
    'candidateSha256': CAND_SHA,
    'baseEntries': len(base_names),
    'candidateEntries': len(cand_names),
    'unchangedEntries': len(unchanged),
    'changedEntries': changed,
    'addedEntries': added,
    'removedEntries': removed,
    'classMajors': class_majors,
    'java8CompatibleChangedAndAddedClasses': True,
    'manifestVersionVerified': True,
    'mcmodVersionVerified': True,
    'pass': True,
}
OUT.parent.mkdir(parents=True, exist_ok=True)
OUT.write_text(json.dumps(report, indent=2, sort_keys=True) + '\n', encoding='utf-8')

print('BETA9_FINAL_BINARY_AUDIT PASS'
      + ' baseEntries=' + str(len(base_names))
      + ' candidateEntries=' + str(len(cand_names))
      + ' unchanged=' + str(len(unchanged))
      + ' changed=' + str(len(changed))
      + ' added=' + str(len(added))
      + ' removed=' + str(len(removed))
      + ' sha256=' + CAND_SHA)
