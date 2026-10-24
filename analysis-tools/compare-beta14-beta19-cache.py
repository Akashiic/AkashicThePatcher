import gzip,pathlib,re
root=pathlib.Path(r"C:\Users\User\AppData\Roaming\.technic\modpacks\forbidden-series\logs")
sources={
 "beta14": gzip.open(root/"2026-09-24-2.log.gz",'rt',encoding='utf-8',errors='replace').read().splitlines(),
 "beta19": (root/"latest.log").read_text(encoding='utf-8',errors='replace').splitlines(),
}
needles=[
 "CoreTweaks","transformer cache","Transformer cache","cache hit","cache miss",
 "Loading cached","cached transformer","transformerCache","full transformer",
 "LiteTransformer","diff","cache invalid","Caching transformers","Cached transformers",
 "Finished MinecraftForge preInitialization","Construction","PreInitialization",
 "Initialization","PostInitialization","LoadComplete","Load Complete",
 "Total mipmap","Resource manager reload","Memory:"
]
for label,lines in sources.items():
    print("\n###",label)
    for line in lines:
        low=line.lower()
        if any(n.lower() in low for n in needles):
            print(line)