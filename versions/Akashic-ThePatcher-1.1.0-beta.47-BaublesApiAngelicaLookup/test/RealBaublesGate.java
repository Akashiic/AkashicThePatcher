import java.io.InputStream;
import java.util.Arrays;
import java.util.jar.JarFile;

import net.minecraft.launchwrapper.IClassTransformer;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * The transformer of the jar under test on the real IBaubleExpanded of a Baubles Expanded jar.
 *
 *   java RealBaublesGate <BaublesExpanded jar> patched|untouched
 *
 * patched:   the methods are the original ones plus public non-abstract onSlotContentsChanged(ItemStack,
 *            EntityLivingBase)V whose body is a single RETURN (the 2.2.26 default); version >= 52; a second pass
 *            changes nothing.
 * untouched: the bytes come back unchanged (2.2.26 copy, older jar, kill-switch).
 */
public final class RealBaublesGate {
    static final String TARGET = "baubles.api.expanded.IBaubleExpanded";

    public static void main(String[] args) throws Exception {
        final boolean patched = args[1].equals("patched");
        final byte[] original;
        try (JarFile jar = new JarFile(args[0]);
             InputStream in = jar.getInputStream(jar.getJarEntry(TARGET.replace('.', '/') + ".class"))) {
            original = TransformingLoader.read(in);
        }
        IClassTransformer transformer = null;
        try {
            transformer = (IClassTransformer) Class.forName(
                "com.akashic.thepatcher.module.baubles.BaublesExpandedApiTransformer").newInstance();
        } catch (ClassNotFoundException absent) {
            System.out.println("A transformer not in the jar under test");
        }
        final byte[] result = transformer == null ? original : transformer.transform(TARGET, TARGET, original);
        final boolean changed = !Arrays.equals(original, result);
        System.out.println("A changed=" + changed);
        if (!patched) {
            check(!changed, "the class was edited");
            System.out.println("REAL_BAUBLES PASS expect=untouched");
            return;
        }
        check(changed, "the class was not edited");
        final ClassNode before = node(original), after = node(result);
        check(after.methods.size() == before.methods.size() + 1, "expected exactly one method added");
        for (int i = 0; i < before.methods.size(); i++) {
            final MethodNode a = (MethodNode) before.methods.get(i), b = (MethodNode) after.methods.get(i);
            check(a.name.equals(b.name) && a.desc.equals(b.desc) && a.access == b.access, "an existing method changed");
        }
        final MethodNode added = (MethodNode) after.methods.get(after.methods.size() - 1);
        check(added.name.equals("onSlotContentsChanged")
            && added.desc.equals("(Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/EntityLivingBase;)V")
            && added.access == Opcodes.ACC_PUBLIC, "unexpected method " + added.name + added.desc + " " + added.access);
        check(added.instructions.size() == 1 && added.instructions.getFirst().getOpcode() == Opcodes.RETURN,
            "the default method is not empty");
        check((after.version & 0xFFFF) >= Opcodes.V1_8, "class version " + after.version);
        System.out.println("A added: public default onSlotContentsChanged" + added.desc + " { return; } version="
            + (after.version & 0xFFFF));
        check(Arrays.equals(result, transformer.transform(TARGET, TARGET, result)), "second pass changed the class");
        System.out.println("A second pass: unchanged");
        System.out.println("REAL_BAUBLES PASS expect=patched");
    }

    static ClassNode node(byte[] bytes) {
        final ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        return node;
    }

    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
