from __future__ import print_function
import hashlib, json, pathlib, struct, subprocess, tempfile, zipfile

ROOT = pathlib.Path(r"C:\Users\User\Desktop\Codex\Akashic-ThePatcher-1.1.0-beta.19-ItemsAtlasBatchPackFix")
JDK = pathlib.Path(r"C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot\bin")
BASE = ROOT / "base-beta18.jar"
BASE_SHA = "12f00211e9f8cd66cad9d9a90da7f809d4e1987a72d31461dfbe42edf5169262"
COMP = ROOT / "atlasbatch-alpha2.9.jar"
COMP_SHA = "a1cee846e34689ab470bc739211467412e7bcc08e1fec328bffdc91094b6efb9"
ASM = pathlib.Path(r"C:\Users\User\.gradle\caches\modules-2\files-2.1\org.ow2.asm\asm\9.8\dc19ecb3f7889b7860697215cae99c0f9b6f6b4b\asm-9.8.jar")
TREE = pathlib.Path(r"C:\Users\User\.gradle\caches\modules-2\files-2.1\org.ow2.asm\asm-tree\9.8\18419ca5b77a2f81097c741e7872e6ab8d2f40d\asm-tree-9.8.jar")
VERSION_TOOL = ROOT / "tools/PatchThePatcherVersion.java"
RUNS = ROOT / "runs"
OUT = ROOT / "releases/!Akashic-ThePatcher-1.1.0-beta.19-ItemsAtlasBatchPackFix.jar"
REPORT = ROOT / "reports/final-build-audit.json"

OLD_VERSION = "1.1.0-beta.18"
NEW_VERSION = "1.1.0-beta.19"
MOD_ENTRY = "com/akashic/thepatcher/AkashicThePatcherMod.class"
STATE_ENTRY = "dev/akashic/asynciconcompat/upload/ItemsAtlasUploadBatcher$State.class"

def sha_bytes(data):
    return hashlib.sha256(data).hexdigest()

def sha(path):
    return sha_bytes(path.read_bytes())

def major(data):
    return struct.unpack_from(">H", data, 6)[0]

if sha(BASE) != BASE_SHA:
    raise ValueError("beta.18 base identity changed")
if sha(COMP) != COMP_SHA:
    raise ValueError("alpha.2.9 component identity changed")

with zipfile.ZipFile(BASE) as z:
    base = {n: z.read(n) for n in z.namelist() if not n.endswith("/")}
with zipfile.ZipFile(COMP) as z:
    comp_names = set(z.namelist())
    if STATE_ENTRY not in comp_names:
        raise ValueError("State nested class missing from component")
    state_bytes = z.read(STATE_ENTRY)
    component_batch_classes = sorted(
        n for n in z.namelist()
        if n.startswith("dev/akashic/asynciconcompat/upload/ItemsAtlasUploadBatcher")
        and n.endswith(".class")
    )

if STATE_ENTRY in base:
    raise ValueError("beta.18 unexpectedly already contains State class")

def compile_tool(folder):
    cp = ";".join(str(p) for p in (ASM, TREE))
    subprocess.run([
        str(JDK / "javac.exe"), "--release", "8", "-encoding", "UTF-8",
        "-Xlint:all,-options", "-Werror", "-cp", cp, "-d", str(folder),
        str(VERSION_TOOL),
    ], check=True, timeout=60)

def patch_version():
    with tempfile.TemporaryDirectory(prefix="patcher-beta19-version-", dir=RUNS) as d:
        d = pathlib.Path(d)
        tools = d / "tools"
        tools.mkdir()
        compile_tool(tools)
        dst = d / "AkashicThePatcherMod.class"
        cp = ";".join(str(p) for p in (tools, ASM, TREE))
        proc = subprocess.run([
            str(JDK / "java.exe"), "-Xverify:all", "-cp", cp,
            "com.akashic.compatlab.tools.PatchThePatcherVersion",
            str(BASE), OLD_VERSION, NEW_VERSION, str(dst),
        ], check=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=60)
        text = proc.stdout.decode("utf-8", errors="replace")
        if "THEPATCHER_VERSION_PATCH PASS" not in text:
            raise ValueError("version proof missing: " + text[-2000:])
        return dst.read_bytes(), text.strip()

version1, proof1 = patch_version()
version2, proof2 = patch_version()
if version1 != version2:
    raise ValueError("non-deterministic version patch")

content = dict(base)
content[MOD_ENTRY] = version1
content[STATE_ENTRY] = state_bytes

manifest = content["META-INF/MANIFEST.MF"].decode("utf-8")
old_line = "Implementation-Version: " + OLD_VERSION
new_line = "Implementation-Version: " + NEW_VERSION
if manifest.count(old_line) != 1:
    raise ValueError("manifest version shape changed")
content["META-INF/MANIFEST.MF"] = manifest.replace(old_line, new_line, 1).encode("utf-8")

mcmod = content["mcmod.info"].decode("utf-8")
if mcmod.count(OLD_VERSION) != 1:
    raise ValueError("mcmod version shape changed")
content["mcmod.info"] = mcmod.replace(OLD_VERSION, NEW_VERSION, 1).encode("utf-8")

base_classes = {n for n in base if n.endswith(".class")}
result_classes = {n for n in content if n.endswith(".class")}
changed_classes = sorted(
    n for n in base_classes if n in content and base[n] != content[n]
)
added_classes = sorted(result_classes - base_classes)
removed_classes = sorted(base_classes - result_classes)

if changed_classes != [MOD_ENTRY]:
    raise ValueError("unexpected changed classes: " + repr(changed_classes))
if added_classes != [STATE_ENTRY]:
    raise ValueError("unexpected added classes: " + repr(added_classes))
if removed_classes:
    raise ValueError("unexpected removed classes: " + repr(removed_classes))

changed_resources = sorted(
    n for n in base
    if not n.endswith(".class") and n in content and base[n] != content[n]
)
if changed_resources != ["META-INF/MANIFEST.MF", "mcmod.info"]:
    raise ValueError("unexpected changed resources: " + repr(changed_resources))

for name in result_classes:
    if major(content[name]) > 52:
        raise ValueError("Java 8 target broken: " + name)

# Package-closure gate: every nested/top-level ItemsAtlasUploadBatcher class
# emitted by alpha.2.9 must exist in the final candidate.
final_batch_classes = sorted(
    n for n in content
    if n.startswith("dev/akashic/asynciconcompat/upload/ItemsAtlasUploadBatcher")
    and n.endswith(".class")
)
if final_batch_classes != component_batch_classes:
    raise ValueError(
        "batcher package closure mismatch component=%r final=%r"
        % (component_batch_classes, final_batch_classes)
    )

if sha_bytes(content[STATE_ENTRY]) != "22de81a893805238b1a3005493e7a1ab9dacfc65ff3241f5cb4b80c4dd80863c":
    raise ValueError("State class identity mismatch")

# Everything except version metadata and State must remain beta.18-identical.
for entry in base:
    if entry in (MOD_ENTRY, "META-INF/MANIFEST.MF", "mcmod.info"):
        continue
    if content[entry] != base[entry]:
        raise ValueError("unexpected beta.18 delta: " + entry)

def archive_bytes():
    with tempfile.TemporaryDirectory(prefix="patcher-beta19-jar-", dir=RUNS) as d:
        p = pathlib.Path(d) / "candidate.jar"
        with zipfile.ZipFile(p, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as z:
            for name in sorted(content):
                info = zipfile.ZipInfo(name, (2026, 9, 24, 16, 30, 0))
                info.create_system = 3
                info.compress_type = zipfile.ZIP_DEFLATED
                info.external_attr = 0o644 << 16
                z.writestr(info, content[name], compresslevel=9)
        with zipfile.ZipFile(p) as z:
            bad = z.testzip()
            if bad is not None:
                raise ValueError("archive CRC failed: " + bad)
        return p.read_bytes()

jar1 = archive_bytes()
jar2 = archive_bytes()
if jar1 != jar2:
    raise ValueError("non-deterministic beta.19 archive")

OUT.parent.mkdir(parents=True, exist_ok=True)
OUT.write_bytes(jar1)

report = {
    "schema": 1,
    "version": NEW_VERSION,
    "artifact": str(OUT.relative_to(ROOT)),
    "sha256": sha(OUT),
    "bytes": OUT.stat().st_size,
    "deterministicRebuild": True,
    "baseBeta18Sha256": BASE_SHA,
    "componentAlpha29Sha256": COMP_SHA,
    "changedClasses": changed_classes,
    "addedClasses": added_classes,
    "removedClasses": removed_classes,
    "changedResources": changed_resources,
    "versionPatchProof": proof1,
    "versionPatchDeterministic": proof1 == proof2,
    "packagingFix": {
        "missingInBeta18": STATE_ENTRY,
        "componentBatchClasses": component_batch_classes,
        "finalBatchClasses": final_batch_classes,
        "logicChanged": False,
    },
    "scope": "beta.18 packaging fix only: include ItemsAtlasUploadBatcher$State",
    "productionInstalled": False,
    "productionApproved": False,
}
REPORT.parent.mkdir(parents=True, exist_ok=True)
REPORT.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")

print(
    "THEPATCHER_BETA19_BUILD PASS sha256=%s bytes=%d added=%s"
    % (report["sha256"], report["bytes"], STATE_ENTRY)
)