import gzip,re,datetime,pathlib,json
logs=pathlib.Path(r"C:\Users\User\AppData\Roaming\.technic\modpacks\forbidden-series\logs")
sources={
 "beta14": ("gz", logs/"2026-09-24-2.log.gz"),
 "beta19": ("txt", logs/"latest.log"),
}
markers=[
 ("FML","Loading tweak class name cpw.mods.fml.common.launcher.FMLTweaker"),
 ("InitialTextureDone","[Texture Pack/INFO]: done"),
 ("ConfigDone","Configuration load completed."),
 ("PreInitDone","Finished PreInitialization"),
 ("InitDone","Finished Initialization"),
 ("PostInitDone","Finished PostInitialization"),
 ("LoadComplete","Load Complete."),
 ("ThermalFoundation","Thermal Foundation: Load Complete."),
 ("ThermalExpansion","Thermal Expansion: Load Complete."),
 ("BlocksHIT","StitchLayoutCache HIT sprites=19336"),
 ("BlocksProfile","AtlasProfile path=textures/blocks total="),
 ("ItemsHIT","StitchLayoutCache HIT sprites=15980"),
 ("ItemsProfile","AtlasProfile path=textures/items total="),
 ("FinalTextureDone","[Texture Pack/INFO]: done"),
 ("Menu","Cinematic replacing exact menu class: net.minecraft.client.gui.GuiMainMenu"),
]
time_re=re.compile(r'^\[(\d\d):(\d\d):(\d\d)\]')
for label,(kind,p) in sources.items():
    text=(gzip.open(p,'rt',encoding='utf-8',errors='replace').read()
          if kind=='gz' else p.read_text(encoding='utf-8',errors='replace'))
    lines=text.splitlines()
    fml=None
    events=[]
    texdone=[]
    menus=[]
    for line in lines:
        m=time_re.match(line)
        if not m: continue
        sec=int(m.group(1))*3600+int(m.group(2))*60+int(m.group(3))
        if "Loading tweak class name cpw.mods.fml.common.launcher.FMLTweaker" in line and fml is None:
            fml=sec
        if "[Texture Pack/INFO]: done" in line: texdone.append((sec,line))
        if "Cinematic replacing exact menu class: net.minecraft.client.gui.GuiMainMenu" in line: menus.append((sec,line))
    chosen={}
    for name,needle in markers:
        if name=="InitialTextureDone":
            if texdone: chosen[name]=texdone[0][0]
            continue
        if name=="FinalTextureDone":
            if texdone: chosen[name]=texdone[-1][0]
            continue
        if name=="Menu":
            if menus: chosen[name]=menus[0][0]
            continue
        for line in lines:
            if needle in line:
                m=time_re.match(line)
                if m:
                    chosen[name]=int(m.group(1))*3600+int(m.group(2))*60+int(m.group(3))
                    break
    print("\n###",label)
    prev=None
    for name,_ in markers:
        if name not in chosen or fml is None: continue
        off=chosen[name]-fml
        step=None if prev is None else chosen[name]-prev[1]
        print(f"{name:20s} +{off:4d}s", "" if step is None else f" step={step:3d}s")
        prev=(name,chosen[name])

# direct comparative milestones
def parse(kind,p):
    text=(gzip.open(p,'rt',encoding='utf-8',errors='replace').read()
          if kind=='gz' else p.read_text(encoding='utf-8',errors='replace'))
    lines=text.splitlines()
    def first(needle):
        for line in lines:
            if needle in line:
                m=time_re.match(line)
                if m:return int(m.group(1))*3600+int(m.group(2))*60+int(m.group(3))
    def last(needle):
        out=None
        for line in lines:
            if needle in line:
                m=time_re.match(line)
                if m:out=int(m.group(1))*3600+int(m.group(2))*60+int(m.group(3))
        return out
    return {
      "FML":first("Loading tweak class name cpw.mods.fml.common.launcher.FMLTweaker"),
      "Config":first("Configuration load completed."),
      "PreInit":first("Finished PreInitialization"),
      "Init":first("Finished Initialization"),
      "PostInit":first("Finished PostInitialization"),
      "Load":first("Load Complete."),
      "ThermalExp":first("Thermal Expansion: Load Complete."),
      "FinalTex":last("[Texture Pack/INFO]: done"),
      "Menu":first("Cinematic replacing exact menu class: net.minecraft.client.gui.GuiMainMenu"),
    }
a=parse(*sources["beta14"]); b=parse(*sources["beta19"])
print("\n### DELTA beta19-beta14 at milestone offsets")
for k in a:
    print(k, (b[k]-b["FML"])-(a[k]-a["FML"]))