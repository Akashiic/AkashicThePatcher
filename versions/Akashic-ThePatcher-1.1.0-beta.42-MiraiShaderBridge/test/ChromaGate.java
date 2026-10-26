import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

/**
 * The ChromaPlayerApiPreload fix folded into ThePatcher, on a real LaunchClassLoader transformer list with the
 * ChromatiCraft shape (pending ENTITYPUSHOUT that throws on EntityPlayerSP bytes without PlayerAPI's body).
 *
 *   java ChromaGate fixed|broken|standalone
 *
 * Calls ChromaPlayerApiPreloadFix.arm() when the jar has it, then plays Mixin's pre-read (EntityPlayerSP without
 * localPushOutOfBlocks) and the real load (with it) through the whole list.
 *   fixed       list = [before, Hide, ChromatiCraft, Restore, after]; the pre-read passes, the patch stays pending,
 *               the real load applies it once; World's patch is untouched by the pre-read.
 *   broken      (beta.41, kill-switch, server) list unchanged; the pre-read throws like ChromatiCraft does.
 *   standalone  the standalone jar's Hide is in the list: ThePatcher's copy stays off (list unchanged).
 */
public final class ChromaGate {
    static final String CHROMA = "Reika.ChromatiCraft.Auxiliary.ChromaASMHandler$ASMExecutor";
    static final String TARGET = "net.minecraft.client.entity.EntityPlayerSP";

    static final class Marker implements IClassTransformer {
        final String label;
        Marker(String label) { this.label = label; }
        @Override public byte[] transform(String name, String transformedName, byte[] bytes) { return bytes; }
        @Override public String toString() { return label; }
    }

    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        final String expect = args[0];
        final LaunchClassLoader loader = new LaunchClassLoader(new URL[0]);
        Launch.classLoader = loader;
        final Field field = LaunchClassLoader.class.getDeclaredField("transformers");
        field.setAccessible(true);
        final List<IClassTransformer> list = (List<IClassTransformer>) field.get(loader);
        list.clear();
        list.add(new Marker("before"));
        if ("standalone".equals(expect)) {
            list.add((IClassTransformer) Class.forName("com.akashic.chromaplayerapifix.ChromaPlayerApiPreloadFixPlugin$Hide").newInstance());
        }
        final Class<?> chromaClass = Class.forName(CHROMA);
        list.add((IClassTransformer) chromaClass.newInstance());
        list.add(new Marker("after"));
        final List<String> before = names(list);

        try {
            Class.forName("com.akashic.thepatcher.module.chroma.ChromaPlayerApiPreloadFix").getMethod("arm").invoke(null);
        } catch (ClassNotFoundException absent) {
            System.out.println("A fix not in the jar under test");
        }
        final List<String> after = names(list);
        System.out.println("A list before=" + before + " after=" + after);

        final Method pending = chromaClass.getMethod("pending", String.class);
        final Field applied = chromaClass.getField("applied");
        Throwable preRead = null;
        try {
            run(list, TARGET, entityPlayerSp(false));
        } catch (Throwable t) {
            preRead = t;
        }
        System.out.println("A pre-read (no PlayerAPI body): " + (preRead == null ? "passed" : "threw " + preRead)
            + "; ENTITYPUSHOUT pending=" + pending.invoke(null, TARGET));
        if ("fixed".equals(expect)) {
            check(after.size() == before.size() + 2 && after.get(1).endsWith("$Hide") && after.get(2).equals(CHROMA)
                && after.get(3).endsWith("$Restore"), "unexpected list " + after);
            check(preRead == null, "pre-read threw");
            check((Boolean) pending.invoke(null, TARGET), "patch lost after the pre-read");
            check((Boolean) pending.invoke(null, "net.minecraft.world.World"), "World patch touched");
            run(list, TARGET, entityPlayerSp(true));
            System.out.println("A real load (with PlayerAPI body): applied=" + applied.getInt(null) + " pending=" + pending.invoke(null, TARGET));
            check(applied.getInt(null) == 1 && !(Boolean) pending.invoke(null, TARGET), "ENTITYPUSHOUT not applied on the real load");
        } else {
            check(after.equals(before), "list changed: " + after);
            if ("broken".equals(expect)) check(preRead != null, "pre-read did not throw");
        }
        System.out.println("CHROMA_GATE PASS expect=" + expect);
    }

    static List<String> names(List<IClassTransformer> list) {
        final List<String> out = new ArrayList<String>();
        for (IClassTransformer t : list) out.add(t instanceof Marker ? t.toString() : t.getClass().getName());
        return out;
    }

    static void run(List<IClassTransformer> list, String name, byte[] bytes) {
        for (IClassTransformer t : new ArrayList<IClassTransformer>(list)) bytes = t.transform(name, name, bytes);
    }

    static byte[] entityPlayerSp(boolean withPlayerApiBody) {
        final ClassWriter w = new ClassWriter(0);
        w.visit(Opcodes.V1_6, Opcodes.ACC_PUBLIC, "net/minecraft/client/entity/EntityPlayerSP", null, "java/lang/Object", null);
        w.visitMethod(Opcodes.ACC_PUBLIC, withPlayerApiBody ? "localPushOutOfBlocks" : "func_145771_j", "(DDD)Z", null, null);
        w.visitEnd();
        return w.toByteArray();
    }

    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
