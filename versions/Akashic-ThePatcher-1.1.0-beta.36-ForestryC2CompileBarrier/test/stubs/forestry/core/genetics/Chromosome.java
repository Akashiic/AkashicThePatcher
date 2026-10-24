package forestry.core.genetics;

import forestry.api.genetics.IAllele;
import forestry.api.genetics.IChromosome;
import net.minecraft.nbt.NBTTagCompound;

/** Test stub: the stored allele is chosen by the NBT slot. */
public class Chromosome implements IChromosome {
    public static IAllele[] alleles;
    public IAllele active;
    public boolean invalid;
    public Chromosome(IAllele allele) { active = allele; }
    public static Chromosome loadChromosomeFromNBT(NBTTagCompound nbt) { return new Chromosome(alleles[nbt.slot]); }
    public IAllele getActiveAllele() { return active; }
    public boolean overrideInvalidAlleles(IAllele fallback, Class<? extends IAllele> type) {
        if (!type.isInstance(active)) { active = fallback; return true; }
        return false;
    }
    public void writeToNBT(NBTTagCompound nbt) { nbt.writes++; }
    public boolean hasInvalidAlleles(Class<? extends IAllele> type) { return !type.isInstance(active); }
}
