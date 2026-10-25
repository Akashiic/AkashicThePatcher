import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicVerifier;

/**
 * Runs the bundled NecroTempusAngelicaFontTransformer (from the release JAR) on
 * the real NecroTempus classes, with the real Angelica JAR behind
 * Launch.classLoader, and checks the result the way Mixin would apply it.
 *
 *   java NecroTempusFontGate <necrotempus.jar> <angelica.jar> <expect: patch|untouched|fixed>
 *
 * "fixed" is a NecroTempus build that already targets the int API: the
 * transformer must leave it alone and its mixin must already resolve.
 *
 * Prints "NT_GATE PASS ..." or throws.
 */
public final class NecroTempusFontGate {

    static final String MIXIN = "io/github/cruciblemc/necrotempus/modules/mixin/mixins/angelica/BatchingFontRendererMixin";
    static final String[] PROVIDERS = {
        "io/github/cruciblemc/necrotempus/modules/features/glyphs/compat/angelica/AngelicaGlyphProvider",
        "io/github/cruciblemc/necrotempus/modules/features/modernfonts/compat/angelica/AngelicaModernFontProvider" };
    static final String RENDERER = "com/gtnewhorizons/angelica/client/font/BatchingFontRenderer";
    static final String PROVIDER = "com/gtnewhorizons/angelica/client/font/FontProvider";

    public static void main(String[] args) throws Exception {
        final File necro = new File(args[0]);
        final File angelica = new File(args[1]);
        final boolean expectPatch = "patch".equals(args[2]);
        final boolean expectFixed = "fixed".equals(args[2]);
        Launch.classLoader = new LaunchClassLoader(new URL[] { angelica.toURI().toURL() });

        final IClassTransformer transformer = (IClassTransformer) Class
            .forName("com.akashic.thepatcher.module.necrotempus.NecroTempusAngelicaFontTransformer").newInstance();
        final List<String> names = new ArrayList<String>();
        names.add(MIXIN);
        names.addAll(Arrays.asList(PROVIDERS));

        final ClassNode renderer = node(read(angelica, RENDERER));
        final ClassNode provider = node(read(angelica, PROVIDER));
        int changed = 0;
        for (String internal : names) {
            final byte[] original = read(necro, internal);
            final String dotted = internal.replace('/', '.');
            final byte[] out = transformer.transform(dotted, dotted, original);
            final boolean same = Arrays.equals(original, out);
            if (!expectPatch) {
                check(same, internal + " must be left untouched");
                if (expectFixed) {
                    verifyAll(node(original));
                    if (internal.equals(MIXIN)) {
                        checkMixin(node(original), renderer);
                    }
                }
                continue;
            }
            check(!same, internal + " was not patched");
            changed++;
            final byte[] again = transformer.transform(dotted, dotted, out);
            check(Arrays.equals(out, again), internal + " transform is not idempotent");
            final ClassNode patched = node(out);
            verifyAll(patched);
            if (internal.equals(MIXIN)) {
                checkMixin(patched, renderer);
            } else {
                checkProvider(node(original), patched, provider);
            }
            System.out.println("PATCHED " + internal + " sha256=" + sha(out));
        }

        System.setProperty("akashic.necroTempusAngelicaFont.disable", "true");
        for (String internal : names) {
            final byte[] original = read(necro, internal);
            final String dotted = internal.replace('/', '.');
            check(Arrays.equals(original, transformer.transform(dotted, dotted, original)), "kill-switch ignored for " + internal);
        }
        System.clearProperty("akashic.necroTempusAngelicaFont.disable");
        final byte[] unrelated = read(angelica, PROVIDER);
        check(transformer.transform(PROVIDER.replace('/', '.'), PROVIDER.replace('/', '.'), unrelated) == unrelated,
            "unrelated class must be returned as is");
        System.out.println("NT_GATE PASS expect=" + args[2] + " patchedClasses=" + changed + " killSwitch=PASS");
    }

    /** What Mixin checks when it applies the mixin: shadows exist, injection points resolve, handler shapes match. */
    static void checkMixin(ClassNode mixin, ClassNode renderer) {
        int shadows = 0;
        int redirects = 0;
        int injects = 0;
        for (Object o : mixin.methods) {
            final MethodNode m = (MethodNode) o;
            if (annotation(m, "Lorg/spongepowered/asm/mixin/Shadow;") != null) {
                check(method(renderer, m.name, m.desc) != null, "@Shadow " + m.name + m.desc + " missing in Angelica");
                shadows++;
            }
            final AnnotationNode redirect = annotation(m, "Lorg/spongepowered/asm/mixin/injection/Redirect;");
            if (redirect != null) {
                final String targetMethod = (String) ((List<?>) value(redirect, "method")).get(0);
                final String target = (String) value((AnnotationNode) value(redirect, "at"), "target");
                final int semicolon = target.indexOf(';');
                final String owner = target.substring(1, semicolon);
                final String rest = target.substring(semicolon + 1);
                final String name = rest.substring(0, rest.indexOf('('));
                final String desc = rest.substring(rest.indexOf('('));
                int hits = 0;
                for (Object ro : renderer.methods) {
                    final MethodNode rm = (MethodNode) ro;
                    if (!matchesSelector(rm, targetMethod)) {
                        continue;
                    }
                    for (AbstractInsnNode insn = rm.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                        if (insn instanceof MethodInsnNode) {
                            final MethodInsnNode call = (MethodInsnNode) insn;
                            if (call.owner.equals(owner) && call.name.equals(name) && call.desc.equals(desc)) {
                                hits++;
                            }
                        }
                    }
                }
                check(hits > 0, "@Redirect target " + target + " not found in " + targetMethod);
                final Type t = Type.getMethodType(desc);
                final boolean isStatic = isStaticCall(renderer, owner, name, desc);
                final List<Type> expected = new ArrayList<Type>();
                if (!isStatic) {
                    expected.add(Type.getObjectType(owner));
                }
                expected.addAll(Arrays.asList(t.getArgumentTypes()));
                final String expectedDesc = Type.getMethodDescriptor(t.getReturnType(), expected.toArray(new Type[0]));
                check(m.desc.equals(expectedDesc), "@Redirect handler " + m.name + m.desc + " != " + expectedDesc);
                System.out.println("  redirect " + m.name + " -> " + target + " hits=" + hits);
                redirects++;
            }
            final AnnotationNode inject = annotation(m, "Lorg/spongepowered/asm/mixin/injection/Inject;");
            if (inject != null) {
                final String targetMethod = (String) ((List<?>) value(inject, "method")).get(0);
                boolean found = false;
                for (Object ro : renderer.methods) {
                    found |= matchesSelector((MethodNode) ro, targetMethod);
                }
                check(found, "@Inject target " + targetMethod + " missing");
                injects++;
            }
        }
        int fieldShadows = 0;
        for (Object o : mixin.fields) {
            final FieldNode f = (FieldNode) o;
            if (hasAnnotation(f.visibleAnnotations, "Lorg/spongepowered/asm/mixin/Shadow;")
                || hasAnnotation(f.invisibleAnnotations, "Lorg/spongepowered/asm/mixin/Shadow;")) {
                boolean found = false;
                for (Object ro : renderer.fields) {
                    final FieldNode rf = (FieldNode) ro;
                    found |= rf.name.equals(f.name) && rf.desc.equals(f.desc);
                }
                check(found, "@Shadow field " + f.name + " " + f.desc + " missing in Angelica");
                fieldShadows++;
            }
        }
        check(shadows == 2 && fieldShadows == 1 && redirects == 3 && injects == 3,
            "unexpected mixin shape s=" + shadows + " f=" + fieldShadows + " r=" + redirects + " i=" + injects);
        System.out.println("  mixin shape OK: methodShadows=" + shadows + " fieldShadows=" + fieldShadows + " redirects="
            + redirects + " injects=" + injects);
    }

    static void checkProvider(ClassNode original, ClassNode patched, ClassNode provider) {
        for (Object o : provider.methods) {
            final MethodNode im = (MethodNode) o;
            if ((im.access & Opcodes.ACC_ABSTRACT) != 0) {
                check(method(patched, im.name, im.desc) != null, patched.name + " does not implement " + im.name + im.desc);
            }
        }
        check(patched.methods.size() == original.methods.size() + 1, "provider must gain exactly one method");
        for (Object o : original.methods) {
            final MethodNode m = (MethodNode) o;
            check(method(patched, m.name, m.desc) != null, "original method lost: " + m.name + m.desc);
        }
        System.out.println("  provider implements every FontProvider method");
    }

    static boolean matchesSelector(MethodNode m, String selector) {
        final int paren = selector.indexOf('(');
        return paren < 0 ? m.name.equals(selector) : (m.name + m.desc).equals(selector);
    }

    static boolean isStaticCall(ClassNode renderer, String owner, String name, String desc) {
        if (!owner.equals(renderer.name)) {
            return true; // FontStrategist.getFontProvider is static
        }
        final MethodNode target = method(renderer, name, desc);
        return target != null && (target.access & Opcodes.ACC_STATIC) != 0;
    }

    static void verifyAll(ClassNode node) throws Exception {
        for (Object o : node.methods) {
            final MethodNode m = (MethodNode) o;
            if ((m.access & Opcodes.ACC_ABSTRACT) == 0) {
                new Analyzer(new BasicVerifier()).analyze(node.name, m);
            }
        }
    }

    static MethodNode method(ClassNode node, String name, String desc) {
        for (Object o : node.methods) {
            final MethodNode m = (MethodNode) o;
            if (m.name.equals(name) && m.desc.equals(desc)) {
                return m;
            }
        }
        return null;
    }

    static AnnotationNode annotation(MethodNode m, String desc) {
        for (List<?> list : Arrays.<List<?>>asList(m.visibleAnnotations, m.invisibleAnnotations)) {
            if (list != null) {
                for (Object o : list) {
                    if (desc.equals(((AnnotationNode) o).desc)) {
                        return (AnnotationNode) o;
                    }
                }
            }
        }
        return null;
    }

    static boolean hasAnnotation(List<?> list, String desc) {
        if (list != null) {
            for (Object o : list) {
                if (desc.equals(((AnnotationNode) o).desc)) {
                    return true;
                }
            }
        }
        return false;
    }

    static Object value(AnnotationNode a, String key) {
        for (int i = 0; i + 1 < a.values.size(); i += 2) {
            if (key.equals(a.values.get(i))) {
                return a.values.get(i + 1);
            }
        }
        return null;
    }

    static ClassNode node(byte[] bytes) {
        final ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        return node;
    }

    static byte[] read(File jar, String internal) throws Exception {
        try (ZipFile zip = new ZipFile(jar)) {
            final ZipEntry entry = zip.getEntry(internal + ".class");
            if (entry == null) {
                throw new IllegalStateException(internal + " not in " + jar);
            }
            try (InputStream in = zip.getInputStream(entry)) {
                final ByteArrayOutputStream out = new ByteArrayOutputStream();
                final byte[] buffer = new byte[8192];
                for (int n; (n = in.read(buffer)) != -1;) {
                    out.write(buffer, 0, n);
                }
                return out.toByteArray();
            }
        }
    }

    static String sha(byte[] data) throws Exception {
        final StringBuilder hex = new StringBuilder();
        for (byte b : MessageDigest.getInstance("SHA-256").digest(data)) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }

    static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
