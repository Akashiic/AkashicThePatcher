"""Run the ClickGuard harness against the real InventoryTweaks / Avaritiaddons JARs.

    python3 test/run.py --guard dist/Akashic-ContainerClickGuard-1.0.0-serveronly.jar --libs <dir> \
        --invtweaks <InventoryTweaks-1.59-unofficial-asie-1.jar> --avaritiaddons <Avaritiaddons-1.9.5-GTNH.jar>

<dir> holds forge-1.7.10-10.13.4.1614-1.7.10-universal.jar (as forge-universal.jar), netty-all-4.0.10.Final.jar and
what tools/ci/fetch_build_libs.sh downloads (launchwrapper, asm-all 5.0.3, log4j). Uses `javac` and `java` on PATH.
"""
import argparse, os, pathlib, subprocess, tempfile

HERE = pathlib.Path(__file__).resolve().parent


def main():
    ap = argparse.ArgumentParser()
    for name in ("--guard", "--libs", "--invtweaks", "--avaritiaddons"):
        ap.add_argument(name, required=True)
    a = ap.parse_args()
    libs = [os.path.abspath(os.path.join(a.libs, n)) for n in ("forge-universal.jar", "netty-all-4.0.10.Final.jar",
                                              "launchwrapper-1.12.jar", "asm-all-5.0.3.jar",
                                              "log4j-api.jar", "log4j-core.jar")]
    with tempfile.TemporaryDirectory() as tmp:
        stubs, classes = os.path.join(tmp, "stubs"), os.path.join(tmp, "classes")
        subprocess.run(["javac", "--release", "8", "-nowarn", "-cp", os.pathsep.join(libs), "-d", stubs,
                        *sorted(str(p) for p in (HERE / "harness-stubs").rglob("*.java"))], check=True)
        cp = os.pathsep.join([stubs, os.path.abspath(a.guard), *libs])
        subprocess.run(["javac", "--release", "8", "-nowarn", "-cp", cp, "-d", classes,
                        str(HERE / "ClickGuardHarness.java")], check=True)
        r = subprocess.run(["java", "-cp", classes + os.pathsep + cp, "ClickGuardHarness",
                            os.path.abspath(a.invtweaks), os.path.abspath(a.avaritiaddons)],
                           cwd=tmp)  # Forge's log4j2.xml writes logs/ into the working directory
    raise SystemExit(r.returncode)


if __name__ == "__main__":
    main()
