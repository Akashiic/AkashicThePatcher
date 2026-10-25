#!/usr/bin/env bash
# Downloads the third-party build/gate inputs of beta.38+ into <dir> and checks every SHA-256.
# They are never committed (see docs/PROVENANCE.md); this list pins the exact files the builds were audited with.
#   tools/ci/fetch_build_libs.sh <dir>
set -euo pipefail
DIR=${1:?usage: fetch_build_libs.sh <dir>}
mkdir -p "$DIR"
MC=https://libraries.minecraft.net
CENTRAL=${MAVEN_CENTRAL:-https://repo.maven.apache.org/maven2}  # override with a mirror if Central is unreachable
GTNH=https://nexus.gtnewhorizons.com/repository/public/com/github/GTNewHorizons
while read -r name sha url; do
  [ -z "$name" ] && continue
  if [ ! -f "$DIR/$name" ] || ! echo "$sha  $DIR/$name" | sha256sum -c --status; then
    curl -fsSL --retry 4 --retry-delay 3 -o "$DIR/$name" "$url" || { echo "download failed: $url" >&2; exit 1; }
  fi
  echo "$sha  $DIR/$name" | sha256sum -c --quiet
done <<LIST
launchwrapper-1.12.jar 57f402b626d16cc2705bf2a37add7adbb074f0ca3b3102fa6e23aa303dae682f $MC/net/minecraft/launchwrapper/1.12/launchwrapper-1.12.jar
asm-all-5.0.3.jar 09d44e6adddaab9fd89ea3164c439f71c755769fa8d3a940352ab3d2003b98ff $CENTRAL/org/ow2/asm/asm-all/5.0.3/asm-all-5.0.3.jar
log4j-api.jar eb0ca9bdd938f80711152a758d6bc331a8b0d769199d457294dc67ed03f73498 $MC/org/apache/logging/log4j/log4j-api/2.0-beta9/log4j-api-2.0-beta9.jar
log4j-core.jar dcde6033b205433d6e9855c93740f798951fa3a3f252035a768d9f356fde806d $MC/org/apache/logging/log4j/log4j-core/2.0-beta9/log4j-core-2.0-beta9.jar
guava-17.0.jar 8c36a80ea613d0b6b8040a17cf837c5bbe3677bc1b06a058a6c174fdb787ebbc $MC/com/google/guava/guava/17.0/guava-17.0.jar
jopt-simple-4.5.jar 64335a48be6b142a7d0c13ec5c82a707857aa58c0234e6eda3eaca0e96eea51b $MC/net/sf/jopt-simple/jopt-simple/4.5/jopt-simple-4.5.jar
asm-9.7.jar adf46d5e34940bdf148ecdd26a9ee8eea94496a72034ff7141066b3eea5c4e9d $CENTRAL/org/ow2/asm/asm/9.7/asm-9.7.jar
asm-tree-9.7.jar 62f4b3bc436045c1acb5c3ba2d8ec556ec3369093d7f5d06c747eb04b56d52b1 $CENTRAL/org/ow2/asm/asm-tree/9.7/asm-tree-9.7.jar
asm-analysis-9.7.jar 7bc6bcbc21379948a0c8c467fb0f864206e5b818f6bc0b546872f5c9f941556f $CENTRAL/org/ow2/asm/asm-analysis/9.7/asm-analysis-9.7.jar
asm-util-9.7.jar 37a6414d36641973f1af104937c95d6d921b2ddb4d612c66c5a9f2b13fc14211 $CENTRAL/org/ow2/asm/asm-util/9.7/asm-util-9.7.jar
angelica-2.2.37.jar 2125183d9f3c11a401be61e6273e1e813a51e41d892ba035b99cdfdb83e2677b $GTNH/Angelica/2.2.37/Angelica-2.2.37.jar
angelica-2.2.23.jar a7b82ca3a193f6737ebb36650f7287b883cf6985a30608c8fed0a6a9c1d4a5f0 $GTNH/Angelica/2.2.23/Angelica-2.2.23.jar
angelica-2.2.18.jar 563d907f3035aedc7761be0f882674d6e736b63c5860e126e7af7df0d9f15c50 $GTNH/Angelica/2.2.18/Angelica-2.2.18.jar
necrotempus-2.0.0.jar d220dad5f270b99fd6ad8a42dcdb1d092eb97412f5c4d888aa072ea3fecc374c https://github.com/CrucibleMC/NecroTempus/releases/download/v2.0.0/necrotempus-2.0.0.jar
necrotempus-2.1.0.jar a34a021fb88a4d3ef1806a9304e1a507ee1535e0e8203d1f5ef43bac4b842b9a https://github.com/CrucibleMC/NecroTempus/releases/download/v2.1.0/necrotempus-v2.1.0.jar
+unimixins-all-1.7.10-0.3.1.jar ad0ea4f92daf7bf7ec5c10e16258425e47929126954aea5d80df69ce26cd7318 https://github.com/LegacyModdingMC/UniMixins/releases/download/0.3.1/+unimixins-all-1.7.10-0.3.1.jar
LIST
echo "FETCH_BUILD_LIBS OK $(ls "$DIR" | wc -l) files in $DIR"
