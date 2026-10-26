#!/bin/bash
# bench.sh <instance dir> <X display number> <phases, e.g. 0,300,900> [extra JVM flags]
# Runs one headless-client session (beta.39's tools/headless-client/run.sh, set up in $MC) with holobench.jar in the
# instance's mods/ and prints holobench-results.txt. Needs JAVA8_HOME.
set -u
I=${1:?instance}
cd "${MC:?set MC to the headless-client work dir}" && export JAVA8_HOME=${JAVA8_HOME:?} JVM_EXTRA="-Dholobench.phases=$3 ${4:-}"
rm -rf "${I:?}/shots" "${I:?}/saves" "${I:?}/holobench-results.txt"
./run.sh "$I" 900 "$2" > "$I/run.out" 2>&1
echo "== $I"; cat "$I/holobench-results.txt" 2>/dev/null || { echo NO RESULTS; grep -a -E 'Exception|Error' "$I/stdout.log" | head -5; }
