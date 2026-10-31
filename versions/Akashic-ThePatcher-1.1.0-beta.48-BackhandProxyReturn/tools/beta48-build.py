"""Build and gate 1.1.0-beta.48-BackhandProxyReturn from the beta.47 release JAR.

    python3 beta48-build.py --jdk8 <jdk8 home> --jdk21 <jdk21 home> --libs <dir> --out <output.jar>

--libs: tools/ci/fetch_build_libs.sh (only asm-9.7 / asm-tree / asm-analysis / asm-util are used).

Problem (latest.log 2026-10-31): behind the Velocity proxy, Forge backend -> vanilla lobby (Paper 1.21 + ViaVersion)
-> back to the backend on the same connection. On the return Backhand's state machine (BackhandGuardHooks, unchanged
from beta.35 to beta.47) captured the backend's FML mod list ("remote FML mod list captured: type=MODDED ... proof=6/6"), but the
TRANSITION -> MODDED promotion in captureRemoteModMap marked that proof consumed before the backend's JoinGame arrived,
so onJoinGamePacket saw no fresh proof and went back to TRANSITION ("join game/backend switch: type=TRANSITION ...
offhandAllowed=false"): the offhand stayed blocked for the rest of the session. Fresh connections were not affected
(there the proof is consumed by the JoinGame itself: "join game consumed fresh FML capability proof").

beta.48 = beta.47 +
  BackhandGuardHooks   a mod list captured after the previous JoinGame counts for the next JoinGame when the type is
                       MODDED (field akashic$proofJoinSerial, helper akashic$freshProofLocked); every other method
                       unchanged (-Dakashic.backhandProxyReturn.disable=true: beta.47 behaviour)

Gates (results in reports/final-build-audit.json):
  * deterministic double build; entries: BackhandGuardHooks, mod class, manifest, mcmod.info changed, nothing added or
    removed, the rest byte-identical to beta.47; class major <= 52
  * HooksDiff: one field added, captureRemoteModMap and onJoinGamePacket changed, akashic$freshProofLocked added
  * BackhandGate on the JVM verifier, the log's event order replayed through the shipped classes: beta.47 reproduces
    the defect, beta.48 keeps MODDED/offhand on the return and never reuses the proof, the kill-switch gives beta.47
"""
import argparse
import hashlib
import json
import pathlib
import re
import struct
import subprocess
import tempfile
import zipfile

HERE = pathlib.Path(__file__).resolve().parent
ROOT = HERE.parent
REPO = ROOT.parent.parent
BASE = REPO / "releases/v1.1.0-beta.47-BaublesApiAngelicaLookup/!Akashic-ThePatcher-1.1.0-beta.47-BaublesApiAngelicaLookup.jar"
BASE_SHA = "e137855579a9950377ac24a538f744c6df9879a7a042e61e7f97860b60563c0f"
OLD = "1.1.0-beta.47"
NEW = "1.1.0-beta.48"
VERSION_TOOL = REPO / "versions/Akashic-ThePatcher-1.1.0-beta.47-BaublesApiAngelicaLookup/tools/PatchThePatcherVersion.java"

ASM9 = {
    "asm-9.7.jar": "adf46d5e34940bdf148ecdd26a9ee8eea94496a72034ff7141066b3eea5c4e9d",
    "asm-tree-9.7.jar": "62f4b3bc436045c1acb5c3ba2d8ec556ec3369093d7f5d06c747eb04b56d52b1",
    "asm-analysis-9.7.jar": "7bc6bcbc21379948a0c8c467fb0f864206e5b818f6bc0b546872f5c9f941556f",
    "asm-util-9.7.jar": "37a6414d36641973f1af104937c95d6d921b2ddb4d612c66c5a9f2b13fc14211",
}

HOOKS = "com/akashic/forbiddenbackhand/core/BackhandGuardHooks.class"
MOD = "com/akashic/thepatcher/AkashicThePatcherMod.class"
MANIFEST = "META-INF/MANIFEST.MF"
MCMOD = "mcmod.info"
EXPECTED_CHANGED = sorted([HOOKS, MOD, MANIFEST, MCMOD])
EXPECTED_DIFF = ("fields+=[4106 akashic$proofJoinSerialJ null] differing=[+4106 akashic$freshProofLocked()Z, "
                 "10 captureRemoteModMap(Ljava/util/Map;)V, 9 onJoinGamePacket(Ljava/lang/Object;Ljava/lang/Object;)V]")


def sha(data):
    return hashlib.sha256(data).hexdigest()


def major(data):
    return struct.unpack_from(">H", data, 6)[0]


def readzip(path):
    with zipfile.ZipFile(str(path)) as z:
        assert z.testzip() is None
        return {n: z.read(n) for n in z.namelist() if not n.endswith("/")}


def make_zip(content, target):
    stamp = (2026, 10, 31, 12, 0, 0)
    with zipfile.ZipFile(str(target), "w", zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        for name in sorted(content):
            info = zipfile.ZipInfo(name, stamp)
            info.create_system = 3
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o644 << 16
            z.writestr(info, content[name], compress_type=zipfile.ZIP_DEFLATED, compresslevel=9)
    with zipfile.ZipFile(str(target)) as z:
        assert z.testzip() is None


def run(cmd, expect=None):
    proc = subprocess.run([str(c) for c in cmd], stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                          timeout=1800, env={"PATH": "/usr/bin:/bin"})
    text = proc.stdout.decode("utf-8", "replace")
    text = "\n".join(l for l in text.splitlines() if not l.startswith("Picked up JAVA_TOOL_OPTIONS"))
    if proc.returncode != 0 or (expect and expect not in text):
        raise SystemExit("FAILED: %s\n%s" % (" ".join(map(str, cmd)), text[-4000:]))
    return text


def cp(*parts):
    return ":".join(str(p) for p in parts)


def asm9(args):
    return cp(*(pathlib.Path(args.libs) / j for j in ASM9))


def javac(args, *rest):
    return run([pathlib.Path(args.jdk21) / "bin/javac", "--release", "8", "-nowarn", "-encoding", "UTF-8"] + list(rest))


def build(args, work):
    tools = work / "tools"
    javac(args, "-cp", asm9(args), "-d", tools, VERSION_TOOL, HERE / "PatchBeta48Backhand.java")
    java21 = pathlib.Path(args.jdk21) / "bin/java"
    run([java21, "-Xverify:all", "-cp", cp(tools, asm9(args)), "com.akashic.compatlab.tools.PatchThePatcherVersion",
         BASE, OLD, NEW, work / "mod/AkashicThePatcherMod.class"], "THEPATCHER_VERSION_PATCH PASS")
    run([java21, "-Xverify:all", "-cp", cp(tools, asm9(args)), "com.akashic.compatlab.tools.PatchBeta48Backhand",
         BASE, work / "hooks/BackhandGuardHooks.class"], "BETA48_BACKHAND_PATCH PASS")

    content = readzip(BASE)
    content[HOOKS] = (work / "hooks/BackhandGuardHooks.class").read_bytes()
    content[MOD] = (work / "mod/AkashicThePatcherMod.class").read_bytes()
    for name in (MANIFEST, MCMOD):
        text = content[name].decode("utf-8")
        assert text.count(OLD) == 1, name
        content[name] = text.replace(OLD, NEW).encode("utf-8")
    out = work / "out.jar"
    make_zip(content, out)
    return out.read_bytes()


def gate_lines(text):
    return [l.strip() for l in text.splitlines() if l.strip().startswith(("GATE", "BACKHAND_GATE"))]


def gates(args, jar, work):
    report = {}
    libs = pathlib.Path(args.libs)
    for name, expected in ASM9.items():
        assert sha((libs / name).read_bytes()) == expected, "unexpected " + name
    report["third_party_sha256"] = dict(ASM9)

    base, new = readzip(BASE), readzip(jar)
    assert set(base) == set(new), sorted(set(base) ^ set(new))
    changed = sorted(n for n in base if base[n] != new[n])
    assert changed == EXPECTED_CHANGED, changed
    for name in changed:
        if name.endswith(".class"):
            assert major(new[name]) <= 52, name
    report.update(entries=len(new), changed=changed, added=[], inherited_identical=len(base) - len(changed))

    test = work / "test"
    javac(args, "-cp", asm9(args), "-d", test, ROOT / "test/HooksDiff.java", ROOT / "test/BackhandGate.java")
    java8 = pathlib.Path(args.jdk8) / "bin/java"
    old_file, new_file = work / "old.class", work / "new.class"
    old_file.write_bytes(base[HOOKS])
    new_file.write_bytes(new[HOOKS])
    text = run([java8, "-cp", cp(test, asm9(args)), "HooksDiff", old_file, new_file], "HOOKS_DIFF")
    assert EXPECTED_DIFF in text, text
    report["hooks_diff." + HOOKS] = text.strip()

    cases = [("beta47", BASE, ["bug"]), ("beta48", jar, ["fixed"]), ("beta48.disabled", jar, ["bug", "kill"])]
    for label, version_jar, mode in cases:
        text = run([java8, "-Xverify:all", "-cp", test, "BackhandGate", version_jar] + mode,
                   "BACKHAND_GATE PASS expect=" + mode[0])
        report["backhand_gate." + label] = gate_lines(text)
    return report


def main():
    parser = argparse.ArgumentParser()
    for name in ("--jdk8", "--jdk21", "--libs", "--out"):
        parser.add_argument(name, required=True)
    parser.add_argument("--report", default=str(ROOT / "reports/final-build-audit.json"))
    args = parser.parse_args()

    assert sha(BASE.read_bytes()) == BASE_SHA, "unexpected beta.47 base"
    builds = []
    for _ in range(2):
        with tempfile.TemporaryDirectory(prefix="beta48-") as tmp:
            builds.append(build(args, pathlib.Path(tmp)))
    assert builds[0] == builds[1], "non-deterministic build"
    out = pathlib.Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_bytes(builds[0])
    with tempfile.TemporaryDirectory(prefix="beta48-gates-") as tmp:
        report = gates(args, out.resolve(), pathlib.Path(tmp))
    report = dict({
        "version": NEW,
        "base": {"path": str(BASE.relative_to(REPO)), "sha256": BASE_SHA},
        "output": {"name": out.name, "sha256": sha(builds[0]), "bytes": len(builds[0])},
        "deterministic_rebuild": "PASS",
    }, **report)
    pathlib.Path(args.report).parent.mkdir(parents=True, exist_ok=True)
    pathlib.Path(args.report).write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print("BETA48_BUILD PASS sha256=%s bytes=%d" % (sha(builds[0]), len(builds[0])))


if __name__ == "__main__":
    main()
