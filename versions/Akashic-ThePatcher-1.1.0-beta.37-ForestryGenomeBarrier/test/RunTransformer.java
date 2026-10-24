import java.nio.file.*;
import net.minecraft.launchwrapper.IClassTransformer;

/**
 * Applies the bundled ForestryGenomeC2Transformer to Genome.class bytes, exactly as LaunchClassLoader would.
 * Run with the real launchwrapper-1.12 and asm-all-5.0.3 on the classpath. args: in.class out.class
 */
public final class RunTransformer {
    public static void main(String[] args) throws Exception {
        byte[] in = Files.readAllBytes(Paths.get(args[0]));
        IClassTransformer transformer = (IClassTransformer)
            Class.forName("com.akashic.thepatcher.module.forestry.ForestryGenomeC2Transformer").newInstance();
        byte[] out = transformer.transform("forestry.core.genetics.Genome", "forestry.core.genetics.Genome", in);
        Path target = Paths.get(args[1]);
        Files.createDirectories(target.getParent());
        Files.write(target, out);
        System.out.println("TRANSFORM identity=" + (out == in) + " bytes=" + in.length + "->" + out.length
            + " applied=" + System.getProperty("akashic.forestryGenomeBarrier.applied")
            + " codeLength=" + System.getProperty("akashic.forestryGenomeBarrier.codeLength")
            + " failed=" + System.getProperty("akashic.forestryGenomeBarrier.failed"));
    }
}
