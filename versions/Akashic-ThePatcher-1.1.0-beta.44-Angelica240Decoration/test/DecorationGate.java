import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.zip.ZipFile;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicVerifier;

/**
 * Runs the bundled AngelicaDecorationPostProcessor.postProcess on the real
 * Angelica BatchingFontRenderer and writes the result.
 *
 *   java DecorationGate <angelica.jar> <out.class>   (or <in.class> <out.class> to re-run on a result)
 */
public final class DecorationGate {
    public static void main(String[] args) throws Exception {
        final byte[] input = args[0].endsWith(".jar") ? fromJar(args[0]) : Files.readAllBytes(Paths.get(args[0]));
        final Class<?> processor = Class.forName("com.akashic.thepatcher.module.rgb.AngelicaDecorationPostProcessor");
        final Method post = processor.getMethod("postProcess", String.class, byte[].class);
        final byte[] out = (byte[]) post.invoke(null, "com.gtnewhorizons.angelica.client.font.BatchingFontRenderer", input);
        final Method fixed = processor.getDeclaredMethod("hasEndpointFix", byte[].class);
        fixed.setAccessible(true);
        Files.write(Paths.get(args[1]), out);
        final ClassNode node = new ClassNode();
        new ClassReader(out).accept(node, 0);
        for (Object o : node.methods) {
            final MethodNode m = (MethodNode) o;
            if ((m.access & Opcodes.ACC_ABSTRACT) == 0) {
                new Analyzer(new BasicVerifier()).analyze(node.name, m);
            }
        }
        System.out.println("DECORATION changed=" + (out != input) + " endpointFix=" + fixed.invoke(null, (Object) out)
            + " verified=" + node.methods.size());
    }

    static byte[] fromJar(String jar) throws Exception {
        try (ZipFile zip = new ZipFile(jar);
            InputStream in = zip.getInputStream(zip.getEntry("com/gtnewhorizons/angelica/client/font/BatchingFontRenderer.class"))) {
            final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            final byte[] buffer = new byte[8192];
            for (int n; (n = in.read(buffer)) != -1;) {
                bytes.write(buffer, 0, n);
            }
            return bytes.toByteArray();
        }
    }
}
