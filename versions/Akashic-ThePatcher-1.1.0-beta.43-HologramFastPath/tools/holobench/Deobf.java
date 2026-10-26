import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;

/** notch -> MCP remapper for the 1.7.10 client jar: Deobf <client.jar> <notch-srg.srg> <mcpdir> <out.jar> */
public class Deobf {
    static Map<String, String> cls = new HashMap<>(), fld = new HashMap<>(), mth = new HashMap<>(), mcp = new HashMap<>();
    static Map<String, String> sup = new HashMap<>();
    static Map<String, String[]> itf = new HashMap<>();

    public static void main(String[] a) throws Exception {
        for (String l : Files.readAllLines(Paths.get(a[1]))) {
            String[] p = l.split(" ");
            if (l.startsWith("CL:")) cls.put(p[1], p[2]);
            else if (l.startsWith("FD:")) fld.put(p[1], p[2].substring(p[2].lastIndexOf('/') + 1));
            else if (l.startsWith("MD:")) mth.put(p[1] + p[2], p[3].substring(p[3].lastIndexOf('/') + 1));
        }
        for (String f : new String[]{"fields.csv", "methods.csv"})
            for (String l : Files.readAllLines(Paths.get(a[2], f))) { String[] p = l.split(","); if (p.length > 1) mcp.put(p[0], p[1]); }
        ZipFile in = new ZipFile(a[0]);
        for (ZipEntry e : Collections.list(in.entries())) if (e.getName().endsWith(".class")) {
            ClassReader r = new ClassReader(in.getInputStream(e));
            sup.put(r.getClassName(), r.getSuperName()); itf.put(r.getClassName(), r.getInterfaces());
        }
        Remapper rm = new Remapper() {
            public String map(String n) { return cls.getOrDefault(n, n); }
            public String mapFieldName(String o, String n, String d) { String s = find(o, n, null, true); return s == null ? n : mcp.getOrDefault(s, s); }
            public String mapMethodName(String o, String n, String d) { String s = find(o, n, d, false); return s == null ? n : mcp.getOrDefault(s, s); }
            String find(String o, String n, String d, boolean f) {
                if (o == null) return null;
                String s = f ? fld.get(o + "/" + n) : mth.get(o + "/" + n + d);
                if (s != null) return s;
                s = find(sup.get(o), n, d, f); if (s != null) return s;
                String[] is = itf.get(o); if (is != null) for (String i : is) { s = find(i, n, d, f); if (s != null) return s; }
                return null;
            }
        };
        ZipOutputStream out = new ZipOutputStream(new FileOutputStream(a[3]));
        for (ZipEntry e : Collections.list(in.entries())) {
            if (!e.getName().endsWith(".class")) continue;
            ClassReader r = new ClassReader(in.getInputStream(e));
            ClassWriter w = new ClassWriter(0);
            r.accept(new ClassRemapper(w, rm), 0);
            out.putNextEntry(new ZipEntry(rm.map(r.getClassName()) + ".class"));
            out.write(w.toByteArray());
        }
        out.close();
    }
}
