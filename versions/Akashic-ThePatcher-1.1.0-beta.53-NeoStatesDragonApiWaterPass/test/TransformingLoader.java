import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import net.minecraft.launchwrapper.IClassTransformer;

/**
 * Child-first loader for the third-party shape classes of the gates: reads them from a directory (compiled shapes)
 * or a jar on the parent class path, runs them through the bundled transformer when the jar under test has it (as
 * LaunchClassLoader would), and defines them. Every other class comes from the parent.
 */
final class TransformingLoader extends ClassLoader {
    private final Path shapes;
    private final List<String> owned;
    private final IClassTransformer transformer;
    int transformed;

    TransformingLoader(ClassLoader parent, Path shapes, List<String> owned, String transformerName) throws Exception {
        super(parent);
        this.shapes = shapes;
        this.owned = owned;
        IClassTransformer found = null;
        try {
            found = (IClassTransformer) Class.forName(transformerName, true, parent).newInstance();
        } catch (ClassNotFoundException absent) {
            System.out.println("transformer " + transformerName + " not in the jar under test");
        }
        this.transformer = found;
    }

    @Override
    protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        if (!owned.contains(name)) {
            return super.loadClass(name, resolve);
        }
        Class<?> c = findLoadedClass(name);
        if (c == null) {
            try {
                byte[] original = Files.readAllBytes(shapes.resolve(name.replace('.', '/') + ".class"));
                byte[] bytes = transformer == null ? original : transformer.transform(name, name, original);
                if (bytes != original && !java.util.Arrays.equals(bytes, original)) transformed++;
                c = defineClass(name, bytes, 0, bytes.length);
            } catch (IOException e) {
                throw new ClassNotFoundException(name, e);
            }
        }
        if (resolve) resolveClass(c);
        return c;
    }

    static byte[] read(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        for (int n; (n = in.read(buffer)) != -1;) out.write(buffer, 0, n);
        return out.toByteArray();
    }
}
