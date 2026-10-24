import forestry.api.apiculture.EnumBeeChromosome;
import forestry.api.genetics.*;
import net.minecraft.nbt.*;
import java.lang.reflect.*;
import java.util.*;

/**
 * Drives the REAL Forestry 4.11.36 forestry.core.genetics.Genome.getChromosomes(NBTTagCompound, ISpeciesRoot)
 * (or any replacement installed by a transformer) hot, with realistic bee genomes, to make HotSpot compile it.
 * args: iterations [method owner: "genome" | "compat"]
 */
public final class GenomeRepro {
    static Object allele(final Class<?> type, final String uid, final boolean dominant) {
        return Proxy.newProxyInstance(GenomeRepro.class.getClassLoader(), new Class<?>[] { type }, new InvocationHandler() {
            public Object invoke(Object p, Method m, Object[] a) {
                String n = m.getName();
                if (n.equals("getUID")) return uid;
                if (n.equals("isDominant")) return dominant;
                if (n.equals("hashCode")) return uid.hashCode();
                if (n.equals("equals")) return p == a[0];
                if (n.equals("toString")) return uid;
                Class<?> r = m.getReturnType();
                if (r == boolean.class) return false;
                if (r == int.class) return 0;
                if (r == float.class) return 0f;
                return null;
            }
        });
    }

    public static void main(String[] args) throws Throwable {
        final int iterations = Integer.parseInt(args[0]);
        final EnumBeeChromosome[] karyotype = EnumBeeChromosome.values();
        final Map<String, IAllele> registry = new HashMap<String, IAllele>();
        final int speciesCount = 8;
        final IAllele[][] templates = new IAllele[speciesCount][];
        for (int s = 0; s < speciesCount; s++) {
            templates[s] = new IAllele[karyotype.length];
            for (int c = 0; c < karyotype.length; c++) {
                String uid = "test." + karyotype[c].name() + "." + (c == 0 ? s : s % 3);
                IAllele a = (IAllele) allele(karyotype[c].getAlleleClass(), uid, (s + c) % 2 == 0);
                templates[s][c] = a;
                registry.put(uid, a);
            }
        }
        AlleleManager.alleleRegistry = (IAlleleRegistry) Proxy.newProxyInstance(GenomeRepro.class.getClassLoader(),
            new Class<?>[] { IAlleleRegistry.class }, new InvocationHandler() {
                public Object invoke(Object p, Method m, Object[] a) {
                    if (m.getName().equals("getAllele")) return registry.get(a[0]);
                    return null;
                }
            });
        final Map<String, IAllele[]> byUid = new HashMap<String, IAllele[]>();
        for (IAllele[] t : templates) byUid.put(t[0].getUID(), t);
        final ISpeciesRoot root = (ISpeciesRoot) Proxy.newProxyInstance(GenomeRepro.class.getClassLoader(),
            new Class<?>[] { ISpeciesRoot.class }, new InvocationHandler() {
                public Object invoke(Object p, Method m, Object[] a) {
                    String n = m.getName();
                    if (n.equals("getDefaultTemplate")) return templates[0];
                    if (n.equals("getKaryotype")) return karyotype;
                    if (n.equals("getKaryotypeKey")) return EnumBeeChromosome.SPECIES;
                    if (n.equals("getTemplate")) return byUid.get(a[0]);
                    return null;
                }
            });

        // Genomes: full, partial (fill-from-template path), out-of-range slot.
        final NBTTagCompound[] genomes = new NBTTagCompound[speciesCount * 2];
        for (int g = 0; g < genomes.length; g++) {
            int s = g % speciesCount;
            NBTTagList list = new NBTTagList();
            int count = g < speciesCount ? karyotype.length : karyotype.length / 2;
            for (int c = 0; c < count; c++) {
                NBTTagCompound chr = new NBTTagCompound();
                chr.func_74774_a("Slot", (byte) c);
                chr.func_74778_a("UID0", templates[s][c].getUID());
                chr.func_74778_a("UID1", templates[(s + 1) % speciesCount][c].getUID());
                list.func_74742_a(chr);
            }
            NBTTagCompound bad = new NBTTagCompound();
            bad.func_74774_a("Slot", (byte) 99);
            list.func_74742_a(bad);
            NBTTagCompound genome = new NBTTagCompound();
            genome.func_74782_a("Chromosomes", list);
            genomes[g] = genome;
        }

        Class<?> genome = Class.forName("forestry.core.genetics.Genome");
        Method target = genome.getDeclaredMethod("getChromosomes", NBTTagCompound.class, ISpeciesRoot.class);
        target.setAccessible(true);
        long checksum = 0;
        for (int i = 0; i < iterations; i++) {
            NBTTagCompound g = genomes[i % genomes.length];
            IChromosome[] result = (IChromosome[]) target.invoke(null, g, root);
            if (result.length != karyotype.length) throw new AssertionError("length");
            for (int c = 0; c < result.length; c++) {
                if (result[c] == null) throw new AssertionError("null chromosome " + c + " at " + i);
            }
            checksum += result[0].getPrimaryAllele().getUID().length();
        }
        System.out.println("REPRO DONE iterations=" + iterations + " checksum=" + checksum);
    }
}
