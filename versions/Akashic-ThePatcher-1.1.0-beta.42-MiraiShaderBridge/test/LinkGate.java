import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Every reference from the jar's classes under <sourcePrefix> to owners under <ownerPrefix> must resolve
 * (class, method or field, through super classes and interfaces) in <targetJar>.
 *
 *   java LinkGate <jar> <sourcePrefix> <targetJar> <ownerPrefix>
 */
public final class LinkGate {
    static final Map<String, byte[]> target = new HashMap<String, byte[]>();

    public static void main(String[] a) throws Exception {
        final String prefix = a[3];
        try (ZipFile z = new ZipFile(a[2])) {
            for (ZipEntry e : Collections.list(z.entries())) {
                if (e.getName().endsWith(".class") && !e.getName().startsWith("META-INF/")) target.put(e.getName().replace(".class", ""), read(z.getInputStream(e)));
            }
        }
        final Set<String> refs = new TreeSet<String>();
        try (ZipFile z = new ZipFile(a[0])) {
            for (ZipEntry e : Collections.list(z.entries())) {
                if (!e.getName().startsWith(a[1]) || !e.getName().endsWith(".class")) continue;
                new ClassReader(read(z.getInputStream(e))).accept(new ClassVisitor(Opcodes.ASM9) {
                    @Override public MethodVisitor visitMethod(int acc, String n, String d, String s, String[] ex) {
                        return new MethodVisitor(Opcodes.ASM9) {
                            @Override public void visitMethodInsn(int op, String o, String mn, String md, boolean itf) { if (o.startsWith(prefix)) refs.add("M " + o + "." + mn + md); }
                            @Override public void visitFieldInsn(int op, String o, String fn, String fd) { if (o.startsWith(prefix)) refs.add("F " + o + "." + fn + ":" + fd); }
                            @Override public void visitTypeInsn(int op, String t) { if (t.startsWith(prefix)) refs.add("C " + t); }
                        };
                    }
                }, 0);
            }
        }
        int missing = 0;
        for (String r : refs) {
            final String body = r.substring(2);
            final boolean ok;
            if (r.charAt(0) == 'C') ok = target.containsKey(body);
            else if (r.charAt(0) == 'M') ok = has(body.substring(0, body.indexOf('.')), body.substring(body.indexOf('.') + 1, body.indexOf('(')), body.substring(body.indexOf('(')), true);
            else ok = has(body.substring(0, body.indexOf('.')), body.substring(body.indexOf('.') + 1, body.indexOf(':')), body.substring(body.indexOf(':') + 1), false);
            System.out.println("A " + (ok ? "ok      " : "MISSING ") + r);
            if (!ok) missing++;
        }
        if (missing != 0 || refs.isEmpty()) throw new AssertionError(missing + " unresolved of " + refs.size());
        System.out.println("LINK_GATE PASS refs=" + refs.size());
    }

    static boolean has(String owner, final String name, final String desc, final boolean method) {
        final java.util.Deque<String> queue = new java.util.ArrayDeque<String>();
        queue.add(owner);
        final Set<String> seen = new java.util.HashSet<String>();
        while (!queue.isEmpty()) {
            final String c = queue.poll();
            if (!seen.add(c)) continue;
            final byte[] b = target.get(c);
            if (b == null) continue;
            final boolean[] found = {false};
            new ClassReader(b).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public void visit(int v, int acc, String n, String s, String sup, String[] itf) {
                    if (sup != null) queue.add(sup);
                    if (itf != null) Collections.addAll(queue, itf);
                }
                @Override public FieldVisitor visitField(int acc, String n, String d, String s, Object val) { if (!method && n.equals(name) && d.equals(desc)) found[0] = true; return null; }
                @Override public MethodVisitor visitMethod(int acc, String n, String d, String s, String[] ex) { if (method && n.equals(name) && d.equals(desc)) found[0] = true; return null; }
            }, ClassReader.SKIP_CODE);
            if (found[0]) return true;
        }
        return false;
    }

    static byte[] read(InputStream in) throws java.io.IOException {
        final java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        final byte[] buffer = new byte[8192];
        for (int n; (n = in.read(buffer)) != -1;) out.write(buffer, 0, n);
        in.close();
        return out.toByteArray();
    }
}
