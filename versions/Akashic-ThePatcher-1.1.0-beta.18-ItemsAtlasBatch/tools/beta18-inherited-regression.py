from __future__ import print_function
import hashlib
import json
import pathlib
import shutil
import subprocess

ROOT = pathlib.Path(r"C:\Users\User\Desktop\Codex\Akashic-ThePatcher-1.1.0-beta.18-ItemsAtlasBatch")
LAB = pathlib.Path(r"C:\Users\User\Desktop\Codex\Akashic-Compatibility-Lab")
JDK = pathlib.Path(r"C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot\bin")
TECH = pathlib.Path(r"C:\Users\User\AppData\Roaming\.technic")

CAND = ROOT / "releases/!Akashic-ThePatcher-1.1.0-beta.18-ItemsAtlasBatch.jar"
BASE = pathlib.Path(r"C:\Users\User\Desktop\Codex\Akashic-ThePatcher-1.1.0-beta.11-Async-Forestry\rollback-pre-beta11-20260924-030822\!Akashic-ThePatcher-1.1.0-beta.8-BASELINE.jar")
DEOBF = LAB / "runs/client-transform-beta4-1/client-1.7.10-deobf-lab.jar"
LW = TECH / "libraries/net/minecraft/launchwrapper/1.12/launchwrapper-1.12.jar"
ASM5 = TECH / "libraries/org/ow2/asm/asm-all/5.0.3/asm-all-5.0.3.jar"
ASM9 = pathlib.Path(r"C:\Users\User\.gradle\caches\modules-2\files-2.1\org.ow2.asm\asm\9.8\dc19ecb3f7889b7860697215cae99c0f9b6f6b4b\asm-9.8.jar")
TREE9 = pathlib.Path(r"C:\Users\User\.gradle\caches\modules-2\files-2.1\org.ow2.asm\asm-tree\9.8\18419ca5b77a2f81097c741e7872e6ab8d2f40d\asm-tree-9.8.jar")
FML = TECH / "modpacks/forbidden-series/bin/modpack.jar.bak-startup-20260923-234134"
HODGE = TECH / "modpacks/forbidden-series/mods/hodgepodge-2.7.189.jar"
UNIMIX = TECH / "modpacks/forbidden-series/mods/+unimixins-all-1.7.10-0.3.1.jar"
GTNH = LAB / "references/gtnhlib-0.11.51.jar"
ICU = TECH / "libraries/com/ibm/icu/icu4j-core-mojang/51.2/icu4j-core-mojang-51.2.jar"
LOGAPI = next((TECH / "libraries/org/apache/logging/log4j/log4j-api/2.0-beta9-fixed").glob("*.jar"))
LOGCORE = next((TECH / "libraries/org/apache/logging/log4j/log4j-core/2.0-beta9-fixed").glob("*.jar"))

PINS = {
    CAND: "12f00211e9f8cd66cad9d9a90da7f809d4e1987a72d31461dfbe42edf5169262",
    BASE: "010c3b9f4fdd20528a772904f13238aa836c8af13759aebfe7184ed07beed44b",
    DEOBF: "8594230a8e4d72689a6ffc97a398639e4ace7a702aa29d97e5aca8bbccdf0e93",
    LW: "57f402b626d16cc2705bf2a37add7adbb074f0ca3b3102fa6e23aa303dae682f",
    ASM5: "09d44e6adddaab9fd89ea3164c439f71c755769fa8d3a940352ab3d2003b98ff",
    ASM9: "876eab6a83daecad5ca67eb9fcabb063c97b5aeb8cf1fca7a989ecde17522051",
    TREE9: "14b7880cb7c85eed101e2710432fc3ffb83275532a6a894dc4c4095d49ad59f1",
    FML: "00d1ca02192c7efb87da95552ebf247021f1e1d642f86d0a03076314def11529",
    HODGE: "7d7650c761e312df0d9831757296621d40bd5a9030f73cf270e4bc0b219666e2",
    UNIMIX: "ad0ea4f92daf7bf7ec5c10e16258425e47929126954aea5d80df69ce26cd7318",
    GTNH: "9d31e3a5011b39a111536a43f1c2bf74e83a037f5789cf66da3d482e2686adf7",
    ICU: "147592c5eb8e11fc8394125954f877acf25918bae13aa210c2825aefc3030ed8",
    LOGAPI: "6370787096a14772972457e929aa07ed1b2e0ee83240396ce5165f5feeef377c",
    LOGCORE: "d08db205b0f5712cf3ae79766672e2daf7a9cf13304ea4ccb9fe7f23c92a200b",
}

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def cp(*items):
    return ";".join(str(x) for x in items)

def javac(out, classpath, *sources):
    out.mkdir(parents=True, exist_ok=True)
    return [
        str(JDK / "javac.exe"),
        "--release", "8",
        "-encoding", "UTF-8",
        "-Xlint:all,-options",
        "-Werror",
        "-cp", classpath,
        "-d", str(out),
    ] + [str(x) for x in sources]

def java(classpath, main, *args):
    return [
        str(JDK / "java.exe"),
        "-Xverify:all",
        "-Xmx256m",
        "-cp", classpath,
        main,
    ] + list(args)

def checked(label, argv, proof=None):
    result = subprocess.run(
        argv,
        cwd=LAB,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        timeout=180,
    )
    text = result.stdout.decode("utf-8", errors="replace")
    if result.returncode != 0 or (proof and proof not in text):
        raise RuntimeError(label + " failed:\n" + text[-3500:])
    print(label + " PASS", flush=True)
    return {
        "label": label,
        "proof": proof,
        "outputSha256": hashlib.sha256(result.stdout).hexdigest(),
        "outputTail": text.splitlines()[-1] if text.splitlines() else "",
    }

def verify():
    for path, expected in PINS.items():
        if not path.is_file():
            raise FileNotFoundError(path)
        got = sha(path)
        if got != expected:
            raise ValueError(
                "Pinned input changed: %s expected=%s got=%s"
                % (path, expected, got)
            )
    print("BETA18_REGRESSION_INPUT_PINS PASS count=" + str(len(PINS)), flush=True)

def main():
    verify()

    work = ROOT / "runs/beta18-regression"
    if work.exists():
        shutil.rmtree(work)
    work.mkdir(parents=True)
    records = []

    tool = work / "tools"
    records.append(
        checked(
            "compile_transform_tools",
            javac(
                tool,
                cp(CAND, LW, ASM9, TREE9, ASM5),
                LAB / "tools/java/ApplyInstalledRgbTextTransformer.java",
                LAB / "tools/java/ApplyHodgepodgeFontRendererAT.java",
            ),
        )
    )

    font = work / "client-font-beta8.jar"
    records.append(
        checked(
            "transform_font_beta8",
            [
                str(JDK / "java.exe"),
                "-cp", cp(tool, CAND, LW, ASM5, FML, LOGAPI, LOGCORE),
                "com.akashic.compatlab.tools.ApplyInstalledRgbTextTransformer",
                str(DEOBF), str(font),
            ],
            "RGB_TEXT_TRANSFORMER_APPLY PASS",
        )
    )

    at = work / "client-font-beta8-at.jar"
    records.append(
        checked(
            "hodgepodge_at_beta8",
            [
                str(JDK / "java.exe"),
                "-cp", cp(tool, ASM5),
                "com.akashic.compatlab.tools.ApplyHodgepodgeFontRendererAT",
                str(font), str(at),
            ],
            "HODGEPODGE_FONT_AT PASS",
        )
    )

    hodge = work / "hodge-tests"
    hsrc = LAB / "tests/NativeHodgepodgeRgbWrapIntegration.java"
    hexsrc = LAB / "tests/NativeHodgepodgeExtendedWrapIntegration.java"
    records.append(
        checked(
            "compile_hodge_tests",
            javac(hodge, str(CAND), hsrc, hexsrc),
        )
    )
    records.append(
        checked(
            "beta8_hodgepodge_rgb",
            java(
                cp(hodge, at, CAND, HODGE, UNIMIX, GTNH, ICU),
                "com.akashic.compatlab.client.NativeHodgepodgeRgbWrapIntegration",
                "candidate",
            ),
            "HODGEPODGE_RGB_WRAP CANDIDATE_PASS cases=768 rgbFailures=0 legacy=6",
        )
    )
    records.append(
        checked(
            "beta8_hodgepodge_extended",
            java(
                cp(hodge, at, CAND, HODGE, UNIMIX, GTNH, ICU),
                "com.akashic.compatlab.client.NativeHodgepodgeExtendedWrapIntegration",
            ),
            "HODGEPODGE_EXTENDED_WRAP PASS cases=10028 checks=10029",
        )
    )

    fmt = work / "format-tests"
    fsources = [
        LAB / "tests/beta5format/com/akashic/thepatcher/module/rgb/NativeBeta5FormatContinuationTest.java",
        LAB / "tests/beta5format/com/akashic/thepatcher/module/rgb/NativeBeta5ExtendedFormatTest.java",
    ]
    records.append(
        checked(
            "compile_format_tests",
            javac(fmt, cp(font, CAND), *fsources),
        )
    )
    records.append(
        checked(
            "format_continuation",
            java(
                cp(fmt, font, CAND, ICU),
                "com.akashic.thepatcher.module.rgb.NativeBeta5FormatContinuationTest",
            ),
            "BETA5_FORMAT_CONTINUATION PASS cases=3072 checks=3073",
        )
    )
    records.append(
        checked(
            "extended_format",
            java(
                cp(fmt, font, CAND, ICU),
                "com.akashic.thepatcher.module.rgb.NativeBeta5ExtendedFormatTest",
            ),
            "BETA5_EXTENDED_FORMAT PASS cases=10028 checks=10032",
        )
    )

    legacy = work / "legacy-tests"
    lsrc = LAB / "tests/beta5format/com/akashic/thepatcher/module/rgb/NativeBeta5LegacyExactParity.java"
    csrc = LAB / "tests/NativeBeta5FormatBridgeCompatibility.java"
    records.append(
        checked(
            "compile_legacy_tests",
            javac(legacy, cp(DEOBF, CAND), lsrc, csrc),
        )
    )
    records.append(
        checked(
            "legacy_exact",
            java(
                cp(legacy, DEOBF, CAND, ICU),
                "com.akashic.thepatcher.module.rgb.NativeBeta5LegacyExactParity",
            ),
            "BETA5_LEGACY_EXACT_PARITY PASS cases=20256 checks=20256",
        )
    )
    records.append(
        checked(
            "bridge_compat",
            java(
                cp(legacy, DEOBF, CAND, ICU),
                "com.akashic.compatlab.client.NativeBeta5FormatBridgeCompatibility",
            ),
            "BETA5_FORMAT_BRIDGE_COMPAT PASS legacyCases=5182 extendedCases=3003 checks=8191",
        )
    )

    transformed = work / "transformed-tests"
    tsrc = LAB / "tests/NativeBeta5TransformedFormat.java"
    records.append(
        checked(
            "compile_transformed_format",
            javac(transformed, cp(font, CAND), tsrc),
        )
    )
    records.append(
        checked(
            "transformed_format",
            java(
                cp(transformed, font, CAND, ICU),
                "com.akashic.compatlab.client.NativeBeta5TransformedFormat",
            ),
            "BETA5_TRANSFORMED_FORMAT PASS cases=4004 checks=4005",
        )
    )

    trim = work / "trim-tests"
    trim_sources = sorted((LAB / "tests/chattrim").rglob("*.java"))
    records.append(
        checked(
            "compile_rgb_trim",
            javac(trim, str(CAND), *trim_sources),
        )
    )
    records.append(
        checked(
            "rgb_trim",
            java(cp(trim, CAND), "NativeRgbTrimBridgeTest"),
            "RGB_TRIM_BRIDGE PASS cases=16128 checks=327169",
        )
    )

    if sha(CAND) != PINS[CAND]:
        raise RuntimeError("Candidate changed during regression")
    if sha(GTNH) != PINS[GTNH]:
        raise RuntimeError("GTNHLib reference changed during regression")

    build = json.loads(
        (ROOT / "reports/final-build-audit.json").read_text(encoding="utf-8")
    )
    if build.get("baseBeta17Sha256") != "18177c88421d675da07ab6dc7ed2b5d1437691d95b1b88eeec4c684814238cc7":
        raise AssertionError("Build report beta.17 baseline mismatch")
    if build.get("changedClasses") != [
        "com/akashic/thepatcher/AkashicThePatcherMod.class",
    ]:
        raise AssertionError("Unexpected changed class set")
    if build.get("addedClasses") != sorted([
        "dev/akashic/asynciconcompat/mixins/MixinTextureMapItemsUploadBatch.class",
        "dev/akashic/asynciconcompat/mixins/MixinTextureUtilItemsUploadBatch.class",
        "dev/akashic/asynciconcompat/upload/ItemsAtlasUploadBatcher.class",
    ]):
        raise AssertionError("Unexpected added class set")


    report = {
        "schema": 1,
        "candidate": CAND.name,
        "candidateSha256": sha(CAND),
        "baselineBeta8Sha256": sha(BASE),
        "gtnhlib": "0.11.51",
        "gtnhlibSha256": sha(GTNH),
        "gateCount": len(records),
        "allPassed": True,
        "gates": records,
        "coverage": {
            "hodgepodgeRgbCases": 768,
            "hodgepodgeExtendedCases": 10028,
            "formatContinuationCases": 3072,
            "extendedFormatCases": 10028,
            "legacyExactCases": 20256,
            "bridgeLegacyCases": 5182,
            "bridgeExtendedCases": 3003,
            "transformedFormatCases": 4004,
            "trimCases": 16128,
            "trimChecks": 327169,
        },
        "binaryAudit": {
            "changedClasses": build["changedClasses"],
            "addedClasses": build["addedClasses"],
        },
        "productionInstalled": False,
        "productionApproved": False,
        "scope": "Inherited beta.8 RGB/font regression suite against beta.18 ItemsAtlasBatch candidate.",
    }

    out = ROOT / "reports/final-inherited-regression-gate.json"
    out.write_text(
        json.dumps(report, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )

    print(
        "THEPATCHER_BETA18_REGRESSION PASS gates=%d sha256=%s"
        % (len(records), sha(CAND)),
        flush=True,
    )

if __name__ == "__main__":
    main()