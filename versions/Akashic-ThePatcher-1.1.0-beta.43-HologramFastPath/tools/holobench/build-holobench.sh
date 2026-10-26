#!/bin/bash
# build-holobench.sh <headless-client work dir (setup.py output)> <libs dir (tools/ci/fetch_build_libs.sh)> <out.jar>
# 1. remaps the vanilla 1.7.10 client to MCP names (Deobf: Forge 1614's notch->SRG data + mcp_stable-12 CSVs),
# 2. compiles HoloBench.java against it and the Forge/LWJGL/netty libraries of the client,
# 3. renames its Minecraft references back to SRG (Reobf), which is what a Forge client runs with.
set -euo pipefail
MC=$(cd "$1" && pwd); L=$(cd "$2" && pwd); OUT=$(realpath -m "$3"); H=$(cd "$(dirname "$0")" && pwd)
W=$(mktemp -d); trap 'rm -rf "${W:?}"' EXIT
ASM="$L/asm-9.7.jar:$L/asm-commons-9.7.jar:$L/asm-tree-9.7.jar"
unzip -q -o "$MC"/forge-1.7.10-10.13.4.1614-1.7.10-universal.jar deobfuscation_data-1.7.10.lzma -d "$W"
python3 -c "import lzma,sys;open(sys.argv[2],'wb').write(lzma.decompress(open(sys.argv[1],'rb').read(),format=lzma.FORMAT_ALONE))" \
  "$W/deobfuscation_data-1.7.10.lzma" "$W/notch-srg.srg"
unzip -q -o "$L/mcp_stable-12-1.7.10.zip" -d "$W/mcp"
javac -cp "$ASM" -d "$W/tools" "$H/Deobf.java" "$H/Reobf.java"
java -cp "$ASM:$W/tools" Deobf "$MC/versions/1.7.10.jar" "$W/notch-srg.srg" "$W/mcp" "$W/mc-mcp.jar"
CP=$(cd "$MC" && python3 -c "import json;print(':'.join('$MC/'+p for p in json.load(open('cp.json'))['forge_cp']))")
javac --release 8 -nowarn -cp "$W/mc-mcp.jar:$CP" -d "$W/cls" "$H/HoloBench.java"
java -cp "$ASM:$W/tools" Reobf "$W/notch-srg.srg" "$W/mcp" "$W/mc-mcp.jar" "$W/cls" "$W/rcls"
(cd "$W/rcls" && jar cf "$OUT" .)
echo "HOLOBENCH_BUILD OK $OUT"
