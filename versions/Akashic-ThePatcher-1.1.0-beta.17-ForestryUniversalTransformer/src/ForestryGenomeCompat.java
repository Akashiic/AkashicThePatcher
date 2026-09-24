package com.akashic.thepatcher.module.forestry;

import forestry.api.genetics.IAllele;
import forestry.api.genetics.IAlleleSpecies;
import forestry.api.genetics.IChromosome;
import forestry.api.genetics.ISpeciesRoot;
import forestry.core.config.Config;
import forestry.core.genetics.Chromosome;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

/**
 * Semantics-preserving decomposition of Forestry 4.11.36
 * Genome.getChromosomes(NBTTagCompound, ISpeciesRoot).
 *
 * The original 288-byte method reproducibly crashes HotSpot 8u51 C2.
 * Keeping each unit small prevents that exact compilation shape while
 * retaining the original NBT mutation and validation behavior.
 */
public final class ForestryGenomeCompat {

    private static final String CHROMOSOMES_TAG = "Chromosomes";
    private static final String SLOT_TAG = "Slot";

    private ForestryGenomeCompat() {}

    public static IChromosome[] loadChromosomes(
        NBTTagCompound genomeNBT,
        ISpeciesRoot speciesRoot) {

        final NBTTagList chromosomesNBT =
            genomeNBT.func_150295_c(CHROMOSOMES_TAG, 10);
        final IChromosome[] chromosomes =
            new IChromosome[speciesRoot.getDefaultTemplate().length];

        for (int index = 0; index < chromosomesNBT.func_74745_c(); index++) {
            loadStoredChromosome(
                chromosomesNBT.func_150305_b(index),
                chromosomesNBT,
                speciesRoot,
                chromosomes);
        }

        fillMissingChromosomes(speciesRoot, chromosomes);
        return chromosomes;
    }

    private static void loadStoredChromosome(
        NBTTagCompound chromosomeNBT,
        NBTTagList chromosomesNBT,
        ISpeciesRoot speciesRoot,
        IChromosome[] chromosomes) {

        final byte slot = chromosomeNBT.func_74771_c(SLOT_TAG);
        if (slot < 0 || slot >= chromosomes.length) return;

        final Chromosome chromosome =
            Chromosome.loadChromosomeFromNBT(chromosomeNBT);
        chromosomes[slot] = chromosome;

        repairInvalidAlleles(
            chromosomeNBT,
            speciesRoot,
            slot,
            chromosome);

        validateChromosome(
            chromosomeNBT,
            chromosomesNBT,
            speciesRoot,
            slot,
            chromosome);
    }

    private static void repairInvalidAlleles(
        NBTTagCompound chromosomeNBT,
        ISpeciesRoot speciesRoot,
        int slot,
        Chromosome chromosome) {

        if (!Config.clearInvalidChromosomes) return;

        final IAllele defaultAllele =
            speciesRoot.getDefaultTemplate()[slot];
        final Class<? extends IAllele> chromosomeClass =
            speciesRoot.getKaryotype()[slot].getAlleleClass();

        if (chromosome.overrideInvalidAlleles(
            defaultAllele,
            chromosomeClass)) {

            chromosome.writeToNBT(chromosomeNBT);
        }
    }

    private static void validateChromosome(
        NBTTagCompound chromosomeNBT,
        NBTTagList chromosomesNBT,
        ISpeciesRoot speciesRoot,
        int slot,
        Chromosome chromosome) {

        final Class<? extends IAllele> chromosomeClass =
            speciesRoot.getKaryotype()[slot].getAlleleClass();

        if (!chromosome.hasInvalidAlleles(chromosomeClass)) return;

        throw new RuntimeException(
            "Found Chromosome with invalid Alleles.\nNBTTagCompound: "
                + chromosomesNBT
                + "\nSee config option \"genetics.clear.invalid.chromosomes\".\nMissing: "
                + chromosomeNBT);
    }

    private static void fillMissingChromosomes(
        ISpeciesRoot speciesRoot,
        IChromosome[] chromosomes) {

        final IChromosome speciesChromosome =
            chromosomes[speciesRoot.getKaryotypeKey().ordinal()];
        if (speciesChromosome == null) return;

        final IAlleleSpecies species =
            (IAlleleSpecies) speciesChromosome.getActiveAllele();
        final IAllele[] template =
            speciesRoot.getTemplate(species.getUID());

        for (int index = 0; index < chromosomes.length; index++) {
            final IAllele allele = template[index];
            if (chromosomes[index] == null && allele != null) {
                chromosomes[index] = new Chromosome(allele);
            }
        }
    }
}