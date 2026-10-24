package dev.akashic.asynciconcompat.forestry;

import forestry.api.genetics.IAllele;
import forestry.api.genetics.IAlleleSpecies;
import forestry.api.genetics.IChromosome;
import forestry.api.genetics.ISpeciesRoot;
import forestry.core.config.Config;
import forestry.core.genetics.Chromosome;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class ForestryGenomeC2Shim {

    private static final String CHROMOSOMES_TAG = "Chromosomes";
    private static final String SLOT_TAG = "Slot";
    private static final Logger LOG =
        LogManager.getLogger("AkashicAsyncCompat/ForestryC2");
    private static volatile boolean executionLogged;

    private ForestryGenomeC2Shim() {}

    public static IChromosome[] getChromosomes(
        NBTTagCompound genomeTag,
        ISpeciesRoot speciesRoot) {

        markShimExecution();

        NBTTagList chromosomeTags =
            genomeTag.getTagList(CHROMOSOMES_TAG, 10);

        IChromosome[] chromosomes =
            new IChromosome[speciesRoot.getDefaultTemplate().length];

        loadStoredChromosomes(
            chromosomeTags,
            speciesRoot,
            chromosomes);

        fillMissingChromosomes(
            speciesRoot,
            chromosomes);

        return chromosomes;
    }

    private static void markShimExecution() {
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

    private static void loadStoredChromosomes(
        NBTTagList chromosomeTags,
        ISpeciesRoot speciesRoot,
        IChromosome[] chromosomes) {

        for (int index = 0; index < chromosomeTags.tagCount(); index++) {
            NBTTagCompound chromosomeTag =
                chromosomeTags.getCompoundTagAt(index);

            int slot = chromosomeTag.getByte(SLOT_TAG);
            if (slot < 0 || slot >= chromosomes.length) {
                continue;
            }

            Chromosome chromosome =
                Chromosome.loadChromosomeFromNBT(chromosomeTag);

            chromosomes[slot] = chromosome;

            repairAndValidateChromosome(
                chromosomeTags,
                chromosomeTag,
                speciesRoot,
                chromosome,
                slot);
        }
    }

    private static void repairAndValidateChromosome(
        NBTTagList chromosomeTags,
        NBTTagCompound chromosomeTag,
        ISpeciesRoot speciesRoot,
        Chromosome chromosome,
        int slot) {

        if (Config.clearInvalidChromosomes) {
            IAllele defaultAllele =
                speciesRoot.getDefaultTemplate()[slot];

            Class<? extends IAllele> alleleClass =
                speciesRoot.getKaryotype()[slot].getAlleleClass();

            if (chromosome.overrideInvalidAlleles(
                defaultAllele,
                alleleClass)) {

                chromosome.writeToNBT(chromosomeTag);
            }
        }

        Class<? extends IAllele> validationClass =
            speciesRoot.getKaryotype()[slot].getAlleleClass();

        if (chromosome.hasInvalidAlleles(validationClass)) {
            throw new RuntimeException(
                "Found Chromosome with invalid Alleles.\n"
                    + "NBTTagCompound:"
                    + chromosomeTags
                    + "\nSee config option "
                    + "\"genetics.clear.invalid.chromosomes\".\n"
                    + "Missing:"
                    + chromosomeTag);
        }
    }

    private static void fillMissingChromosomes(
        ISpeciesRoot speciesRoot,
        IChromosome[] chromosomes) {

        IChromosome speciesChromosome =
            chromosomes[speciesRoot.getKaryotypeKey().ordinal()];

        if (speciesChromosome == null) {
            return;
        }

        IAlleleSpecies species =
            (IAlleleSpecies) speciesChromosome.getActiveAllele();

        IAllele[] template =
            speciesRoot.getTemplate(species.getUID());

        for (int index = 0; index < chromosomes.length; index++) {
            IAllele allele = template[index];

            if (chromosomes[index] == null && allele != null) {
                chromosomes[index] = new Chromosome(allele);
            }
        }
    }
}