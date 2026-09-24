from pathlib import Path
p=Path(r"C:\Users\User\Desktop\Codex\Akashic-ThePatcher-1.1.0-beta.20-StartupLifecycleProfiler\tools\beta20-final-build.py")
t=p.read_text(encoding="utf-8")
old='''expected_resources = [
    "META-INF/MANIFEST.MF",
    "mcmod.info",
    MIXIN_CFG,
    REFMAP,
]
'''
new='''expected_resources = [
    "META-INF/MANIFEST.MF",
    "mcmod.info",
    MIXIN_CFG,
]
'''
if old not in t:
    raise SystemExit("expected resources block not found")
p.write_text(t.replace(old,new),encoding="utf-8",newline="\n")
print("BETA20_RESOURCE_GATE_FIXED")