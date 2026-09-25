from __future__ import print_function

import hashlib
import json
import pathlib
import struct
import subprocess
import tempfile
import zipfile

ROOT = pathlib.Path(r"C:\Users\User\Desktop\Codex\Akashic-ThePatcher-1.1.0-beta.28-NuminaFovFiniteGuard")
BASE = ROOT / "base-beta27.jar"
OUT = ROOT / "releases/!Akashic-ThePatcher-1.1.0-beta.28-NuminaFovFiniteGuard.jar"
REPORT = ROOT / "reports/final-build-audit.json"
RUNS = ROOT / "runs"

BASE_SHA = "fd0a88afa9ec037f326be8cc5d702f865d6ded70f80b2c7f3002d330ce43140c"
OLD = "1.1.0-beta.27"
NEW = "1.1.0-beta.28"
MOD = "com/akashic/thepatcher/AkashicThePatcherMod.class"
MIXIN_CFG = "mixins.akashicasynccompat.json"
REFMAP = "mixins.akashicasynccompat.refmap.json"

NEW_CLASSES = {
    "dev/akashic/asynciconcompat/graphics/NuminaFovSafety.class":
        ROOT / "classes/dev/akashic/asynciconcompat/graphics/NuminaFovSafety.class",
    "dev/akashic/asynciconcompat/mixins/MixinForgeHooksClientFovFiniteGuard.class":
        ROOT / "classes/dev/akashic/asynciconcompat/mixins/MixinForgeHooksClientFovFiniteGuard.class",
}

JDK = pathlib.Path(r"C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot\bin")
ASM = pathlib.Path(r"C:\Users\User\.gradle\caches\modules-2\files-2.1\org.ow2.asm\asm\9.8\dc19ecb3f7889b7860697215cae99c0f9b6f6b4b\asm-9.8.jar")
TREE = pathlib.Path(r"C:\Users\User\.gradle\caches\modules-2\files-2.1\org.ow2.asm\asm-tree\9.8\18419ca5b77a2f81097c741e7872e6ab8d2f40d\asm-tree-9.8.jar")
TOOL = ROOT / "tools/PatchThePatcherVersion.java"

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def major(data):
    return struct.unpack_from(">H", data, 6)[0]

def make_zip(content, target, stamp=(2026, 9, 25, 2, 15, 0)):
    with zipfile.ZipFile(target, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        for name in sorted(content):
            info = zipfile.ZipInfo(name, stamp)
            info.create_system = 3
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o644 << 16
            z.writestr(info, content[name], compress_type=zipfile.ZIP_DEFLATED, compresslevel=9)
    with zipfile.ZipFile(target) as z:
        assert z.testzip() is None

assert BASE.is_file() and sha(BASE) == BASE_SHA
for path in NEW_CLASSES.values():
    assert path.is_file()
    assert major(path.read_bytes()) <= 52

with zipfile.ZipFile(BASE) as z:
    base = {n: z.read(n) for n in z.namelist() if not n.endswith("/")}

cfg_obj = json.loads(base[MIXIN_CFG].decode("utf-8"))
client = cfg_obj["client"]
mixin_name = "MixinForgeHooksClientFovFiniteGuard"
assert mixin_name not in client
client.append(mixin_name)
cfg = (json.dumps(cfg_obj, indent=4) + "\n").encode("utf-8")

def patch_version():
    with tempfile.TemporaryDirectory(prefix="beta28-version-", dir=RUNS) as work:
        work = pathlib.Path(work)
        cp = ";".join(str(x) for x in (ASM, TREE))
        subprocess.run([
            str(JDK / "javac.exe"), "--release", "8", "-encoding", "UTF-8",
            "-Xlint:all,-options", "-Werror", "-cp", cp,
            "-d", str(work), str(TOOL)
        ], check=True, timeout=60)
        out = work / "AkashicThePatcherMod.class"
        cp2 = ";".join([str(work), str(ASM), str(TREE)])
        proc = subprocess.run([
            str(JDK / "java.exe"), "-Xverify:all", "-cp", cp2,
            "com.akashic.compatlab.tools.PatchThePatcherVersion",
            str(BASE), OLD, NEW, str(out)
        ], check=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=60)
        text = proc.stdout.decode("utf-8", "replace")
        assert "THEPATCHER_VERSION_PATCH PASS" in text
        return out.read_bytes(), text.strip()

v1, proof1 = patch_version()
v2, proof2 = patch_version()
assert v1 == v2
assert proof1 == proof2

content = dict(base)
for name, path in NEW_CLASSES.items():
    assert name not in content
    content[name] = path.read_bytes()
content[MIXIN_CFG] = cfg
content[MOD] = v1

manifest = content["META-INF/MANIFEST.MF"].decode("utf-8")
assert manifest.count("Implementation-Version: " + OLD) == 1
content["META-INF/MANIFEST.MF"] = manifest.replace(
    "Implementation-Version: " + OLD,
    "Implementation-Version: " + NEW, 1).encode("utf-8")

mcmod = content["mcmod.info"].decode("utf-8")
assert mcmod.count(OLD) == 1
content["mcmod.info"] = mcmod.replace(OLD, NEW, 1).encode("utf-8")

base_classes = {n for n in base if n.endswith(".class")}
result_classes = {n for n in content if n.endswith(".class")}
changed = sorted(n for n in base_classes if base[n] != content[n])
added = sorted(result_classes - base_classes)
removed = sorted(base_classes - result_classes)

assert changed == [MOD], changed
assert added == sorted(NEW_CLASSES), added
assert removed == [], removed

changed_res = sorted(
    n for n in base
    if not n.endswith(".class") and base[n] != content[n])
assert changed_res == [
    "META-INF/MANIFEST.MF", "mcmod.info", MIXIN_CFG
], changed_res

for n, data in content.items():
    if n.endswith(".class"):
        assert major(data) <= 52, n

for n in base_classes:
    if n != MOD:
        assert content[n] == base[n], "inherited class changed: " + n

safety = content["dev/akashic/asynciconcompat/graphics/NuminaFovSafety.class"]
mixin = content["dev/akashic/asynciconcompat/mixins/MixinForgeHooksClientFovFiniteGuard.class"]

for literal in [
    b"safeBaseline",
    b"isFinitePositive",
]:
    assert literal in safety, literal

for literal in [
    b"ForgeHooksClient",
    b"getOffsetFOV",
    b"EntityPlayerSP",
    b"NuminaFovSafety",
    b"Invalid post-event FOV repaired",
]:
    assert literal in mixin, literal

assert b"MixinForgeHooksClientFovFiniteGuard" in content[MIXIN_CFG]

for prefix in [
    "org/embeddedt/embeddium/",
    "com/gtnewhorizons/angelica/",
    "com/gtnewhorizon/gtnhlib/",
]:
    assert not any(n.startswith(prefix) for n in content), prefix

def candidate_bytes():
    with tempfile.TemporaryDirectory(prefix="beta28-jar-", dir=RUNS) as work:
        target = pathlib.Path(work) / "candidate.jar"
        make_zip(content, target)
        return target.read_bytes()

j1 = candidate_bytes()
j2 = candidate_bytes()
assert j1 == j2
OUT.parent.mkdir(parents=True, exist_ok=True)
OUT.write_bytes(j1)

report = {
    "schema": 1,
    "version": NEW,
    "artifact": str(OUT.relative_to(ROOT)),
    "sha256": sha(OUT),
    "bytes": OUT.stat().st_size,
    "deterministicRebuild": True,
    "baseBeta27Sha256": BASE_SHA,
    "changedClasses": changed,
    "addedClasses": added,
    "removedClasses": removed,
    "changedResources": changed_res,
    "scope": (
        "post-event finite-safety guard at ForgeHooksClient.getOffsetFOV: preserve "
        "all finite positive FOVUpdateEvent listener results, repair only invalid "
        "output before EntityRenderer can latch NaN/Infinity into fovModifierHand"
    ),
    "numinaFovPolicy": {
        "rootCauseCandidate": "Numina 0.4.1.105 FOVUpdateEventHandler",
        "targetHook": "ForgeHooksClient.getOffsetFOV(EntityPlayerSP,float)",
        "preserveFinitePositiveListenerResults": True,
        "sanitizeNonFiniteOrNonPositiveResult": True,
        "fallback": "vanilla/Forge input fov, then 1.0",
        "diagnostics": ["baseFov", "invalidResult", "entityClass"],
        "maxDiagnosticWarnings": 12,
        "directNuminaClassReference": False,
    },
    "inheritsBeta27BitIdentical": True,
    "versionPatchProof": proof1,
    "productionInstalled": False,
    "productionApproved": False,
}
REPORT.parent.mkdir(parents=True, exist_ok=True)
REPORT.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")

print("THEPATCHER_BETA28_BUILD PASS sha256=%s bytes=%d"
      % (report["sha256"], report["bytes"]))
