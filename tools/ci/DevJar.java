import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

/**
 * Builds the "-dev" JAR: the release JAR with Minecraft member names mapped from SRG (func_*, field_*)
 * to MCP names, for use in a deobfuscated (RetroFuturaGradle / ForgeGradle) development workspace.
 * Class names are already MCP names in 1.7.10 and are kept. Non-class entries are copied unchanged.
 *
 *   java -cp asm-9.7.jar:asm-commons-9.7.jar:. DevJar <release.jar> <mcp_stable-12-1.7.10.zip> <out-dev.jar>
 *
 * Annotation strings (e.g. Mixin targets) and string literals are not rewritten; the shipped refmap
 * still maps them. The output is deterministic (sorted entries, fixed time stamp).
 */
public final class DevJar {
    public static void main(String[] args) throws Exception {
        final Map<String, String> names = new HashMap<String, String>();
        try (ZipFile mcp = new ZipFile(args[1])) {
            load(mcp, "methods.csv", names);
            load(mcp, "fields.csv", names);
        }
        final Remapper remapper = new Remapper() {
            @Override public String mapMethodName(String owner, String name, String descriptor) { return srgToMcp(name); }
            @Override public String mapFieldName(String owner, String name, String descriptor) { return srgToMcp(name); }
            @Override public String mapInvokeDynamicMethodName(String name, String descriptor) { return srgToMcp(name); }
            private String srgToMcp(String name) {
                if (!name.startsWith("func_") && !name.startsWith("field_")) return name;
                final String mapped = names.get(name);
                return mapped != null ? mapped : name;
            }
        };
        int classes = 0, renamed = 0;
        try (ZipFile in = new ZipFile(args[0]);
             ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(Paths.get(args[2])))) {
            final List<String> entries = new ArrayList<String>();
            for (ZipEntry e : Collections.list(in.entries())) if (!e.isDirectory()) entries.add(e.getName());
            Collections.sort(entries);
            for (String name : entries) {
                byte[] data = read(in.getInputStream(in.getEntry(name)));
                if (name.endsWith(".class")) {
                    final ClassReader reader = new ClassReader(data);
                    final ClassWriter writer = new ClassWriter(0);
                    reader.accept(new ClassRemapper(writer, remapper), 0);
                    final byte[] mapped = writer.toByteArray();
                    classes++;
                    if (!java.util.Arrays.equals(mapped, data)) renamed++;
                    data = mapped;
                }
                final ZipEntry entry = new ZipEntry(name);
                entry.setTime(1761404400000L); // fixed time stamp; run with -Duser.timezone=UTC for identical bytes
                out.putNextEntry(entry);
                out.write(data);
                out.closeEntry();
            }
        }
        System.out.println("DEV_JAR OK classes=" + classes + " remapped=" + renamed + " mappings=" + names.size());
    }

    private static void load(ZipFile zip, String entry, Map<String, String> names) throws IOException {
        final String text = new String(read(zip.getInputStream(zip.getEntry(entry))), StandardCharsets.UTF_8);
        boolean header = true;
        for (String line : text.split("\r?\n")) {
            if (header) { header = false; continue; }
            final String[] cols = line.split(",", 3);
            if (cols.length >= 2 && !cols[0].isEmpty()) names.put(cols[0], cols[1]);
        }
    }

    private static byte[] read(InputStream in) throws IOException {
        try (InputStream stream = in) {
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            final byte[] buffer = new byte[8192];
            for (int n; (n = stream.read(buffer)) != -1;) out.write(buffer, 0, n);
            return out.toByteArray();
        }
    }

    private DevJar() {}
}
