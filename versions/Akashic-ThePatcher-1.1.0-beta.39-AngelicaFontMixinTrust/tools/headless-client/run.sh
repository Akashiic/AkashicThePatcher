#!/bin/bash
# usage: run.sh <instance-dir> <seconds> <display-number>
set -u
MC=$(cd "$(dirname "$0")" && pwd)
INST=$1; SECS=$2; DISP=$3
J8=${JAVA8_HOME:?set JAVA8_HOME}/bin/java
CP=$(python3 -c "import json;print(':'.join('$MC/'+p for p in json.load(open('$MC/cp.json'))['forge_cp']))"):$MC/versions/1.7.10.jar
mkdir -p "$INST/shots"
Xvfb :$DISP -screen 0 1280x720x24 -nolisten tcp >/dev/null 2>&1 &
XPID=$!
sleep 2
export DISPLAY=:$DISP LIBGL_ALWAYS_SOFTWARE=1 GALLIUM_DRIVER=llvmpipe
openbox >/dev/null 2>&1 &
WMPID=$!
cd "$INST"
"$J8" ${JVM_EXTRA:-} -Xmx3G -Xms1G -Djava.library.path=$MC/natives -Dfml.ignoreInvalidMinecraftCertificates=true \
  -cp "$CP" net.minecraft.launchwrapper.Launch --username ACMenuQA --version 1.7.10 --gameDir "$INST" \
  --assetsDir "$MC/assets" --assetIndex 1.7.10 --uuid 00000000000000000000000000000000 --accessToken 0 \
  --userProperties '{}' --userType legacy --tweakClass cpw.mods.fml.common.launcher.FMLTweaker > "$INST/stdout.log" 2>&1 &
JPID=$!
for ((t=0; t<SECS; t+=3)); do
  sleep 3
  if [ -z "${NOFOCUS:-}" ]; then for w in $(xdotool search --name "Minecraft" 2>/dev/null); do xdotool windowactivate $w 2>/dev/null; done; fi
  import -display :$DISP -window root "$INST/shots/t$(printf %03d $t).png" 2>/dev/null
  kill -0 $JPID 2>/dev/null || { echo "java exited at ${t}s"; break; }
  if [ -n "${DUMP_AT:-}" ] && [ $t -eq $DUMP_AT ]; then "$(dirname $J8)/jstack" $JPID > "$INST/jstack-${t}s.txt" 2>&1; fi
done
kill $JPID 2>/dev/null; sleep 2; kill -9 $JPID 2>/dev/null
kill $WMPID 2>/dev/null; kill $XPID 2>/dev/null
wait 2>/dev/null
echo "done $INST"
