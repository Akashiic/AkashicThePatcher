import forestry.api.genetics.*;
import forestry.core.genetics.Chromosome;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import com.akashic.thepatcher.module.forestry.ForestryGenomeCompat;

/**
 * Calls ForestryGenomeCompat.loadChromosomes hot enough to be C1/C2/OSR-compiled
 * and checks every result. Run with -XX:+PrintCompilation and grep for ForestryGenomeCompat.
 */
public final class GenomeCompatHarness {
    interface Species extends IAlleleSpecies {}

    static final class Type implements IChromosomeType {
        final int index;
        Type(int index) { this.index = index; }
        public Class<? extends IAllele> getAlleleClass() { return index == 0 ? Species.class : IAllele.class; }
        public int ordinal() { return index; }
    }

    public static void main(String[] args) {
        final int iterations = Integer.parseInt(args[0]);
        final Species species = new Species() { public String getUID() { return "forestry.speciesTest"; } };
        final IAllele stored = new IAllele() {};
        final IAllele templateOnly = new IAllele() {};
        final IAllele[] template = { species, stored, templateOnly };
        final IChromosomeType[] karyotype = { new Type(0), new Type(1), new Type(2) };
        Chromosome.alleles = new IAllele[] { species, stored };
        final ISpeciesRoot root = new ISpeciesRoot() {
            public IAllele[] getDefaultTemplate() { return template; }
            public IChromosomeType[] getKaryotype() { return karyotype; }
            public IChromosomeType getKaryotypeKey() { return karyotype[0]; }
            public IAllele[] getTemplate(String uid) { return template; }
        };
        final NBTTagCompound genome = new NBTTagCompound();
        genome.list = new NBTTagList();
        final NBTTagCompound slot0 = new NBTTagCompound();
        final NBTTagCompound slot1 = new NBTTagCompound();
        final NBTTagCompound outOfRange = new NBTTagCompound();
        slot0.slot = 0;
        slot1.slot = 1;
        outOfRange.slot = 7;
        genome.list.entries = new NBTTagCompound[] { slot0, slot1, outOfRange };

        long checksum = 0;
        for (int i = 0; i < iterations; i++) {
            final IChromosome[] result = ForestryGenomeCompat.loadChromosomes(genome, root);
            if (result.length != 3
                || result[0].getActiveAllele() != species
                || result[1].getActiveAllele() != stored
                || result[2].getActiveAllele() != templateOnly) {
                throw new AssertionError("wrong genome at iteration " + i);
            }
            checksum += result.length;
        }
        if (slot0.writes != 0 || slot1.writes != 0) throw new AssertionError("unexpected NBT repair");

        // Invalid allele path: repaired when clearInvalidChromosomes, otherwise the original RuntimeException.
        Chromosome.alleles = new IAllele[] { stored, stored };
        forestry.core.config.Config.clearInvalidChromosomes = false;
        try {
            ForestryGenomeCompat.loadChromosomes(genome, root);
            throw new AssertionError("invalid allele accepted");
        } catch (RuntimeException expected) {
            if (!expected.getMessage().startsWith("Found Chromosome with invalid Alleles.")) throw expected;
        }
        forestry.core.config.Config.clearInvalidChromosomes = true;
        final IChromosome[] repaired = ForestryGenomeCompat.loadChromosomes(genome, root);
        if (repaired[0].getActiveAllele() != species || slot0.writes != 1) throw new AssertionError("repair failed");

        System.out.println("HARNESS PASS iterations=" + iterations + " checksum=" + checksum);
    }
}
