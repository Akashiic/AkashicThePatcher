package forestry.core.genetics;

import com.google.common.base.Objects;
import com.google.common.base.Objects.ToStringHelper;
import forestry.api.apiculture.EnumBeeChromosome;
import forestry.api.genetics.IAllele;
import forestry.api.genetics.IAlleleSpecies;
import forestry.api.genetics.IChromosome;
import forestry.api.genetics.IChromosomeType;
import forestry.api.genetics.IGenome;
import forestry.api.genetics.ISpeciesRoot;
import forestry.api.lepidopterology.EnumButterflyChromosome;
import forestry.core.config.Config;
import forestry.core.genetics.Chromosome;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

public abstract class Genome implements IGenome {

   private static final String SLOT_TAG = "Slot";
   private static final Set unusedChromsomes = new HashSet();
   private IChromosome[] chromosomes;


   protected Genome(NBTTagCompound nbttagcompound) {
      this.chromosomes = new Chromosome[this.getDefaultTemplate().length];
      this.readFromNBT(nbttagcompound);
   }

   protected Genome(IChromosome[] chromosomes) {
      this.checkChromosomes(chromosomes);
      this.chromosomes = chromosomes;
   }

   private void checkChromosomes(IChromosome[] chromosomes) {
      if(chromosomes != null && chromosomes.length == this.getDefaultTemplate().length) {
         IChromosomeType[] var10 = this.getSpeciesRoot().getKaryotype();

         for(int i = 0; i < var10.length; ++i) {
            IChromosomeType chromosomeType = var10[i];
            if(!unusedChromsomes.contains(chromosomeType)) {
               IChromosome chromosome = chromosomes[i];
               if(chromosome == null) {
                  String var11 = String.format("Tried to create a genome for \'%s\' from an invalid chromosome template. Missing chromosome \'%s\'.\n%s", new Object[]{this.getSpeciesRoot().getUID(), chromosomeType.getName(), this.chromosomesToString(chromosomes)});
                  throw new IllegalArgumentException(var11);
               }

               IAllele primary = chromosome.getPrimaryAllele();
               if(primary == null) {
                  String var12 = String.format("Tried to create a genome for \'%s\' from an invalid chromosome template. Missing primary allele for \'%s\'.\n%s", new Object[]{this.getSpeciesRoot().getUID(), chromosomeType.getName(), this.chromosomesToString(chromosomes)});
                  throw new IllegalArgumentException(var12);
               }

               IAllele secondary = chromosome.getSecondaryAllele();
               if(secondary == null) {
                  String var13 = String.format("Tried to create a genome for \'%s\' from an invalid chromosome template. Missing secondary allele for \'%s\'.\n%s", new Object[]{this.getSpeciesRoot().getUID(), chromosomeType.getName(), this.chromosomesToString(chromosomes)});
                  throw new IllegalArgumentException(var13);
               }

               Class chromosomeAlleleClass = chromosomeType.getAlleleClass();
               String message;
               if(!chromosomeAlleleClass.isAssignableFrom(primary.getClass())) {
                  message = String.format("Tried to create a genome for \'%s\' from an invalid chromosome template. Incorrect type for primary allele \'%s\'.\n%s.", new Object[]{this.getSpeciesRoot().getUID(), chromosomeType.getName(), this.chromosomesToString(chromosomes)});
                  throw new IllegalArgumentException(message);
               }

               if(!chromosomeAlleleClass.isAssignableFrom(secondary.getClass())) {
                  message = String.format("Tried to create a genome for \'%s\' from an invalid chromosome template. Incorrect type for secondary allele \'%s\'.\n%s.", new Object[]{this.getSpeciesRoot().getUID(), chromosomeType.getName(), this.chromosomesToString(chromosomes)});
                  throw new IllegalArgumentException(message);
               }
            }
         }

      } else {
         String karyotype = String.format("Tried to create a genome for \'%s\' from an invalid chromosome template.\n%s", new Object[]{this.getSpeciesRoot().getUID(), this.chromosomesToString(chromosomes)});
         throw new IllegalArgumentException(karyotype);
      }
   }

   private String chromosomesToString(IChromosome[] chromosomes) {
      if(chromosomes == null) {
         return "null";
      } else {
         StringBuilder stringBuilder = new StringBuilder();
         IChromosomeType[] karyotype = this.getSpeciesRoot().getKaryotype();

         for(int i = 0; i < chromosomes.length; ++i) {
            IChromosomeType chromosomeType = karyotype[i];
            if(!unusedChromsomes.contains(chromosomeType)) {
               IChromosome chromosome = chromosomes[i];
               stringBuilder.append(chromosomeType.getName()).append(": ").append(chromosome).append("\n");
            }
         }

         return stringBuilder.toString();
      }
   }

   private IAllele[] getDefaultTemplate() {
      return this.getSpeciesRoot().getDefaultTemplate();
   }

   protected static IAlleleSpecies getSpeciesDirectly(ItemStack itemStack) {
      NBTTagCompound nbtTagCompound = itemStack.func_77978_p();
      if(nbtTagCompound == null) {
         return null;
      } else {
         NBTTagCompound genomeNBT = nbtTagCompound.func_74775_l("Genome");
         if(genomeNBT == null) {
            return null;
         } else {
            NBTTagList chromosomesNBT = genomeNBT.func_150295_c("Chromosomes", 10);
            if(chromosomesNBT == null) {
               return null;
            } else {
               NBTTagCompound chromosomeNBT = chromosomesNBT.func_150305_b(0);
               Chromosome chromosome = Chromosome.loadChromosomeFromNBT(chromosomeNBT);
               IAllele activeAllele = chromosome.getActiveAllele();
               return !(activeAllele instanceof IAlleleSpecies)?null:(IAlleleSpecies)activeAllele;
            }
         }
      }
   }

   private static IChromosome getChromosome(ItemStack itemStack, IChromosomeType chromosomeType, ISpeciesRoot speciesRoot) {
      NBTTagCompound nbtTagCompound = itemStack.func_77978_p();
      if(nbtTagCompound == null) {
         return null;
      } else {
         NBTTagCompound genome = nbtTagCompound.func_74775_l("Genome");
         if(genome == null) {
            return null;
         } else {
            IChromosome[] chromosomes = getChromosomes(genome, speciesRoot);
            return chromosomes == null?null:chromosomes[chromosomeType.ordinal()];
         }
      }
   }

   private static IChromosome[] getChromosomes(NBTTagCompound genomeNBT, ISpeciesRoot speciesRoot) {
      NBTTagList chromosomesNBT = genomeNBT.func_150295_c("Chromosomes", 10);
      IChromosome[] chromosomes = new IChromosome[speciesRoot.getDefaultTemplate().length];

      IAllele allele;
      for(int speciesChromosome = 0; speciesChromosome < chromosomesNBT.func_74745_c(); ++speciesChromosome) {
         NBTTagCompound species = chromosomesNBT.func_150305_b(speciesChromosome);
         byte template = species.func_74771_c("Slot");
         if(template >= 0 && template < chromosomes.length) {
            Chromosome i = Chromosome.loadChromosomeFromNBT(species);
            chromosomes[template] = i;
            if(Config.clearInvalidChromosomes) {
               allele = speciesRoot.getDefaultTemplate()[template];
               Class chromosomeClass = speciesRoot.getKaryotype()[template].getAlleleClass();
               if(i.overrideInvalidAlleles(allele, chromosomeClass)) {
                  i.writeToNBT(species);
               }
            }

            if(i.hasInvalidAlleles(speciesRoot.getKaryotype()[template].getAlleleClass())) {
               throw new RuntimeException("Found Chromosome with invalid Alleles.\nNBTTagCompound: " + chromosomesNBT + "\nSee config option \"genetics.clear.invalid.chromosomes\".\nMissing: " + species);
            }
         }
      }

      IChromosome var10 = chromosomes[speciesRoot.getKaryotypeKey().ordinal()];
      if(var10 != null) {
         IAlleleSpecies var11 = (IAlleleSpecies)var10.getActiveAllele();
         IAllele[] var12 = speciesRoot.getTemplate(var11.getUID());

         for(int var13 = 0; var13 < chromosomes.length; ++var13) {
            allele = var12[var13];
            if(chromosomes[var13] == null && allele != null) {
               chromosomes[var13] = new Chromosome(allele);
            }
         }
      }

      return chromosomes;
   }

   protected static IAllele getActiveAllele(ItemStack itemStack, IChromosomeType chromosomeType, ISpeciesRoot speciesRoot) {
      IChromosome chromosome = getChromosome(itemStack, chromosomeType, speciesRoot);
      return chromosome == null?null:chromosome.getActiveAllele();
   }

   public void readFromNBT(NBTTagCompound nbttagcompound) {
      this.chromosomes = getChromosomes(nbttagcompound, this.getSpeciesRoot());
   }

   public void writeToNBT(NBTTagCompound nbttagcompound) {
      NBTTagList nbttaglist = new NBTTagList();

      for(int i = 0; i < this.chromosomes.length; ++i) {
         if(this.chromosomes[i] != null) {
            NBTTagCompound nbttagcompound1 = new NBTTagCompound();
            nbttagcompound1.func_74774_a("Slot", (byte)i);
            this.chromosomes[i].writeToNBT(nbttagcompound1);
            nbttaglist.func_74742_a(nbttagcompound1);
         }
      }

      nbttagcompound.func_74782_a("Chromosomes", nbttaglist);
   }

   public IChromosome[] getChromosomes() {
      return (IChromosome[])Arrays.copyOf(this.chromosomes, this.chromosomes.length);
   }

   public IAllele getActiveAllele(IChromosomeType chromosomeType) {
      return this.chromosomes[chromosomeType.ordinal()].getActiveAllele();
   }

   public IAllele getInactiveAllele(IChromosomeType chromosomeType) {
      return this.chromosomes[chromosomeType.ordinal()].getInactiveAllele();
   }

   public boolean isGeneticEqual(IGenome other) {
      IChromosome[] genetics = other.getChromosomes();
      if(this.chromosomes.length != genetics.length) {
         return false;
      } else {
         for(int i = 0; i < this.chromosomes.length; ++i) {
            IChromosome chromosome = this.chromosomes[i];
            IChromosome otherChromosome = genetics[i];
            if(chromosome != null || otherChromosome != null) {
               if(chromosome == null || otherChromosome == null) {
                  return false;
               }

               if(!chromosome.getPrimaryAllele().getUID().equals(otherChromosome.getPrimaryAllele().getUID())) {
                  return false;
               }

               if(!chromosome.getSecondaryAllele().getUID().equals(otherChromosome.getSecondaryAllele().getUID())) {
                  return false;
               }
            }
         }

         return true;
      }
   }

   public String toString() {
      ToStringHelper toStringHelper = Objects.toStringHelper(this);
      int i = 0;
      IChromosome[] var3 = this.chromosomes;
      int var4 = var3.length;

      for(int var5 = 0; var5 < var4; ++var5) {
         IChromosome chromosome = var3[var5];
         toStringHelper.add(String.valueOf(i++), chromosome);
      }

      return toStringHelper.toString();
   }

   static {
      unusedChromsomes.add(EnumBeeChromosome.HUMIDITY);
      unusedChromsomes.add(EnumButterflyChromosome.TERRITORY);
   }
}
