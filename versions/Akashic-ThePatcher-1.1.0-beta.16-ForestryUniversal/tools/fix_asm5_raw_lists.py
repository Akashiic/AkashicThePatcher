from pathlib import Path
p=Path(r"C:\Users\User\Desktop\Codex\Akashic-ThePatcher-1.1.0-beta.16-ForestryUniversal\src\ForestryGenomeC2Transformer.java")
t=p.read_text(encoding="utf-8")
t=t.replace(
    "        for (MethodNode method : node.methods) {\n",
    "        for (Object methodObject : node.methods) {\n"
    "            final MethodNode method = (MethodNode) methodObject;\n")
t=t.replace(
    "        for (MethodNode method : verify.methods) {\n",
    "        for (Object methodObject : verify.methods) {\n"
    "            final MethodNode method = (MethodNode) methodObject;\n")
p.write_text(t, encoding="utf-8", newline="\n")
print("ASM5_RAW_LIST_CASTS_FIXED")