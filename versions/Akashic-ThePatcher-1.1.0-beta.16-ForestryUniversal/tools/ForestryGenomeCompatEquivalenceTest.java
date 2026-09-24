package com.akashic.compatlab.tools;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;

import forestry.api.genetics.IAllele;
import forestry.api.genetics.IChromosome;
import forestry.api.genetics.IChromosomeType;
import forestry.api.genetics.ISpeciesRoot;
import forestry.core.genetics.Genome;

import com.akashic.thepatcher.module.forestry.ForestryGenomeCompat;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

public final class ForestryGenomeCompatEquivalenceTest {

    private ForestryGenomeCompatEquivalenceTest() {}

    public static void main(String[] args) throws Exception {
        testEmptyChromosomeList();
        testInvalidSlotsAreIgnored();
        System.out.println(
            "FORESTRY_GENOME_COMPAT_EQUIVALENCE PASS cases=2");
    }

    private static void testEmptyChromosomeList() throws Exception {
        NBTTagCompound genome = new NBTTagCompound();
        genome.func_74782_a("Chromosomes", new NBTTagList());

        ISpeciesRoot root = fakeRoot(3, 0);

        assertEquivalent("empty", genome, root);
    }

    private static void testInvalidSlotsAreIgnored() throws Exception {
        NBTTagCompound genome = new NBTTagCompound();
        NBTTagList list = new NBTTagList();

        NBTTagCompound negative = new NBTTagCompound();
        negative.func_74774_a("Slot", (byte) -1);
        list.func_74742_a(negative);

        NBTTagCompound tooLarge = new NBTTagCompound();
        tooLarge.func_74774_a("Slot", (byte) 7);
        list.func_74742_a(tooLarge);

        genome.func_74782_a("Chromosomes", list);

        ISpeciesRoot root = fakeRoot(3, 0);

        assertEquivalent("invalid-slots", genome, root);
    }

    private static void assertEquivalent(
        String label,
        NBTTagCompound genome,
        ISpeciesRoot root) throws Exception {

        Method original = Genome.class.getDeclaredMethod(
            "getChromosomes",
            NBTTagCompound.class,
            ISpeciesRoot.class);
        original.setAccessible(true);

        IChromosome[] expected =
            (IChromosome[]) original.invoke(null, genome, root);
        IChromosome[] actual =
            ForestryGenomeCompat.loadChromosomes(genome, root);

        if (expected.length != actual.length) {
            throw new AssertionError(
                label + ": length "
                    + expected.length + " != " + actual.length);
        }

        if (!Arrays.equals(expected, actual)) {
            throw new AssertionError(
                label + ": chromosome arrays differ");
        }

        for (IChromosome chromosome : actual) {
            if (chromosome != null) {
                throw new AssertionError(
                    label + ": expected all-null output");
            }
        }
    }

    private static ISpeciesRoot fakeRoot(
        final int templateLength,
        final int keyOrdinal) {

        final IChromosomeType type =
            (IChromosomeType) Proxy.newProxyInstance(
                ForestryGenomeCompatEquivalenceTest.class.getClassLoader(),
                new Class<?>[] {IChromosomeType.class},
                new InvocationHandler() {
                    @Override
                    public Object invoke(
                        Object proxy,
                        Method method,
                        Object[] args) {

                        String name = method.getName();
                        if ("ordinal".equals(name)) {
                            return Integer.valueOf(keyOrdinal);
                        }
                        if ("getAlleleClass".equals(name)) {
                            return IAllele.class;
                        }
                        if ("getName".equals(name)) {
                            return "fake";
                        }
                        return defaultValue(method.getReturnType());
                    }
                });

        return (ISpeciesRoot) Proxy.newProxyInstance(
            ForestryGenomeCompatEquivalenceTest.class.getClassLoader(),
            new Class<?>[] {ISpeciesRoot.class},
            new InvocationHandler() {
                @Override
                public Object invoke(
                    Object proxy,
                    Method method,
                    Object[] args) {

                    String name = method.getName();
                    if ("getDefaultTemplate".equals(name)) {
                        return new IAllele[templateLength];
                    }
                    if ("getKaryotypeKey".equals(name)) {
                        return type;
                    }
                    if ("getKaryotype".equals(name)) {
                        IChromosomeType[] result =
                            new IChromosomeType[templateLength];
                        Arrays.fill(result, type);
                        return result;
                    }
                    if ("getUID".equals(name)) {
                        return "fake-root";
                    }
                    return defaultValue(method.getReturnType());
                }
            });
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == Boolean.TYPE) return Boolean.FALSE;
        if (type == Byte.TYPE) return Byte.valueOf((byte) 0);
        if (type == Short.TYPE) return Short.valueOf((short) 0);
        if (type == Integer.TYPE) return Integer.valueOf(0);
        if (type == Long.TYPE) return Long.valueOf(0L);
        if (type == Float.TYPE) return Float.valueOf(0.0F);
        if (type == Double.TYPE) return Double.valueOf(0.0D);
        if (type == Character.TYPE) return Character.valueOf((char) 0);
        return null;
    }
}