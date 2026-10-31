#!/usr/bin/env bash
# Rebuilds Akashic-ElnEndlessIdsFix, compares it byte for byte with dist/, runs the hook test, and applies the
# transformer to the real Electrical Age 1.24.6 and 1.24.8 classes (VerifyPatch: ASM + HotSpot verifier).
#   projects/Akashic-ElnEndlessIdsFix/tools/check.sh <libs dir from tools/ci/fetch_build_libs.sh> [javac] [java]
set -euo pipefail
LIBS=$(cd "${1:?usage: check.sh <libs dir> [javac] [java]}" && pwd)
JAVAC=${2:-javac}
JAVA=${3:-java}
HERE=$(cd "$(dirname "$0")/.." && pwd)
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
JAR=Akashic-ElnEndlessIdsFix-1.0.0.jar

python3 "$HERE/build.py" --javac "$JAVAC" --libs "$LIBS" --out "$WORK/$JAR"
cmp "$WORK/$JAR" "$HERE/dist/$JAR"
(cd "$HERE/dist" && sha256sum -c SHA256SUMS.txt)

mkdir "$WORK/ht"
"$JAVAC" -nowarn -d "$WORK/ht" $(find "$HERE/tools/hooktest" -name '*.java') \
  "$HERE/src/main/java/com/akashic/elnendlessids/hook/ElnEndlessIdsHooks.java"
"$JAVA" -cp "$WORK/ht" HookTest

# Electrical Age release JARs (age-series/ElectricalAge), hash-pinned. 1.24.6 and 1.24.8 carry byte-identical
# copies of both patched classes.
while read -r v sha; do
  f="$LIBS/Eln-$v.jar"
  if [ ! -f "$f" ] || ! echo "$sha  $f" | sha256sum -c --status; then
    curl -fsSL --retry 4 --retry-delay 3 -o "$f" "https://github.com/age-series/ElectricalAge/releases/download/$v/Eln-$v.jar"
  fi
  echo "$sha  $f" | sha256sum -c --quiet
done <<LIST
1.24.6 1e0f87c7d3bb3edf018c46bb64d69a530ebe87ecec2a794298138731d05bfbae
1.24.8 d119bf2ae1ce14bece14b208736d9d87d35ab53ed29c62f65c4218bd3acfd053
LIST

mkdir "$WORK/vp"
"$JAVAC" -nowarn -cp "$LIBS/asm-all-5.0.3.jar" -d "$WORK/vp" "$HERE/tools/VerifyPatch.java"
for v in 1.24.6 1.24.8; do
  "$JAVA" -cp "$WORK/vp:$LIBS/asm-all-5.0.3.jar" VerifyPatch "$HERE/dist/$JAR" "$LIBS/Eln-$v.jar" "$LIBS" "$WORK/out-$v"
done
echo "ELN ENDLESSIDS FIX CHECK OK"
