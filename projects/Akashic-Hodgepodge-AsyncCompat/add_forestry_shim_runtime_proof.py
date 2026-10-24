from pathlib import Path
p=Path(r"C:\Users\User\Desktop\Codex\Akashic-Hodgepodge-AsyncCompat\src\main\java\dev\akashic\asynciconcompat\forestry\ForestryGenomeC2Shim.java")
t=p.read_text(encoding="utf-8")
if "org.apache.logging.log4j.LogManager" not in t:
    t=t.replace(
        "import net.minecraft.nbt.NBTTagList;\n",
        "import net.minecraft.nbt.NBTTagList;\n"
        "import org.apache.logging.log4j.LogManager;\n"
        "import org.apache.logging.log4j.Logger;\n"
    )
if "private static final Logger LOG" not in t:
    t=t.replace(
        '    private static final String SLOT_TAG = "Slot";\n',
        '    private static final String SLOT_TAG = "Slot";\n'
        '    private static final Logger LOG =\n'
        '        LogManager.getLogger("AkashicAsyncCompat/ForestryC2");\n'
        '    private static volatile boolean executionLogged;\n'
    )
if "markShimExecution();" not in t:
    t=t.replace(
        "        ISpeciesRoot speciesRoot) {\n\n",
        "        ISpeciesRoot speciesRoot) {\n\n"
        "        markShimExecution();\n\n"
    )
if "private static void markShimExecution()" not in t:
    anchor="    private static void loadStoredChromosomes(\n"
    method='''    private static void markShimExecution() {
        if (executionLogged) {
            return;
        }

        synchronized (ForestryGenomeC2Shim.class) {
            if (executionLogged) {
                return;
            }

            executionLogged = true;
            System.setProperty(
                "akashic.forestryC2Shim.executed",
                "true");
            LOG.info(
                "ForestryGenomeC2Shim ACTIVE: using split "
                    + "Genome.getChromosomes implementation");
        }
    }

'''
    if anchor not in t:
        raise RuntimeError("mark insertion anchor missing")
    t=t.replace(anchor,method+anchor)
p.write_text(t,encoding="utf-8",newline="\n")
print("SHIM_RUNTIME_PROOF_ADDED")