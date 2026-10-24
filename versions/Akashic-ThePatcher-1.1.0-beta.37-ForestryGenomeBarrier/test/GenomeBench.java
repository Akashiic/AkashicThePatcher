import forestry.api.apiculture.EnumBeeChromosome;
import forestry.api.genetics.*;
import net.minecraft.nbt.*;
import java.lang.invoke.*;
import java.lang.reflect.*;
import java.util.*;

/**
 * Per-call cost of Forestry's Genome.getChromosomes on a full 15-chromosome bee genome.
 * Calls through a constant MethodHandle (no reflection overhead). args: seconds
 */
public final class GenomeBench {
    static final Map<String, IAllele> REGISTRY = new HashMap<String, IAllele>();

    static IAllele allele(Class<?> type, final String uid) {
        IAllele a = (IAllele) Proxy.newProxyInstance(GenomeBench.class.getClassLoader(), new Class<?>[] { type },
            new InvocationHandler() {
                public Object invoke(Object p, Method m, Object[] args) {
                    String n = m.getName();
                    if (n.equals("getUID")) return uid;
                    if (n.equals("isDominant")) return true;
                    if (n.equals("hashCode")) return uid.hashCode();
                    if (n.equals("equals")) return p == args[0];
                    return m.getReturnType() == boolean.class ? Boolean.FALSE : null;
                }
            });
        REGISTRY.put(uid, a);
        return a;
    }

    public static void main(String[] args) throws Throwable {
        final double seconds = Double.parseDouble(args[0]);
        final EnumBeeChromosome[] karyotype = EnumBeeChromosome.values();
        final IAllele[] template = new IAllele[karyotype.length];
        for (int c = 0; c < karyotype.length; c++) template[c] = allele(karyotype[c].getAlleleClass(), "bench." + c);
        AlleleManager.alleleRegistry = (IAlleleRegistry) Proxy.newProxyInstance(GenomeBench.class.getClassLoader(),
            new Class<?>[] { IAlleleRegistry.class }, new InvocationHandler() {
                public Object invoke(Object p, Method m, Object[] a) {
                    return m.getName().equals("getAllele") ? REGISTRY.get(a[0]) : null;
                }
            });
        // Same proxy-backed root for both runs; only the Genome method differs.
        final ISpeciesRoot root = (ISpeciesRoot) Proxy.newProxyInstance(GenomeBench.class.getClassLoader(),
            new Class<?>[] { ISpeciesRoot.class }, new InvocationHandler() {
                public Object invoke(Object p, Method m, Object[] a) {
                    String n = m.getName();
                    if (n.equals("getDefaultTemplate") || n.equals("getTemplate")) return template;
                    if (n.equals("getKaryotype")) return karyotype;
                    if (n.equals("getKaryotypeKey")) return EnumBeeChromosome.SPECIES;
                    return null;
                }
            });
        NBTTagList list = new NBTTagList();
        for (int c = 0; c < karyotype.length; c++) {
            NBTTagCompound chr = new NBTTagCompound();
            chr.func_74774_a("Slot", (byte) c);
            chr.func_74778_a("UID0", template[c].getUID());
            chr.func_74778_a("UID1", template[c].getUID());
            list.func_74742_a(chr);
        }
        final NBTTagCompound genome = new NBTTagCompound();
        genome.func_74782_a("Chromosomes", list);

        Method m = Class.forName("forestry.core.genetics.Genome")
            .getDeclaredMethod("getChromosomes", NBTTagCompound.class, ISpeciesRoot.class);
        m.setAccessible(true);
        final MethodHandle handle = MethodHandles.lookup().unreflect(m);

        long sink = 0;
        long calls = 0;
        // warm-up
        long end = System.nanoTime() + (long) (seconds * 0.3e9);
        while (System.nanoTime() < end) { sink += ((IChromosome[]) handle.invokeExact(genome, root)).length; calls++; }
        calls = 0;
        long start = System.nanoTime();
        end = start + (long) (seconds * 1e9);
        long now;
        do {
            for (int i = 0; i < 1000; i++) sink += ((IChromosome[]) handle.invokeExact(genome, root)).length;
            calls += 1000;
        } while ((now = System.nanoTime()) < end);
        System.out.printf(Locale.ROOT, "BENCH ns/call=%.0f calls=%d sink=%d%n", (now - start) / (double) calls, calls, sink);
    }
}
