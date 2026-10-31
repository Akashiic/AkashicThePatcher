import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceMethodVisitor;

/**
 * Compares two versions of a class method by method (instructions, labels
 * renumbered, debug info dropped) and lists the methods that differ. Also
 * checks class header and fields are unchanged.
 *
 *   java MethodDiff <old.class> <new.class>
 */
public final class MethodDiff {
    public static void main(String[] args) throws Exception {
        final ClassNode a = node(args[0]);
        final ClassNode b = node(args[1]);
        if (!a.name.equals(b.name) || !String.valueOf(a.superName).equals(String.valueOf(b.superName))
            || !a.interfaces.equals(b.interfaces) || a.access != b.access || a.version != b.version) {
            throw new AssertionError("class header changed");
        }
        if (!fields(a).equals(fields(b))) {
            throw new AssertionError("fields changed");
        }
        final Map<String, String> ma = methods(a);
        final Map<String, String> mb = methods(b);
        final TreeSet<String> differ = new TreeSet<String>();
        for (String key : new TreeSet<String>(ma.keySet())) {
            if (!mb.containsKey(key)) {
                differ.add("-" + key);
            } else if (!ma.get(key).equals(mb.get(key))) {
                differ.add(key);
            }
        }
        for (String key : mb.keySet()) {
            if (!ma.containsKey(key)) {
                differ.add("+" + key);
            }
        }
        System.out.println("METHOD_DIFF " + a.name + " methods=" + ma.size() + " differing=" + differ);
    }

    static TreeSet<String> fields(ClassNode node) {
        final TreeSet<String> out = new TreeSet<String>();
        for (FieldNode f : node.fields) {
            out.add(f.access + " " + f.name + f.desc + " " + f.value);
        }
        return out;
    }

    static Map<String, String> methods(ClassNode node) {
        final Map<String, String> out = new TreeMap<String, String>();
        for (MethodNode m : node.methods) {
            final Textifier text = new Textifier();
            m.accept(new TraceMethodVisitor(text));
            final StringWriter w = new StringWriter();
            text.print(new PrintWriter(w));
            out.put(m.access + " " + m.name + m.desc, w.toString().replaceAll("(?m)^\\s*(LINENUMBER|MAXSTACK|MAXLOCALS|LOCALVARIABLE).*$", ""));
        }
        return out;
    }

    static ClassNode node(String path) throws Exception {
        final ClassNode node = new ClassNode();
        new ClassReader(Files.readAllBytes(Paths.get(path))).accept(node, ClassReader.SKIP_DEBUG);
        return node;
    }
}
