"""Publish a GitHub Release for every releases/<tag>/ folder that does not have one yet.

    python3 tools/ci/publish_releases.py [--dry-run]

Run by .github/workflows/release.yml (GH_TOKEN = the workflow token, contents: write); `gh` must be on PATH.
For each build in data/lineage.json order whose folder exists and has no release:
  * the tag points at the commit that added the JAR (so the history stays chronological);
  * the body is the build's table (hash, size, base) plus its docs/CHANGELOG.md section;
  * the asset is the JAR, uploaded without the leading "!" (GitHub would rename it anyway);
  * builds whose lineage note starts with "do not install" are published as pre-releases;
  * the newest build that is not a pre-release is marked Latest.
Existing releases are never edited or deleted.
"""
import argparse
import json
import os
import pathlib
import re
import shutil
import subprocess
import tempfile

ROOT = pathlib.Path(__file__).resolve().parents[2]


def sh(*cmd, check=True):
    proc = subprocess.run(cmd, cwd=ROOT, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    if check and proc.returncode != 0:
        raise SystemExit("FAILED: %s\n%s%s" % (" ".join(cmd), proc.stdout, proc.stderr))
    return proc


def changelog_section(version):
    text = (ROOT / "docs/CHANGELOG.md").read_text(encoding="utf-8")
    match = re.search(r"^## %s - .*?(?=^## |\Z)" % re.escape(version), text, re.S | re.M)
    return match.group(0).strip() if match else ""


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()
    repo = os.environ.get("GITHUB_REPOSITORY") or "Akashiic/AkashicThePatcher"

    lineage = json.loads((ROOT / "data/lineage.json").read_text(encoding="utf-8"))
    sums = dict(reversed(line.split("  ", 1)) for line in (ROOT / "releases/SHA256SUMS.txt").read_text().splitlines() if line)
    by_sha = {entry["sha256"]: entry for entry in lineage}
    pending = []
    for entry in lineage:
        tag, jar = entry.get("tag"), entry.get("jar")
        if not tag or not jar or not (ROOT / "releases" / tag / jar).is_file():
            continue
        if sh("gh", "api", "repos/%s/releases/tags/%s" % (repo, tag), check=False).returncode == 0:
            continue
        pending.append(entry)
    if not pending:
        print("PUBLISH_RELEASES nothing to publish")
        return

    latest = None
    for entry in pending:
        if not entry.get("note", "").lower().startswith("do not install"):
            latest = entry["tag"]

    for entry in pending:
        tag, jar = entry["tag"], entry["jar"]
        path = "releases/%s/%s" % (tag, jar)
        assert sums.get(path) == entry["sha256"], "SHA256SUMS and lineage disagree for " + path
        commit = sh("git", "log", "--diff-filter=A", "--format=%H", "-1", "--", path).stdout.strip()
        assert commit, "no commit added " + path
        prerelease = entry.get("note", "").lower().startswith("do not install")
        base = by_sha.get(entry.get("declared_base_sha256"), {}).get("tag", entry.get("declared_base_sha256", "?"))
        version = entry["id"]
        name = tag[1:] if tag.startswith("v") else tag
        lines = []
        if prerelease:
            lines.append("**Do not install.** " + entry["note"])
        elif entry["tag"] == latest:
            lines.append("**Newest build.** Install it as the only `!Akashic-ThePatcher-*.jar` in `mods/`.")
        else:
            lines.append("Superseded by a newer build; kept for the history.")
        lines += ["", "| | |", "|---|---|",
                  "| Kind | %s |" % entry.get("kind", "?"),
                  "| SHA-256 | `%s` |" % entry["sha256"],
                  "| Size | %s bytes, %s ZIP entries |" % (entry.get("size"), entry.get("entries")),
                  "| Declared base | %s |" % base,
                  "| Build date | %s |" % entry.get("date", "?"),
                  "| Commit | %s |" % commit, "",
                  changelog_section(version), "",
                  "Details: [CHANGELOG](https://github.com/%s/blob/main/docs/CHANGELOG.md), "
                  "[LINEAGE](https://github.com/%s/blob/main/docs/LINEAGE.md)." % (repo, repo),
                  "The asset is uploaded without the leading `!`; verify it with the SHA-256 above."]
        body = "\n".join(lines) + "\n"
        flags = ["--prerelease"] if prerelease else []
        flags.append("--latest" if tag == latest else "--latest=false")
        print("PUBLISH %s commit=%s prerelease=%s latest=%s" % (tag, commit[:7], prerelease, tag == latest))
        if args.dry_run:
            print(body)
            continue
        with tempfile.TemporaryDirectory() as tmp:
            asset = pathlib.Path(tmp) / jar.lstrip("!")
            shutil.copyfile(ROOT / path, asset)
            notes = pathlib.Path(tmp) / "notes.md"
            notes.write_text(body, encoding="utf-8")
            sh("gh", "release", "create", tag, str(asset), "--repo", repo, "--target", commit,
               "--title", name, "--notes-file", str(notes), *flags)
    print("PUBLISH_RELEASES done: %d release(s)" % len(pending))


if __name__ == "__main__":
    main()
