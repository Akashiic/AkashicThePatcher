from pathlib import Path
p=Path(r"C:\Users\User\Desktop\Codex\Akashic-ThePatcher-1.1.0-beta.16-ForestryUniversal\src\ForestryGenomeC2Transformer.java")
t=p.read_text(encoding="utf-8")
old='''            throw new IllegalStateException(
                "Forestry 4.11.36 method fingerprint mismatch");
'''
new='''            throw new IllegalStateException(
                "Forestry 4.11.36 method fingerprint mismatch"
                    + " strings=" + strings
                    + " load=" + loadChromosome
                    + " clear=" + clearInvalid
                    + " key=" + karyotypeKey);
'''
if old not in t:
    raise SystemExit("anchor missing")
p.write_text(t.replace(old,new),encoding="utf-8",newline="\n")
print("FINGERPRINT_DIAGNOSTIC_ADDED")