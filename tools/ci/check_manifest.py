"""Check every file listed in data/manifest.tsv (path, source, bytes, sha256) against the working tree.

    python3 tools/ci/check_manifest.py

Paths moved to a shorter directory are resolved through data/relocated.tsv when it exists.
"""
import csv
import hashlib
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]


def main():
    relocated = {}
    reloc = ROOT / "data/relocated.tsv"
    if reloc.exists():
        with reloc.open(encoding="utf-8") as f:
            for row in csv.reader(f, delimiter="\t"):
                if len(row) >= 2 and not row[0].startswith("#"):
                    relocated[row[0]] = row[1]
    bad, checked = [], 0
    with (ROOT / "data/manifest.tsv").open(encoding="utf-8") as f:
        rows = list(csv.reader(f, delimiter="\t", quoting=csv.QUOTE_NONE))
    for row in rows[1:]:
        if not row:
            continue
        path, size, digest = row[0], int(row[2]), row[3]
        target = ROOT / relocated.get(path, path)
        if not target.is_file():
            bad.append("missing " + path)
            continue
        data = target.read_bytes()
        checked += 1
        if len(data) != size or hashlib.sha256(data).hexdigest() != digest:
            bad.append("changed " + path)
    for line in bad[:50]:
        print(line)
    print("MANIFEST %s checked=%d problems=%d" % ("PASS" if not bad else "FAIL", checked, len(bad)))
    sys.exit(1 if bad else 0)


if __name__ == "__main__":
    main()
