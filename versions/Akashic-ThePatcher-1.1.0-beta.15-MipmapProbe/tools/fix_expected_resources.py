from pathlib import Path
p=Path(r"C:\Users\User\Desktop\Codex\Akashic-ThePatcher-1.1.0-beta.15-MipmapProbe\tools\beta15-final-build.py")
t=p.read_text(encoding="utf-8")
t=t.replace('    "mixins.akashicasynccompat.json",\n    "mixins.akashicasynccompat.refmap.json",\n','    "mixins.akashicasynccompat.json",\n')
p.write_text(t,encoding="utf-8",newline="\n")
print("EXPECTED_RESOURCES_FIXED")