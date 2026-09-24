package dev.akashic.asynciconcompat.mixins;

import forestry.api.genetics.IChromosome;
import forestry.api.genetics.ISpeciesRoot;
import forestry.core.genetics.Genome;
import net.minecraft.nbt.NBTTagCompound;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import dev.akashic.asynciconcompat.forestry.ForestryGenomeC2Shim;

@Mixin(value = Genome.class, remap = false)
public abstract class MixinForestryGenomeC2 {

    /**
     * Replaces Forestry 4.11.36's private 288-byte implementation with a
     * structurally smaller delegation. The original algorithm is preserved in
     * split helper methods inside ForestryGenomeC2Shim.
     *
     * @author Rede Akashic
     * @reason Java 8u51 C2 crashes natively while compiling the original
     *         Genome.getChromosomes implementation.
     */
    @Overwrite(remap = false)
    private static IChromosome[] getChromosomes(
        NBTTagCompound genomeTag,
        ISpeciesRoot speciesRoot) {

        return ForestryGenomeC2Shim.getChromosomes(
            genomeTag,
            speciesRoot);
    }
}