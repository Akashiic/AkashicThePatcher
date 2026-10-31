"""Build Akashic-ElnEndlessIdsFix-<version>.jar (deterministic: fixed entry order and timestamps).

    python3 build.py --javac <javac> --libs <dir> --out <jar>

<dir> must hold launchwrapper-1.12.jar, asm-all-5.0.3.jar and log4j-api.jar (2.0-beta9), as downloaded by
tools/ci/fetch_build_libs.sh. Minecraft and Forge types come from the stubs in src/stubs (SRG names, as in the
running game); the stubs are compiled but never packaged.
"""
import argparse, hashlib, os, pathlib, subprocess, tempfile, zipfile

VERSION = "1.0.0"
HERE = pathlib.Path(__file__).resolve().parent
EPOCH = (2026, 10, 31, 0, 0, 0)
LIBS = ["launchwrapper-1.12.jar", "asm-all-5.0.3.jar", "log4j-api.jar"]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--javac", required=True)
    ap.add_argument("--libs", required=True)
    ap.add_argument("--out", required=True)
    a = ap.parse_args()
    cp = os.pathsep.join(str(pathlib.Path(a.libs) / n) for n in LIBS)
    stubs = sorted(str(p) for p in (HERE / "src/stubs/java").rglob("*.java"))
    sources = sorted(str(p) for p in (HERE / "src/main/java").rglob("*.java"))
    with tempfile.TemporaryDirectory() as tmp:
        stub_out, out = os.path.join(tmp, "stubs"), os.path.join(tmp, "classes")
        release = ["--release", "8"] if b"version \"1.8" not in subprocess.run(
            [a.javac, "-version"], capture_output=True).stderr else ["-source", "8", "-target", "8"]
        javac = [a.javac, *release, "-nowarn", "-encoding", "UTF-8", "-g:source,lines"]
        subprocess.run([*javac, "-d", stub_out, *stubs], check=True)
        subprocess.run([*javac, "-cp", cp + os.pathsep + stub_out, "-d", out, *sources], check=True)
        entries = {"META-INF/MANIFEST.MF": (HERE / "src/main/resources/MANIFEST.MF").read_bytes()}
        for p in sorted(pathlib.Path(out).rglob("*.class")):
            entries[p.relative_to(out).as_posix()] = p.read_bytes()
        with zipfile.ZipFile(a.out, "w", zipfile.ZIP_DEFLATED) as z:
            for name, data in entries.items():
                info = zipfile.ZipInfo(name, EPOCH)
                info.compress_type = zipfile.ZIP_DEFLATED
                info.external_attr = 0o644 << 16
                z.writestr(info, data)
    print("BUILT", a.out, hashlib.sha256(pathlib.Path(a.out).read_bytes()).hexdigest())


if __name__ == "__main__":
    main()
