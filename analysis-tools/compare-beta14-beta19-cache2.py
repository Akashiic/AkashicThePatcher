import gzip,pathlib,re,sys
root=pathlib.Path(r"C:\Users\User\AppData\Roaming\.technic\modpacks\forbidden-series\logs")
sources={
 "beta14": gzip.open(root/"2026-09-24-2.log.gz",'rt',encoding='utf-8',errors='replace').read().splitlines(),
 "beta19": (root/"latest.log").read_text(encoding='utf-8',errors='replace').splitlines(),
}
patterns=[
 "Loaded lite transformer cache",
 "Replacing cpw.mods.fml.common.asm.transformers.SideTransformer",
 "Replacing net.minecraftforge.classloading.FluidIdTransformer",
 "Attempting early MinecraftForge initialization",
 "Completed early MinecraftForge initialization",
 "Pre Initialization ( started )",
 "Pre Initialization ( ended after",
 "Entering preinitialization phase.",
 "Total mipmap :",
 "Finished PreInitialization",
 "Resource manager reload, new language:",
 "Attempting to run pre-initialization methods",
 "Starting PreInitialization for modtweaker2",
 "Initialization ( started )",
 "Initialization ( ended after",
 "Finished API Initialization",
 "Entering initialization phase.",
 "Finished Initialization",
 "Attempting to run initialization methods",
 "Starting Initialization for modtweaker2",
 "Post Initialization ( started )",
 "Post Initialization ( ended after",
 "Entering postinitialization phase.",
 "Finished PostInitialization",
 "Attempting to run post-initialization methods",
 "Starting PostInitialization for modtweaker2",
 "Load Complete.",
 "Thermal Foundation: Load Complete.",
 "Thermal Expansion: Load Complete.",
]
for label,lines in sources.items():
    print("\n### "+label)
    for line in lines:
        if any(p in line for p in patterns):
            safe=line.encode('ascii','replace').decode('ascii')
            print(safe)