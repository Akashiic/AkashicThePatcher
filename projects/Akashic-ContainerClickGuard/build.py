"""Build Akashic-ContainerClickGuard-<version>-serveronly.jar (deterministic: fixed entry order and timestamps).

    python3 build.py --javac <javac> --cp <forge-universal.jar:launchwrapper.jar:asm-all.jar:log4j-api.jar> --out <jar>

Compile-time inputs are Forge 10.13.4.1614 universal, launchwrapper 1.12, asm-all 5.0.3 and log4j-api 2.0-beta9
(the last three come from tools/ci/fetch_build_libs.sh). Minecraft classes (and FML's MessageContext, whose
universal-JAR signature uses obfuscated names) come from stubs/ with run-time names; they are compiled first and
never packaged.
"""
import argparse, hashlib, os, pathlib, subprocess, tempfile, zipfile

VERSION = "1.0.0"
HERE = pathlib.Path(__file__).resolve().parent
EPOCH = (2026, 10, 27, 0, 0, 0)


def javac(exe, cp, out, sources):
    release = ["--release", "8"] if b"version \"1.8" not in subprocess.run(
        [exe, "-version"], capture_output=True).stderr else ["-source", "8", "-target", "8"]
    subprocess.run([exe, *release, "-nowarn", "-encoding", "UTF-8", "-g:source,lines",
                    "-cp", cp, "-d", out, *sources], check=True)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--javac", required=True)
    ap.add_argument("--cp", required=True)
    ap.add_argument("--out", required=True)
    a = ap.parse_args()
    with tempfile.TemporaryDirectory() as tmp:
        stubs, classes = os.path.join(tmp, "stubs"), os.path.join(tmp, "classes")
        javac(a.javac, a.cp, stubs, sorted(str(p) for p in (HERE / "stubs").rglob("*.java")))
        javac(a.javac, stubs + os.pathsep + a.cp, classes,  # stubs first: they shadow FML's obfuscated MessageContext
              sorted(str(p) for p in (HERE / "src/main/java").rglob("*.java")))
        entries = {"META-INF/MANIFEST.MF": (HERE / "src/main/resources/MANIFEST.MF").read_bytes()}
        for p in sorted(pathlib.Path(classes).rglob("*.class")):
            entries[p.relative_to(classes).as_posix()] = p.read_bytes()
        with zipfile.ZipFile(a.out, "w", zipfile.ZIP_DEFLATED) as z:
            for name, data in entries.items():
                info = zipfile.ZipInfo(name, EPOCH)
                info.compress_type = zipfile.ZIP_DEFLATED
                info.external_attr = 0o644 << 16
                z.writestr(info, data)
    print("BUILT", a.out, hashlib.sha256(pathlib.Path(a.out).read_bytes()).hexdigest())


if __name__ == "__main__":
    main()
