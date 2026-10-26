#!/bin/bash
# Builds one headless-client instance for the Mirai x Angelica shader A/B (beta.39's tools/headless-client harness).
#   mkinst.sh <harness dir> <pack .minecraft dir> <probe.jar> <name> <ThePatcher jar|none> <on|off>
# Mods, all taken from the TEOF-4.0-MANUAL pack: +unimixins-all-1.7.10-0.3.1, gtnhlib-0.11.51, angelica-2.2.37,
# Akashic-Horizons-of-The-Mirai-forge17-0.1.19-dev-1.7.10 (SHA-256 481d6f7b...986ea); configs: the pack's
# angelica-modules.cfg / angelica.cfg / angelica-options.json; shader pack: the pack's BSL_v8.2.09.zip (as a .zip).
# Then: JAVA8_HOME=<jdk8> <harness>/run.sh <harness>/inst-<name> 330 <display>
set -eu
MC=$1; P=$2; PROBE=$3; NAME=$4; PATCHER=$5; SHADERS=$6
I=$MC/inst-$NAME; rm -rf "$I"; mkdir -p "$I/mods" "$I/config" "$I/shaderpacks"
cp "$P/mods/+unimixins-all-1.7.10-0.3.1.jar" "$P/mods/gtnhlib-0.11.51.jar" "$P/mods/angelica-2.2.37.jar" \
   "$P/mods/Akashic-Horizons-of-The-Mirai-forge17-0.1.19-dev-1.7.10.jar" "$PROBE" "$I/mods/"
[ "$PATCHER" != none ] && cp "$PATCHER" "$I/mods/"
cp "$P/config/angelica-modules.cfg" "$P/config/angelica.cfg" "$P/config/angelica-options.json" "$I/config/"
cp "$P/shaderpacks/BSL_v8.2.09.zip" "$I/shaderpacks/"
if [ "$SHADERS" = on ]; then printf 'shaderPack=BSL_v8.2.09.zip\nenableShaders=true\n' > "$I/config/shaders.properties"
else printf 'shaderPack=\nenableShaders=false\n' > "$I/config/shaders.properties"; fi
echo "$I"
