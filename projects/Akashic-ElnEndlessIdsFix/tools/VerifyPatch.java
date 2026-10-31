import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicVerifier;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.util.Iterator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Applies the fix's transformer to the real Electrical Age classes and checks the result.
 *
 * <pre>
 *   javac -cp asm-all-5.0.3.jar -d out tools/VerifyPatch.java
 *   java -cp out:asm-all-5.0.3.jar VerifyPatch &lt;fix.jar&gt; &lt;Eln-x.y.z.jar&gt; &lt;libs dir&gt; &lt;out dir&gt;
 * </pre>
 *
 * 1. the transformer (run in its own class loader with launchwrapper/log4j) changes both classes and logs "Patched";
 * 2. the patched scanner no longer calls getBlockLSBArray/getBlockMSBArray, and calls scannerBlockKey once;
 *    checkIsOre calls oreMapBlockId and oreMapIndex once each;
 * 3. ASM's BasicVerifier accepts every method of both patched classes;
 * 4. HotSpot's own bytecode verifier accepts the patched classes (and the originals, as a control): each is
 *    loaded with every class missing from the Electrical Age jar replaced by an empty class, and initialised;
 *    a VerifyError fails the check, any later error (missing Minecraft code in &lt;clinit&gt;) means it passed;
 * 5. transforming a second time is a no-op (already patched), and unrelated classes are returned unchanged.
 */
public class VerifyPatch {
    static final String SCANNER = "mods.eln.item.electricalitem.PortableOreScannerItem$RenderStorage";
    static final String MINER = "mods.eln.transparentnode.autominer.AutoMinerSlowProcess";
    static int failures = 0;

    public static void main(String[] a) throws Exception {
        File fix = new File(a[0]), eln = new File(a[1]), libs = new File(a[2]), out = new File(a[3]);
        out.mkdirs();
        URLClassLoader tl = new URLClassLoader(new URL[] {fix.toURI().toURL(),
                new File(libs, "launchwrapper-1.12.jar").toURI().toURL(),
                new File(libs, "asm-all-5.0.3.jar").toURI().toURL(),
                new File(libs, "log4j-api.jar").toURI().toURL(),
                new File(libs, "log4j-core.jar").toURI().toURL()}, null);
        Object transformer = tl.loadClass("com.akashic.elnendlessids.ElnEndlessIdsTransformer").newInstance();
        Method transform = transformer.getClass().getMethod("transform", String.class, String.class, byte[].class);

        ZipFile zip = new ZipFile(eln);
        byte[] scanner = read(zip, SCANNER), miner = read(zip, MINER);
        byte[] pScanner = (byte[]) transform.invoke(transformer, SCANNER, SCANNER, scanner);
        byte[] pMiner = (byte[]) transform.invoke(transformer, MINER, MINER, miner);
        check(pScanner != scanner && pScanner.length != 0, "scanner class changed");
        check(pMiner != miner, "auto miner class changed");
        Files.write(new File(out, "PortableOreScannerItem$RenderStorage.class").toPath(), pScanner);
        Files.write(new File(out, "AutoMinerSlowProcess.class").toPath(), pMiner);

        check(count(pScanner, "func_76658_g") == 0 && count(pScanner, "getBlockLSBArray") == 0,
                "scanner: no getBlockLSBArray left");
        check(count(pScanner, "func_76660_i") == 0 && count(pScanner, "getBlockMSBArray") == 0,
                "scanner: no getBlockMSBArray left");
        check(count(scanner, "func_76658_g") == 1, "control: original scanner calls getBlockLSBArray once");
        check(count(pScanner, "scannerBlockKey") == 1, "scanner: calls scannerBlockKey once");
        check(count(pMiner, "oreMapBlockId") == 1 && count(pMiner, "oreMapIndex") == 1,
                "auto miner: calls oreMapBlockId and oreMapIndex once");
        check(count(pMiner, "func_149682_b") == count(miner, "func_149682_b") - 1,
                "auto miner: exactly one getIdFromBlock replaced");

        analyze(pScanner, "scanner");
        analyze(pMiner, "auto miner");

        check(jvmVerify(zip, fix, SCANNER, scanner), "HotSpot verifier: original scanner (control)");
        check(jvmVerify(zip, fix, SCANNER, pScanner), "HotSpot verifier: patched scanner");
        check(jvmVerify(zip, fix, MINER, miner), "HotSpot verifier: original auto miner (control)");
        check(jvmVerify(zip, fix, MINER, pMiner), "HotSpot verifier: patched auto miner");

        byte[] again = (byte[]) transform.invoke(transformer, SCANNER, SCANNER, pScanner);
        check(again == pScanner, "scanner: second transform is a no-op");
        byte[] again2 = (byte[]) transform.invoke(transformer, MINER, MINER, pMiner);
        check(again2 == pMiner, "auto miner: second transform is a no-op");
        byte[] other = read(zip, "mods.eln.Eln");
        check(transform.invoke(transformer, "mods.eln.Eln", "mods.eln.Eln", other) == other, "unrelated class untouched");

        System.out.println(failures == 0 ? "VERIFY PASS " + eln.getName() : "VERIFY FAIL " + failures + " " + eln.getName());
        System.exit(failures == 0 ? 0 : 1);
    }

    static void check(boolean ok, String what) {
        System.out.println((ok ? "  ok   " : "  FAIL ") + what);
        if (!ok) {
            failures++;
        }
    }

    static byte[] read(ZipFile zip, String cls) throws Exception {
        ZipEntry e = zip.getEntry(cls.replace('.', '/') + ".class");
        if (e == null) {
            return null;
        }
        InputStream in = zip.getInputStream(e);
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        for (int r; (r = in.read(buf)) > 0; ) {
            bo.write(buf, 0, r);
        }
        in.close();
        return bo.toByteArray();
    }

    static int count(byte[] cls, String methodName) {
        ClassNode cn = new ClassNode();
        new ClassReader(cls).accept(cn, 0);
        int n = 0;
        for (Object o : cn.methods) {
            for (Iterator<?> it = ((MethodNode) o).instructions.iterator(); it.hasNext(); ) {
                AbstractInsnNode i = (AbstractInsnNode) it.next();
                if (i instanceof MethodInsnNode && ((MethodInsnNode) i).name.equals(methodName)) {
                    n++;
                }
            }
        }
        return n;
    }

    static void analyze(byte[] cls, String what) {
        ClassNode cn = new ClassNode();
        new ClassReader(cls).accept(cn, 0);
        int bad = 0;
        for (Object o : cn.methods) {
            MethodNode m = (MethodNode) o;
            try {
                new Analyzer(new BasicVerifier()).analyze(cn.name, m);
            } catch (Exception e) {
                System.out.println("    " + m.name + m.desc + ": " + e);
                bad++;
            }
        }
        check(bad == 0, "ASM BasicVerifier: " + what + " (" + cn.methods.size() + " methods)");
    }

    /** Loads {@code name} from {@code bytes}; other classes come from the Eln jar, the fix jar, or empty stand-ins. */
    static boolean jvmVerify(final ZipFile eln, File fix, final String name, final byte[] bytes) throws Exception {
        final ZipFile fixZip = new ZipFile(fix);
        ClassLoader l = new ClassLoader(ClassLoader.getSystemClassLoader().getParent()) {
            @Override
            protected Class<?> findClass(String n) throws ClassNotFoundException {
                try {
                    byte[] b = n.equals(name) ? bytes : read(eln, n);
                    if (b == null) {
                        b = read(fixZip, n);
                    }
                    if (b == null) {
                        b = emptyClass(n);
                    }
                    return defineClass(n, b, 0, b.length);
                } catch (ClassNotFoundException e) {
                    throw e;
                } catch (Exception e) {
                    throw new ClassNotFoundException(n, e);
                }
            }
        };
        try {
            Class.forName(name, true, l);
            return true;
        } catch (VerifyError e) {
            System.out.println("    " + e);
            return false;
        } catch (Throwable t) {
            for (Throwable c = t; c != null; c = c.getCause()) {
                if (c instanceof VerifyError && String.valueOf(c.getMessage()).contains(name.replace('.', '/'))) {
                    System.out.println("    " + c);
                    return false;
                }
            }
            return true; // verified; failed later in <clinit> for lack of real Minecraft classes
        }
    }

    static byte[] emptyClass(String n) {
        org.objectweb.asm.ClassWriter cw = new org.objectweb.asm.ClassWriter(0);
        cw.visit(50, 1, n.replace('.', '/'), null, "java/lang/Object", null);
        cw.visitEnd();
        return cw.toByteArray();
    }
}
