from __future__ import print_function

import hashlib
import json
import pathlib
import struct
import subprocess
import tempfile
import zipfile

ROOT = pathlib.Path(r"C:\Users\User\Desktop\Codex\Akashic-ThePatcher-1.1.0-beta.26-DrawSubmissionProbe")
BASE = ROOT / "base-beta25.jar"
CLASS = ROOT / "classes/dev/akashic/asynciconcompat/mixins/MixinDefaultChunkRendererDrawSubmissionProbe.class"
COMP = ROOT / "draw-submission-alpha2.15.jar"
OUT = ROOT / "releases/!Akashic-ThePatcher-1.1.0-beta.26-DrawSubmissionProbe.jar"
REPORT = ROOT / "reports/final-build-audit.json"

BASE_SHA = "f49b424c4000eb992a42cc9baff66c353d2c86bc28b21d10b19bed83b70766de"
OLD = "1.1.0-beta.25"
NEW = "1.1.0-beta.26"
MOD = "com/akashic/thepatcher/AkashicThePatcherMod.class"
MIXIN_CFG = "mixins.akashicasynccompat.json"
REFMAP = "mixins.akashicasynccompat.refmap.json"
NEW_CLASS = "dev/akashic/asynciconcompat/mixins/MixinDefaultChunkRendererDrawSubmissionProbe.class"

JDK = pathlib.Path(r"C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot\bin")
ASM = pathlib.Path(r"C:\Users\User\.gradle\caches\modules-2\files-2.1\org.ow2.asm\asm\9.8\dc19ecb3f7889b7860697215cae99c0f9b6f6b4b\asm-9.8.jar")
TREE = pathlib.Path(r"C:\Users\User\.gradle\caches\modules-2\files-2.1\org.ow2.asm\asm-tree\9.8\18419ca5b77a2f81097c741e7872e6ab8d2f40d\asm-tree-9.8.jar")
TOOL = ROOT / "tools/PatchThePatcherVersion.java"
RUNS = ROOT / "runs"

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def major(data):
    return struct.unpack_from(">H", data, 6)[0]

def make_zip(content, target, stamp=(2026, 9, 25, 0, 10, 0)):
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
assert CLASS.is_file()
assert major(CLASS.read_bytes()) <= 52

with zipfile.ZipFile(BASE) as z:
    base = {n: z.read(n) for n in z.namelist() if not n.endswith("/")}

cfg_obj = json.loads(base[MIXIN_CFG].decode("utf-8"))
client = cfg_obj["client"]
name = "MixinDefaultChunkRendererDrawSubmissionProbe"
assert name not in client
client.append(name)
cfg = (json.dumps(cfg_obj, indent=4) + "\n").encode("utf-8")

component = {
    NEW_CLASS: CLASS.read_bytes(),
    MIXIN_CFG: cfg,
    REFMAP: base[REFMAP],
}
make_zip(component, COMP)
comp_sha = sha(COMP)

def patch_version():
    with tempfile.TemporaryDirectory(prefix="beta26-version-", dir=RUNS) as work:
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

content = dict(base)
content[NEW_CLASS] = component[NEW_CLASS]
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
assert added == [NEW_CLASS], added
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

assert b"MixinDefaultChunkRendererDrawSubmissionProbe" in content[MIXIN_CFG]
for literal in [
    b"DrawSubmission", b"geometrySections", b"execute-probe-failed",
    b"GL_CURRENT_PROGRAM", b"indexBufferSize"
]:
    assert literal in content[NEW_CLASS], literal

for prefix in [
    "org/embeddedt/embeddium/",
    "com/gtnewhorizons/angelica/",
    "com/gtnewhorizon/gtnhlib/",
]:
    assert not any(n.startswith(prefix) for n in content), prefix

def make_candidate():
    with tempfile.TemporaryDirectory(prefix="beta26-jar-", dir=RUNS) as work:
        target = pathlib.Path(work) / "candidate.jar"
        make_zip(content, target)
        return target.read_bytes()

j1 = make_candidate()
j2 = make_candidate()
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
    "baseBeta25Sha256": BASE_SHA,
    "componentAlpha215Sha256": comp_sha,
    "changedClasses": changed,
    "addedClasses": added,
    "removedClasses": removed,
    "changedResources": changed_res,
    "scope": (
        "telemetry-only Celeritas draw submission probe: render-list regions, "
        "pass storages, geometry section counts, matrices, non-empty batch "
        "execute calls, draw-command counts and pre-draw GL bindings"
    ),
    "behaviorMutation": False,
    "inheritsBeta25BlendAlphaProbeBitIdentical": True,
    "versionPatchProof": proof1,
    "productionInstalled": False,
    "productionApproved": False,
}
REPORT.parent.mkdir(parents=True, exist_ok=True)
REPORT.write_text(
    json.dumps(report, indent=2, sort_keys=True) + "\n",
    encoding="utf-8")

print(
    "THEPATCHER_BETA26_BUILD PASS sha256=%s component=%s bytes=%d"
    % (report["sha256"], comp_sha, report["bytes"]))
