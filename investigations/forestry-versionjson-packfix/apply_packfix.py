import json, pathlib, zipfile, os, hashlib, tempfile
game=pathlib.Path(r"C:\Users\User\AppData\Roaming\.technic\modpacks\forbidden-series")
jar=game/"bin"/"modpack.jar"
binver=game/"bin"/"version.json"
jvm=[
 "-XX:CompileCommand=exclude,forestry/core/genetics/Genome.getChromosomes",
 "-XX:CompileCommand=exclude,com/akashic/thepatcher/module/forestry/ForestryGenomeCompat.loadChromosomes",
 "-XX:CompileCommand=exclude,com/akashic/thepatcher/module/forestry/ForestryGenomeCompat.loadStoredChromosome",
 "-XX:CompileCommand=exclude,com/akashic/thepatcher/module/forestry/ForestryGenomeCompat.repairInvalidAlleles",
 "-XX:CompileCommand=exclude,com/akashic/thepatcher/module/forestry/ForestryGenomeCompat.validateChromosome",
 "-XX:CompileCommand=exclude,com/akashic/thepatcher/module/forestry/ForestryGenomeCompat.fillMissingChromosomes",
]
with zipfile.ZipFile(jar,"r") as zin:
    raw=zin.read("version.json")
    data=json.loads(raw.decode("utf-8"))
    legacy=data.get("minecraftArguments")
    if not legacy:
        raise SystemExit("embedded minecraftArguments missing")
    data["arguments"]={
      "game": legacy.split(),
      "jvm": jvm,
    }
    patched=(json.dumps(data,indent=2)+"\n").encode("utf-8")
    infos=zin.infolist()
    blobs={i.filename: zin.read(i.filename) for i in infos}
blobs["version.json"]=patched
tmp=jar.with_suffix(".jar.tmp-forestry-packfix")
with zipfile.ZipFile(tmp,"w") as zout:
    for info in infos:
        payload=blobs[info.filename]
        # Preserve entry metadata/compression; only version.json bytes change.
        zout.writestr(info,payload)
with zipfile.ZipFile(tmp,"r") as z:
    bad=z.testzip()
    if bad:
        raise SystemExit("CRC failed: "+bad)
    embedded=z.read("version.json")
    parsed=json.loads(embedded.decode("utf-8"))
    if parsed.get("arguments",{}).get("jvm") != jvm:
        raise SystemExit("embedded JVM args validation failed")
os.replace(tmp,jar)
binver.write_bytes(patched)
print("PACKFIX_JAR_SHA",hashlib.sha256(jar.read_bytes()).hexdigest())
print("VERSION_SHA",hashlib.sha256(patched).hexdigest())
print("JVM_ARGS",len(jvm))