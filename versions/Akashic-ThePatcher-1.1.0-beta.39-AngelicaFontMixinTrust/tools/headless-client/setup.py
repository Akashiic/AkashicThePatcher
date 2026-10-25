"""Set up a headless Minecraft 1.7.10 + Forge 10.13.4.1614 client for A/B tests (no credentials, offline user).

    python3 setup.py <workdir>            -> <workdir>/{versions,libraries,natives,cp.json,forge-...-universal.jar}
    then: mkdir <workdir>/inst-X/mods, copy the mods, and run
    JAVA8_HOME=<jdk8> ./run.sh <workdir>/inst-X <seconds> <display> (needs Xvfb, openbox, xdotool, ImageMagick)

run.sh starts Xvfb + openbox (the window must have focus: CinematicMenu's clock pauses while it is inactive),
software GL (Mesa llvmpipe, GL 4.5 core), focuses the window and takes a screenshot every 3 s into inst-X/shots.
Optional: DUMP_AT=<s> writes a jstack, JVM_EXTRA="-D..." adds JVM flags, NOFOCUS=1 skips focusing.
Downloads come from Mojang, maven.minecraftforge.net, libraries.minecraft.net and a Maven Central mirror.
"""
import json, os, sys, urllib.request, zipfile

MIRRORS = ["https://libraries.minecraft.net/", "https://maven.minecraftforge.net/",
           "https://maven-central.storage-download.googleapis.com/maven2/"]
FORGE = "https://maven.minecraftforge.net/net/minecraftforge/forge/1.7.10-10.13.4.1614-1.7.10/forge-1.7.10-10.13.4.1614-1.7.10-installer.jar"
SCALA_MODULES = {"scala-parser-combinators_2.11", "scala-swing_2.11", "scala-xml_2.11"}


def get(url, path):
    if os.path.exists(path):
        return
    os.makedirs(os.path.dirname(path) or ".", exist_ok=True)
    request = urllib.request.Request(url, headers={"User-Agent": "akashic-headless-client/1.0"})  # maven.minecraftforge.net rejects urllib's default
    with urllib.request.urlopen(request, timeout=120) as r, open(path + ".part", "wb") as f:
        f.write(r.read())
    os.rename(path + ".part", path)


def main(work):
    os.makedirs(work, exist_ok=True)
    os.chdir(work)
    man = json.load(urllib.request.urlopen("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"))
    get([v for v in man["versions"] if v["id"] == "1.7.10"][0]["url"], "versions/1.7.10.json")
    vj = json.load(open("versions/1.7.10.json"))
    get(vj["downloads"]["client"]["url"], "versions/1.7.10.jar")
    vanilla, natives = [], []
    for lib in vj["libraries"]:
        allowed = True
        for rule in lib.get("rules", []):
            if rule.get("os", {}).get("name") in (None, "linux"):
                allowed = rule["action"] == "allow"
        if not allowed:
            continue
        d = lib.get("downloads", {})
        if "artifact" in d:
            p = "libraries/" + d["artifact"]["path"]
            get(d["artifact"]["url"], p)
            vanilla.append(p)
        if "linux" in lib.get("natives", {}):
            a = d["classifiers"][lib["natives"]["linux"].replace("${arch}", "64")]
            p = "libraries/" + a["path"]
            get(a["url"], p)
            natives.append(p)
    for n in natives:
        with zipfile.ZipFile(n) as z:
            for e in z.namelist():
                if not e.startswith("META-INF") and not e.endswith("/"):
                    z.extract(e, "natives")
    get(FORGE, "forge-installer.jar")
    with zipfile.ZipFile("forge-installer.jar") as z:
        profile = json.loads(z.read("install_profile.json"))
        universal = profile["install"]["filePath"]
        with open(universal, "wb") as f:
            f.write(z.read(universal))
    forge = [universal]
    for lib in profile["versionInfo"]["libraries"]:
        g, a, v = lib["name"].split(":")
        if a == "forge":
            continue
        rel = "%s/%s/%s/%s-%s.jar" % (g.replace(".", "/"), a, v, a, v)
        path = "libraries/" + rel
        candidates = ([lib["url"]] if lib.get("url") else []) + MIRRORS
        if a in SCALA_MODULES:  # published under org.scala-lang.modules
            candidates = [MIRRORS[2] + "org/scala-lang/modules/" + rel[len("org/scala-lang/"):]]
        for base in candidates:
            try:
                get(base if base.endswith(".jar") else base + rel, path)
                break
            except Exception:
                continue
        else:
            raise SystemExit("missing " + rel)
        forge.append(path)
    names = {os.path.basename(x).rsplit("-", 1)[0] for x in forge}
    cp = forge + [x for x in vanilla if os.path.basename(x).rsplit("-", 1)[0] not in names]
    json.dump({"vanilla_cp": vanilla, "forge_cp": cp}, open("cp.json", "w"), indent=1)
    print("SETUP OK", len(cp), "classpath entries")


if __name__ == "__main__":
    main(sys.argv[1])
