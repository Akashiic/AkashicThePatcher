# HoloBench - HolographicDisplays holograms in a headless 1.7.10 client

Measures what many HolographicDisplays 2.x holograms cost a 1.7.10 client, without a Bukkit server: the client test mod
`HoloBench.java` creates a flat creative world, then builds the exact packets HolographicDisplays 2.2.6 sends for a 1.7 client
(`S0E` wither skull type 66 with data 0, `S0F` horse aged -1700000 with its custom name shown, `S1B` attach; item lines: `S0E` item +
`S1C` metadata, stack size 0 with the anti-stack lore), serializes them, and lets the client's own `NetHandlerPlayClient` process
them. It then records frame times per phase and writes `holobench-results.txt` in the instance.

1. Set up beta.39's headless client: `python3 versions/*beta.39*/tools/headless-client/setup.py <MC>` (Xvfb, openbox, xdotool,
   ImageMagick and a JDK 8 are needed; copy `run.sh` next to it).
2. `tools/ci/fetch_build_libs.sh <libs>`, then `./build-holobench.sh <MC> <libs> holobench.jar`.
3. Make an instance: `<inst>/mods` (UniMixins, GTNHLib, Angelica, EntityCulling, the ThePatcher JAR under test, `holobench.jar`)
   and `<inst>/config` (the pack's configs), `options.txt`.
4. `MC=<MC> JAVA8_HOME=<jdk8> ./bench.sh <inst> <display> 0,300,900`

Flags: `-Dholobench.phases=0,300,900` (lines per phase), `-Dholobench.linesPer=6`, `-Dholobench.items=true`,
`-Dholobench.warmup=100 -Dholobench.measure=300` (ticks), `-Dholobench.counter=true` (the second line of the front hologram
becomes a play-time counter, renamed every second with an `S1C` metadata packet like a HolographicDisplays placeholder). The RESULT line reports `vanillaTags` (text lines whose horse is within
64 blocks of the player, i.e. what a 1.7 client shows) and the particle count. Beyond 90 holograms the rows wrap (same for every
build). Results of beta.42/beta.43: `../../reports/real-client/runs.json`.
