import json, pathlib
src=pathlib.Path(r"C:\Users\User\AppData\Roaming\.technic\modpacks\forbidden-series\bin\version.json")
out=pathlib.Path(r"C:\Users\User\Desktop\Codex\forestry-versionjson-packfix\version-patched.json")
data=json.loads(src.read_text(encoding="utf-8"))
legacy=data.get("minecraftArguments")
if not legacy:
    raise SystemExit("minecraftArguments missing")
game=legacy.split()
jvm=[
 "-XX:CompileCommand=exclude,forestry/core/genetics/Genome.getChromosomes",
 "-XX:CompileCommand=exclude,com/akashic/thepatcher/module/forestry/ForestryGenomeCompat.loadChromosomes",
 "-XX:CompileCommand=exclude,com/akashic/thepatcher/module/forestry/ForestryGenomeCompat.loadStoredChromosome",
 "-XX:CompileCommand=exclude,com/akashic/thepatcher/module/forestry/ForestryGenomeCompat.repairInvalidAlleles",
 "-XX:CompileCommand=exclude,com/akashic/thepatcher/module/forestry/ForestryGenomeCompat.validateChromosome",
 "-XX:CompileCommand=exclude,com/akashic/thepatcher/module/forestry/ForestryGenomeCompat.fillMissingChromosomes",
]
data["arguments"]={"game":game,"jvm":jvm}
out.write_text(json.dumps(data,indent=2)+"\n",encoding="utf-8")
print("PATCHED",out)
print("gameArgs",len(game),"jvmArgs",len(jvm))