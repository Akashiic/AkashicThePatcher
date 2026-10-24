from pathlib import Path
import re,shlex
p=Path(r"C:\Users\User\AppData\Roaming\.technic\logs\techniclauncher_2026-09-24.log")
lines=p.read_text(encoding='utf-8',errors='replace').splitlines()
for stamp in ("13:18:","16:27:"):
    print("\n---",stamp,"---")
    for line in lines:
        if stamp in line and "[INFO] Running " in line and "jre-legacy\\bin\\javaw.exe" in line:
            cmd=line.split("[INFO] Running ",1)[1]
            before_cp=cmd.split(" -cp ",1)[0]
            parts=before_cp.split()
            # Drop executable token/path fragments until first JVM option.
            opts=[x for x in parts if x.startswith("-")]
            print(" ".join(opts))
            break