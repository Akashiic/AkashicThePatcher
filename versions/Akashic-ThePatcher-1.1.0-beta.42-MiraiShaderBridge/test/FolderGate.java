import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import net.minecraft.launchwrapper.IClassTransformer;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * AngelicaShaderPackFolderTransformer.
 *
 *   java FolderGate <work dir> <shape dir> fixed|broken <angelica.jar>...
 *
 * 1. Real ShaderPack classes: the root (Java 8) ShaderPack.class of each Angelica jar is transformed; fixed: exactly
 *    one invokedynamic changes, its implementation Path.toString -> AngelicaShaderPackFolderHooks.folderName;
 *    broken: unchanged.
 * 2. Behaviour: the ShaderPack shape (same stream, same method references) is transformed, defined and run on a
 *    zip shader pack (shaders/world0, world-1, world1, lib, program) through the JVM's zip file system. On Java 8,
 *    fixed finds [world-1, world0, world1]; broken finds [] (Angelica 2.2.37's behaviour); on Java 9+ both find all.
 */
public final class FolderGate {
    static final String TRANSFORMER = "com.akashic.thepatcher.module.angelica.AngelicaShaderPackFolderTransformer";

    public static void main(String[] args) throws Exception {
        final Path work = Paths.get(args[0]);
        final Path shapes = Paths.get(args[1]);
        final String expect = args[2];
        IClassTransformer transformer = null;
        try {
            transformer = (IClassTransformer) Class.forName(TRANSFORMER).newInstance();
        } catch (ClassNotFoundException absent) {
            System.out.println("A transformer not in the jar under test");
        }
        for (int i = 3; i < args.length; i++) {
            final byte[] original;
            try (ZipFile z = new ZipFile(args[i])) {
                original = read(z.getInputStream(z.getEntry("net/coderbot/iris/shaderpack/ShaderPack.class")));
            }
            final byte[] result = transformer == null ? original
                : transformer.transform("net.coderbot.iris.shaderpack.ShaderPack", "net.coderbot.iris.shaderpack.ShaderPack", original.clone());
            final List<String> changes = indyChanges(original, result);
            System.out.println("A " + Paths.get(args[i]).getFileName() + " ShaderPack changed=" + !Arrays.equals(original, result) + " " + changes);
            if ("fixed".equals(expect)) {
                check(changes.size() == 1 && changes.get(0).endsWith(
                    "java/nio/file/Path.toString()Ljava/lang/String; -> com/akashic/thepatcher/module/angelica/AngelicaShaderPackFolderHooks.folderName(Ljava/nio/file/Path;)Ljava/lang/String;"),
                    "unexpected change " + changes);
            } else {
                check(Arrays.equals(original, result), "class changed");
            }
        }

        final Path zip = work.resolve("pack.zip");
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zip.toFile()))) {
            for (String name : new String[] {"shaders/", "shaders/world0/", "shaders/world0/dh_terrain.fsh", "shaders/world-1/",
                "shaders/world-1/gbuffers_terrain.fsh", "shaders/world1/", "shaders/world1/gbuffers_terrain.fsh", "shaders/lib/",
                "shaders/lib/settings.glsl", "shaders/program/", "shaders/program/dh_terrain.glsl", "shaders/shaders.properties"}) {
                out.putNextEntry(new ZipEntry(name));
                if (!name.endsWith("/")) out.write("// test\n".getBytes("UTF-8"));
                out.closeEntry();
            }
        }
        final String shape = "net.coderbot.iris.shaderpack.ShaderPack";
        final byte[] shapeBytes = java.nio.file.Files.readAllBytes(shapes.resolve(shape.replace('.', '/') + ".class"));
        final byte[] shapeResult = transformer == null ? shapeBytes : transformer.transform(shape, shape, shapeBytes.clone());
        final ClassLoader loader = new ClassLoader(FolderGate.class.getClassLoader()) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (!name.equals(shape)) return super.loadClass(name, resolve);
                Class<?> c = findLoadedClass(name);
                if (c == null) c = defineClass(name, shapeResult, 0, shapeResult.length);
                return c;
            }
        };
        final Constructor<?> constructor = loader.loadClass(shape).getConstructor(Path.class);
        final String java = System.getProperty("java.specification.version");
        try (FileSystem fs = FileSystems.newFileSystem(zip, (ClassLoader) null)) {
            final Object pack = constructor.newInstance(fs.getPath("shaders"));
            final Object found = pack.getClass().getField("foundFolders").get(pack);
            System.out.println("A Java " + java + " zip pack folders found=" + found + " (shape changed=" + !Arrays.equals(shapeBytes, shapeResult) + ")");
            final boolean all = found.toString().equals("[world-1, world0, world1]");
            if ("fixed".equals(expect) || !"1.8".equals(java)) {
                check(all, "expected all three folders");
            } else {
                check(found.toString().equals("[]"), "expected Angelica's Java 8 result []");
            }
        }
        System.out.println("FOLDER_GATE PASS expect=" + expect + " java=" + java);
    }

    static List<String> indyChanges(byte[] a, byte[] b) {
        final List<InvokeDynamicInsnNode> x = indies(a), y = indies(b);
        check(x.size() == y.size(), "invokedynamic count changed");
        final List<String> out = new ArrayList<String>();
        for (int i = 0; i < x.size(); i++) {
            final Handle h1 = (Handle) x.get(i).bsmArgs[1], h2 = (Handle) y.get(i).bsmArgs[1];
            if (!h1.equals(h2)) {
                out.add(h1.getOwner() + "." + h1.getName() + h1.getDesc() + " -> " + h2.getOwner() + "." + h2.getName() + h2.getDesc());
            }
        }
        return out;
    }

    static List<InvokeDynamicInsnNode> indies(byte[] bytes) {
        final ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        final List<InvokeDynamicInsnNode> out = new ArrayList<InvokeDynamicInsnNode>();
        for (Object m : node.methods) {
            for (AbstractInsnNode i = ((MethodNode) m).instructions.getFirst(); i != null; i = i.getNext()) {
                if (i instanceof InvokeDynamicInsnNode && ((InvokeDynamicInsnNode) i).bsmArgs.length > 1
                    && ((InvokeDynamicInsnNode) i).bsmArgs[1] instanceof Handle) out.add((InvokeDynamicInsnNode) i);
            }
        }
        return out;
    }

    static byte[] read(InputStream in) throws java.io.IOException {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final byte[] buffer = new byte[8192];
        for (int n; (n = in.read(buffer)) != -1;) out.write(buffer, 0, n);
        return out.toByteArray();
    }

    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
