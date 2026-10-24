import zipfile,difflib
old=r"C:\Users\User\Desktop\Codex\Angelica-2.2.8-vs-2.2.18-Audit\angelica-2.2.8.jar"
new=r"C:\Users\User\AppData\Roaming\.technic\modpacks\forbidden-series\mods\angelica-2.2.18.jar"
files=[
"assets/sodium/shaders/blocks/block_layer_opaque.fsh",
"assets/sodium/shaders/blocks/block_layer_opaque.vsh",
"assets/sodium/shaders/include/chunk_vertex.glsl",
"assets/sodium/shaders/include/fog.glsl",
"assets/angelica/shaders/blocks/block_layer_opaque.fsh",
]
zo=zipfile.ZipFile(old); zn=zipfile.ZipFile(new)
for f in files:
 print("\n###",f)
 a=zo.read(f).decode("utf-8","replace").splitlines() if f in zo.namelist() else []
 b=zn.read(f).decode("utf-8","replace").splitlines() if f in zn.namelist() else []
 print("\n".join(difflib.unified_diff(a,b,fromfile="2.2.8/"+f,tofile="2.2.18/"+f,lineterm="")))
zo.close();zn.close()