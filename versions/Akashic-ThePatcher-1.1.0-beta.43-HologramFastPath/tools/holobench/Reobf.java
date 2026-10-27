import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;

/** MCP -> SRG member remapper for classes compiled against mc-mcp.jar:
 *  Reobf <notch-srg.srg> <mcpdir> <mc-mcp.jar> <in-classes-dir> <out-dir> */
public class Reobf {
    static Map<String, String> fld = new HashMap<>(), mth = new HashMap<>(), mcp = new HashMap<>();
    static Map<String, String> sup = new HashMap<>();
    static Map<String, String[]> itf = new HashMap<>();

    public static void main(String[] a) throws Exception {
        for (String f : new String[]{"fields.csv", "methods.csv"})
            for (String l : Files.readAllLines(Paths.get(a[1], f))) { String[] p = l.split(","); if (p.length > 1) mcp.put(p[0], p[1]); }
        for (String l : Files.readAllLines(Paths.get(a[0]))) {
            String[] p = l.split(" ");
            if (l.startsWith("FD:")) { String s = p[2]; int i = s.lastIndexOf('/'); String o = s.substring(0, i), n = s.substring(i + 1); fld.put(o + "/" + mcp.getOrDefault(n, n), n); }
            else if (l.startsWith("MD:")) { String s = p[3]; int i = s.lastIndexOf('/'); String o = s.substring(0, i), n = s.substring(i + 1); mth.put(o + "/" + mcp.getOrDefault(n, n) + p[4], n); }
        }
        try (ZipFile in = new ZipFile(a[2])) {
            for (ZipEntry e : Collections.list(in.entries())) if (e.getName().endsWith(".class")) {
                ClassReader r = new ClassReader(in.getInputStream(e));
                sup.put(r.getClassName(), r.getSuperName()); itf.put(r.getClassName(), r.getInterfaces());
            }
        }
        Path inDir = Paths.get(a[3]), outDir = Paths.get(a[4]);
        // own classes may extend MC classes
        List<Path> files = new ArrayList<>();
        Files.walk(inDir).filter(p -> p.toString().endsWith(".class")).forEach(files::add);
        for (Path p : files) { ClassReader r = new ClassReader(Files.readAllBytes(p)); sup.put(r.getClassName(), r.getSuperName()); itf.put(r.getClassName(), r.getInterfaces()); }
        Remapper rm = new Remapper() {
            public String mapFieldName(String o, String n, String d) { String s = find(o, n, null, true); return s == null ? n : s; }
            public String mapMethodName(String o, String n, String d) { String s = find(o, n, d, false); return s == null ? n : s; }
            String find(String o, String n, String d, boolean f) {
                if (o == null || o.startsWith("[")) return null;
                String s = f ? fld.get(o + "/" + n) : mth.get(o + "/" + n + d);
                if (s != null) return s;
                s = find(sup.get(o), n, d, f); if (s != null) return s;
                String[] is = itf.get(o); if (is != null) for (String i : is) { s = find(i, n, d, f); if (s != null) return s; }
                return null;
            }
        };
        for (Path p : files) {
            ClassReader r = new ClassReader(Files.readAllBytes(p));
            ClassWriter w = new ClassWriter(0);
            r.accept(new ClassRemapper(w, rm), 0);
            Path o = outDir.resolve(inDir.relativize(p));
            Files.createDirectories(o.getParent());
            Files.write(o, w.toByteArray());
        }
        System.out.println("REOBF OK " + files.size());
    }
}
