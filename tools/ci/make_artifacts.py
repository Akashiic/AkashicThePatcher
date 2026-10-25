"""Build the downloadable artifacts of the newest build in data/lineage.json.

    python3 tools/ci/make_artifacts.py --libs <dir with asm-9.7/asm-commons-9.7 and the MCP zip> \
        --java <jdk home with javac> --out <dir>

Writes four files into <out> (no leading "!", like the GitHub Release assets):
  Akashic-ThePatcher-<version>.jar          the release JAR, byte-identical to releases/<tag>/
  Akashic-ThePatcher-<version>-dev.jar      the same with Minecraft members mapped SRG -> MCP (stable_12)
  Akashic-ThePatcher-<version>-sources.jar  the newest recorded source of each class + SOURCES.md
  SHA256SUMS.txt                            SHA-256 of the three JARs

Sources: the core bundle has never had complete sources (docs/ARCHITECTURE.md). For every class in the JAR,
data/lineage.json tells which build last changed it; if that build's versions/ folder has its .java, the file
is "exact" (the source stored with that build). Otherwise the newest .java found anywhere in versions/ or projects/ is
included as "recorded" (an older or decompiled source, not proven identical). Classes without any source are
listed as binary-only. SOURCES.md inside the JAR has the full table.
"""
import argparse
import hashlib
import json
import pathlib
import re
import subprocess
import tempfile
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[2]
STAMP = (2025, 10, 25, 12, 0, 0)
VERSION_RE = re.compile(r"Akashic-ThePatcher-(\d+\.\d+\.\d+-beta\.[\d.]+)")


def version_key(version):
    nums = re.findall(r"\d+", version)
    return tuple(int(n) for n in nums)


def class_of(java_file):
    text = java_file.read_text(encoding="utf-8", errors="replace")
    package = re.search(r"^\s*package\s+([\w.]+)\s*;", text, re.M)
    if not package:
        return None
    return package.group(1).replace(".", "/") + "/" + java_file.stem + ".class"


def folder_version(path):
    match = VERSION_RE.search(str(path))
    return match.group(1) if match else None


def write_zip(target, files):
    with zipfile.ZipFile(target, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        for name in sorted(files):
            info = zipfile.ZipInfo(name, STAMP)
            info.create_system = 3
            info.external_attr = 0o644 << 16
            info.compress_type = zipfile.ZIP_DEFLATED
            z.writestr(info, files[name])


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--libs", required=True)
    parser.add_argument("--java", required=True)
    parser.add_argument("--out", required=True)
    args = parser.parse_args()
    libs, out = pathlib.Path(args.libs), pathlib.Path(args.out)
    out.mkdir(parents=True, exist_ok=True)

    lineage = json.loads((ROOT / "data/lineage.json").read_text(encoding="utf-8"))
    current = [e for e in lineage if e.get("tag") and e.get("jar")][-1]
    version = current["id"]
    release = ROOT / "releases" / current["tag"] / current["jar"]
    data = release.read_bytes()
    assert hashlib.sha256(data).hexdigest() == current["sha256"], "release JAR does not match lineage"
    stem = current["jar"].lstrip("!")[:-len(".jar")]

    # 1. the release JAR
    (out / (stem + ".jar")).write_bytes(data)

    # 2. dev JAR
    with tempfile.TemporaryDirectory() as tmp:
        cp = ":".join(str(libs / j) for j in ("asm-9.7.jar", "asm-tree-9.7.jar", "asm-commons-9.7.jar"))
        java = pathlib.Path(args.java) / "bin"
        subprocess.run([str(java / "javac"), "-nowarn", "--release", "8", "-cp", cp, "-d", tmp,
                        str(ROOT / "tools/ci/DevJar.java")], check=True)
        result = subprocess.run([str(java / "java"), "-Duser.timezone=UTC", "-cp", cp + ":" + tmp, "DevJar",
                                 str(release), str(libs / "mcp_stable-12-1.7.10.zip"), str(out / (stem + "-dev.jar"))],
                                check=True, stdout=subprocess.PIPE, text=True)
        print(result.stdout.strip())

    # 3. sources JAR
    with zipfile.ZipFile(release) as z:
        classes = sorted(n for n in z.namelist() if n.endswith(".class") and "$" not in n)
    origin = {}
    for entry in lineage:
        if version_key(entry["id"]) > version_key(version):
            continue
        for name in entry.get("changed", []) + entry.get("added", []):
            origin[name] = entry["id"]
    candidates = {}
    for java_file in list((ROOT / "versions").rglob("*.java")) + list((ROOT / "projects").rglob("*.java")):
        parts = set(java_file.relative_to(ROOT).parts)
        if parts & {"test", "tests", "tools", "runs", "stubs"} or "/src/test/" in str(java_file):
            continue
        name = class_of(java_file)
        if name:
            candidates.setdefault(name, []).append(java_file)
    files, rows, counts = {}, [], {"exact": 0, "recorded": 0, "binary-only": 0}
    for name in classes:
        built_by = origin.get(name)
        found = candidates.get(name, [])
        exact = [f for f in found if built_by and folder_version(f) == built_by and "recovered-source" not in str(f)]
        if exact:
            chosen, status = exact[0], "exact"
        elif found:
            chosen = max(found, key=lambda f: (version_key(folder_version(f) or "0"), str(f)))
            status = "recorded"
        else:
            chosen, status = None, "binary-only"
        counts[status] += 1
        if chosen is not None:
            files[name[:-len(".class")] + ".java"] = chosen.read_bytes()
        rows.append("| `%s` | %s | %s | %s |" % (name[:-6].replace("/", "."), built_by or "?", status,
                                                  chosen.relative_to(ROOT).as_posix() if chosen else "-"))
    files["SOURCES.md"] = ("# Sources of %s\n\n"
                           "* **exact**: the source stored with the build that last changed this class (that build's versions/ folder).\n"
                           "* **recorded**: the newest source in the repository (older build, project snapshot or "
                           "decompilation); not proven identical to the shipped class.\n"
                           "* **binary-only**: no source exists (see docs/OPEN-ISSUES.md).\n\n"
                           "exact=%d recorded=%d binary-only=%d (top-level classes; inner classes share their file)\n\n"
                           "| Class | Last changed by | Status | Source in the repository |\n|---|---|---|---|\n%s\n"
                           % (version, counts["exact"], counts["recorded"], counts["binary-only"], "\n".join(rows))
                           ).encode("utf-8")
    write_zip(out / (stem + "-sources.jar"), files)
    print("SOURCES_JAR exact=%d recorded=%d binary-only=%d" % (counts["exact"], counts["recorded"], counts["binary-only"]))

    # 4. checksums
    sums = []
    for suffix in (".jar", "-dev.jar", "-sources.jar"):
        f = out / (stem + suffix)
        sums.append("%s  %s" % (hashlib.sha256(f.read_bytes()).hexdigest(), f.name))
    (out / "SHA256SUMS.txt").write_text("\n".join(sums) + "\n", encoding="utf-8")
    print("ARTIFACTS OK " + version)
    for line in sums:
        print("  " + line)


if __name__ == "__main__":
    main()
