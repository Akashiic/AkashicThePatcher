from pathlib import Path
p=Path(r"C:\Users\User\Desktop\Codex\Akashic-ThePatcher-1.1.0-beta.16-ForestryUniversal\src\ForestryGenomeC2Transformer.java")
t=p.read_text(encoding="utf-8")
t=t.replace(
    '"Found Chromosome with invalid Alleles.\\nNBTTagCompound:"',
    '"Found Chromosome with invalid Alleles.\\nNBTTagCompound: "')
t=t.replace(
    '"\\nSee config option \\\"genetics.clear.invalid.chromosomes\\\".\\nMissing:"',
    '"\\nSee config option \\\"genetics.clear.invalid.chromosomes\\\".\\nMissing: "')
p.write_text(t,encoding="utf-8",newline="\n")
print("FINGERPRINT_SPACES_FIXED")