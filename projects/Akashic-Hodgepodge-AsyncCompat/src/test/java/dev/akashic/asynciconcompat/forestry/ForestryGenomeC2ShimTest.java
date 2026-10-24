package dev.akashic.asynciconcompat.forestry;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import forestry.api.genetics.IAllele;
import forestry.api.genetics.IChromosome;
import forestry.api.genetics.IChromosomeType;
import forestry.api.genetics.ISpeciesRoot;
import forestry.core.genetics.Genome;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import org.junit.BeforeClass;
import org.junit.Test;

public class ForestryGenomeC2ShimTest {

    private static Method originalGetChromosomes;

    @BeforeClass
    public static void locateOriginalMethod() throws Exception {
        originalGetChromosomes = Genome.class.getDeclaredMethod(
            "getChromosomes",
            NBTTagCompound.class,
            ISpeciesRoot.class);
        originalGetChromosomes.setAccessible(true);
    }

    @Test
    public void emptyGenomeMatchesOriginal() throws Throwable {
        NBTTagCompound genomeTag = new NBTTagCompound();
        ISpeciesRoot speciesRoot = speciesRoot(4);

        assertArrayEquals(
            invokeOriginal(genomeTag, speciesRoot),
            ForestryGenomeC2Shim.getChromosomes(
                genomeTag,
                speciesRoot));
    }

    @Test
    public void invalidSlotsMatchOriginal() throws Throwable {
        NBTTagCompound genomeTag = new NBTTagCompound();
        NBTTagList chromosomeTags = new NBTTagList();

        chromosomeTags.appendTag(slotTag(-1));
        chromosomeTags.appendTag(slotTag(4));
        chromosomeTags.appendTag(slotTag(127));

        genomeTag.setTag("Chromosomes", chromosomeTags);

        ISpeciesRoot speciesRoot = speciesRoot(4);

        assertArrayEquals(
            invokeOriginal(genomeTag, speciesRoot),
            ForestryGenomeC2Shim.getChromosomes(
                genomeTag,
                speciesRoot));
    }

    @Test
    public void nullGenomeFailureMatchesOriginal() {
        ISpeciesRoot speciesRoot = speciesRoot(2);

        assertEquals(
            captureFailure(null, speciesRoot, true),
            captureFailure(null, speciesRoot, false));
    }

    @Test
    public void nullRootFailureMatchesOriginal() {
        NBTTagCompound genomeTag = new NBTTagCompound();

        assertEquals(
            captureFailure(genomeTag, null, true),
            captureFailure(genomeTag, null, false));
    }

    private static NBTTagCompound slotTag(int slot) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setByte("Slot", (byte) slot);
        return tag;
    }

    private static IChromosome[] invokeOriginal(
        NBTTagCompound genomeTag,
        ISpeciesRoot speciesRoot) throws Throwable {

        try {
            return (IChromosome[]) originalGetChromosomes.invoke(
                null,
                genomeTag,
                speciesRoot);
        } catch (InvocationTargetException exception) {
            throw exception.getCause();
        }
    }

    private static Class<?> captureFailure(
        NBTTagCompound genomeTag,
        ISpeciesRoot speciesRoot,
        boolean original) {

        try {
            if (original) {
                invokeOriginal(genomeTag, speciesRoot);
            } else {
                ForestryGenomeC2Shim.getChromosomes(
                    genomeTag,
                    speciesRoot);
            }
            return null;
        } catch (Throwable throwable) {
            return throwable.getClass();
        }
    }

    private static ISpeciesRoot speciesRoot(final int length) {
        final IChromosomeType keyType = chromosomeType();

        InvocationHandler handler = (proxy, method, args) -> {
            switch (method.getName()) {
                case "getDefaultTemplate":
                    return new IAllele[length];
                case "getKaryotypeKey":
                    return keyType;
                case "getKaryotype":
                    IChromosomeType[] karyotype =
                        new IChromosomeType[length];
                    for (int index = 0; index < length; index++) {
                        karyotype[index] = keyType;
                    }
                    return karyotype;
                case "getTemplate":
                    return new IAllele[length];
                case "toString":
                    return "AkashicTestSpeciesRoot";
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "equals":
                    return proxy == args[0];
                default:
                    return defaultValue(method.getReturnType());
            }
        };

        return (ISpeciesRoot) Proxy.newProxyInstance(
            ISpeciesRoot.class.getClassLoader(),
            new Class<?>[] {ISpeciesRoot.class},
            handler);
    }

    private static IChromosomeType chromosomeType() {
        InvocationHandler handler = (proxy, method, args) -> {
            switch (method.getName()) {
                case "ordinal":
                    return 0;
                case "getAlleleClass":
                    return IAllele.class;
                case "getName":
                    return "akashic_test";
                case "toString":
                    return "AkashicTestChromosomeType";
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "equals":
                    return proxy == args[0];
                default:
                    return defaultValue(method.getReturnType());
            }
        };

        return (IChromosomeType) Proxy.newProxyInstance(
            IChromosomeType.class.getClassLoader(),
            new Class<?>[] {IChromosomeType.class},
            handler);
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0.0F;
        }
        if (type == double.class) {
            return 0.0D;
        }
        if (type == char.class) {
            return (char) 0;
        }
        return null;
    }
}