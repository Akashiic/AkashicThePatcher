import forestry.api.apiculture.EnumBeeChromosome;
import forestry.api.arboriculture.EnumTreeChromosome;
import forestry.api.lepidopterology.EnumButterflyChromosome;
import forestry.api.genetics.*;
import forestry.core.config.Config;
import net.minecraft.nbt.*;
import java.lang.reflect.*;
import java.util.*;

/**
 * Game-like, profile-polluting driver for Forestry 4.11.36 Genome.getChromosomes(NBTTagCompound, ISpeciesRoot):
 * three species roots (distinct receiver classes), full / partial / invalid-allele / bad-slot / no-species genomes,
 * Config.clearInvalidChromosomes toggling and the RuntimeException path. Compares every result with a reference model.
 * args: iterations seed
 */
public final class GenomeChaos {
    static final ClassLoader CL = GenomeChaos.class.getClassLoader();
    static final Map<String, IAllele> REGISTRY = new HashMap<String, IAllele>();

    static IAllele allele(Class<?> type, final String uid, final boolean dominant) {
        if (REGISTRY.containsKey(uid)) return REGISTRY.get(uid);
        IAllele a = (IAllele) Proxy.newProxyInstance(CL, new Class<?>[] { type }, new InvocationHandler() {
            public Object invoke(Object p, Method m, Object[] args) {
                String n = m.getName();
                if (n.equals("getUID")) return uid;
                if (n.equals("isDominant")) return dominant;
                if (n.equals("hashCode")) return uid.hashCode();
                if (n.equals("equals")) return p == args[0];
                if (n.equals("toString")) return uid;
                Class<?> r = m.getReturnType();
                if (r == boolean.class) return false;
                if (r == int.class) return 0;
                if (r == float.class) return 0f;
                return null;
            }
        });
        REGISTRY.put(uid, a);
        return a;
    }

    static final class Root {
        final IChromosomeType[] karyotype;
        final IAllele[][] templates;
        final Map<String, IAllele[]> byUid = new HashMap<String, IAllele[]>();
        final IAllele foreign;
        final ISpeciesRoot proxy;

        Root(String name, IChromosomeType[] karyotype, int species, Class<?> marker) {
            this.karyotype = karyotype;
            templates = new IAllele[species][karyotype.length];
            for (int s = 0; s < species; s++) {
                for (int c = 0; c < karyotype.length; c++) {
                    templates[s][c] = allele(karyotype[c].getAlleleClass(), name + "." + c + "." + (c == 0 ? s : s % 3), (s + c) % 2 == 0);
                }
                byUid.put(templates[s][0].getUID(), templates[s]);
            }
            foreign = allele(IAllele.class, name + ".foreign", true); // valid IAllele, wrong subtype for every typed slot
            proxy = (ISpeciesRoot) Proxy.newProxyInstance(CL, new Class<?>[] { ISpeciesRoot.class, marker }, new InvocationHandler() {
                public Object invoke(Object p, Method m, Object[] a) {
                    String n = m.getName();
                    if (n.equals("getDefaultTemplate")) return templates[0];
                    if (n.equals("getKaryotype")) return Root.this.karyotype;
                    if (n.equals("getKaryotypeKey")) return Root.this.karyotype[0];
                    if (n.equals("getTemplate")) return byUid.get(a[0]);
                    if (n.equals("hashCode")) return 1;
                    if (n.equals("equals")) return p == a[0];
                    return null;
                }
            });
        }
    }

    static NBTTagCompound chromosome(int slot, String uid0, String uid1) {
        NBTTagCompound c = new NBTTagCompound();
        c.func_74774_a("Slot", (byte) slot);
        c.func_74778_a("UID0", uid0);
        c.func_74778_a("UID1", uid1);
        return c;
    }

    public static void main(String[] args) throws Throwable {
        final int iterations = Integer.parseInt(args[0]);
        final Random random = new Random(Long.parseLong(args[1]));
        final Root[] roots = {
            new Root("bee", EnumBeeChromosome.values(), 24, Runnable.class),
            new Root("tree", EnumTreeChromosome.values(), 16, Comparable.class),
            new Root("butterfly", EnumButterflyChromosome.values(), 12, Cloneable.class),
        };
        AlleleManager.alleleRegistry = (IAlleleRegistry) Proxy.newProxyInstance(CL, new Class<?>[] { IAlleleRegistry.class },
            new InvocationHandler() {
                public Object invoke(Object p, Method m, Object[] a) {
                    return m.getName().equals("getAllele") ? REGISTRY.get(a[0]) : null;
                }
            });
        Method target = Class.forName("forestry.core.genetics.Genome")
            .getDeclaredMethod("getChromosomes", NBTTagCompound.class, ISpeciesRoot.class);
        target.setAccessible(true);

        long ok = 0, thrown = 0, repaired = 0;
        final java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
        for (int i = 0; i < iterations; i++) {
            if (i % 20000 == 0) Config.clearInvalidChromosomes = random.nextInt(3) != 0;
            Root root = roots[random.nextInt(roots.length)];
            int s = random.nextInt(root.templates.length);
            IAllele[] t = root.templates[s];
            int kind = random.nextInt(10);
            NBTTagList list = new NBTTagList();
            int invalidSlot = -1;
            boolean noSpecies = kind == 7;
            int count = kind == 1 ? random.nextInt(t.length) : t.length;
            for (int c = noSpecies ? 1 : 0; c < count; c++) {
                String uid0 = t[c].getUID();
                if (kind == 2 && c == 1 + (i % (t.length - 1)) && !root.karyotype[c].getAlleleClass().isInstance(root.foreign)) { uid0 = root.foreign.getUID(); invalidSlot = c; }
                list.func_74742_a(chromosome(c, uid0, root.templates[random.nextInt(root.templates.length)][c].getUID()));
            }
            if (kind == 3) list.func_74742_a(chromosome(-1 - random.nextInt(5), t[0].getUID(), t[0].getUID()));
            if (kind == 4) list.func_74742_a(chromosome(t.length + random.nextInt(50), t[0].getUID(), t[0].getUID()));
            if (kind == 5) Collections.shuffle(list.list, random);
            NBTTagCompound genome = new NBTTagCompound();
            if (kind != 6) genome.func_74782_a("Chromosomes", list);

            IChromosome[] result;
            try {
                result = (IChromosome[]) target.invoke(null, genome, root.proxy);
            } catch (InvocationTargetException e) {
                if (!(e.getCause() instanceof RuntimeException) || invalidSlot < 0 || Config.clearInvalidChromosomes
                    || !e.getCause().getMessage().startsWith("Found Chromosome with invalid Alleles.")) throw e.getCause();
                thrown++;
                digest.update(("X" + e.getCause().getMessage() + genome).getBytes("UTF-8"));
                continue;
            }
            StringBuilder sb = new StringBuilder("R").append(result.length);
            for (IChromosome chr : result) {
                sb.append('|').append(chr == null ? "null" : chr.getClass().getName() + ":" + chr.getPrimaryAllele().getUID() + "/" + chr.getSecondaryAllele().getUID());
            }
            digest.update(sb.append(genome).toString().getBytes("UTF-8"));
            if (invalidSlot >= 0 && !Config.clearInvalidChromosomes) throw new AssertionError("expected exception at " + i);
            // Reference model of the original method.
            if (result.length != root.karyotype.length) throw new AssertionError("length at " + i);
            boolean speciesPresent = result[0] != null;
            IAllele[] fill = speciesPresent ? root.byUid.get(result[0].getActiveAllele().getUID()) : null;
            for (int c = 0; c < result.length; c++) {
                boolean stored = kind != 6 && c < count && !(noSpecies && c == 0);
                if (stored) {
                    IAllele expected = c == invalidSlot ? t[c] /* repaired to default? */ : t[c];
                    IAllele primary = result[c].getPrimaryAllele();
                    if (c == invalidSlot) {
                        if (primary == root.foreign || !root.karyotype[c].getAlleleClass().isInstance(primary)) throw new AssertionError("repair at " + i + " slot " + c);
                        repaired++;
                    } else if (primary != expected) throw new AssertionError("primary at " + i + " slot " + c);
                } else if (speciesPresent) {
                    if (result[c] == null || result[c].getPrimaryAllele() != fill[c]) throw new AssertionError("fill at " + i + " slot " + c);
                } else if (result[c] != null) {
                    throw new AssertionError("unexpected chromosome at " + i + " slot " + c);
                }
            }
            ok++;
        }
        System.out.println("CHAOS DONE iterations=" + iterations + " ok=" + ok + " thrown=" + thrown + " repaired=" + repaired
            + " digest=" + new java.math.BigInteger(1, digest.digest()).toString(16));
    }
}
