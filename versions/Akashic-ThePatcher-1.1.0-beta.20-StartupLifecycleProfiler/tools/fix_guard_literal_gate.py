from pathlib import Path
p=Path(r"C:\Users\User\Desktop\Codex\Akashic-ThePatcher-1.1.0-beta.20-StartupLifecycleProfiler\tools\beta20-final-build.py")
t=p.read_text(encoding="utf-8")
t=t.replace('    b"6/6",\n','    b"compilerOracleRequired",\n')
p.write_text(t,encoding="utf-8",newline="\n")
print("BETA20_GUARD_LITERAL_GATE_FIXED")