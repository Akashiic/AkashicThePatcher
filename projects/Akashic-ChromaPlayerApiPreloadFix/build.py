"""Build Akashic-ChromaPlayerApiPreloadFix-<version>.jar (deterministic: fixed entry order and timestamps).

    python3 build.py --javac <javac> --cp <forge-universal.jar:launchwrapper.jar:asm-all.jar:log4j-api.jar> --out <jar>

Compile-time inputs are Forge 10.13.4.1614 universal, launchwrapper 1.12, asm-all 5.0.3 and log4j-api 2.0-beta9
(the headless client setup in versions/*beta.39*/tools/headless-client/setup.py downloads all of them).
"""
import argparse, hashlib, os, pathlib, subprocess, tempfile, zipfile

VERSION = "1.0.0"
HERE = pathlib.Path(__file__).resolve().parent
EPOCH = (2026, 10, 26, 0, 0, 0)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--javac", required=True)
    ap.add_argument("--cp", required=True)
    ap.add_argument("--out", required=True)
    a = ap.parse_args()
    sources = sorted(str(p) for p in (HERE / "src/main/java").rglob("*.java"))
    with tempfile.TemporaryDirectory() as tmp:
        release = ["--release", "8"] if b"version \"1.8" not in subprocess.run(
            [a.javac, "-version"], capture_output=True).stderr else ["-source", "8", "-target", "8"]
        subprocess.run([a.javac, *release, "-nowarn", "-encoding", "UTF-8", "-g:source,lines",
                        "-cp", a.cp, "-d", tmp, *sources], check=True)
        entries = {"META-INF/MANIFEST.MF": (HERE / "src/main/resources/MANIFEST.MF").read_bytes()}
        for p in sorted(pathlib.Path(tmp).rglob("*.class")):
            entries[p.relative_to(tmp).as_posix()] = p.read_bytes()
        with zipfile.ZipFile(a.out, "w", zipfile.ZIP_DEFLATED) as z:
            for name, data in entries.items():
                info = zipfile.ZipInfo(name, EPOCH)
                info.compress_type = zipfile.ZIP_DEFLATED
                info.external_attr = 0o644 << 16
                z.writestr(info, data)
    print("BUILT", a.out, hashlib.sha256(pathlib.Path(a.out).read_bytes()).hexdigest())


if __name__ == "__main__":
    main()
