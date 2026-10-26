package com.akashic.thepatcher.module.chroma;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import com.akashic.thepatcher.core.RuntimeSide;
import com.akashic.thepatcher.util.BundleLog;

/**
 * Akashic-ChromaPlayerApiPreloadFix 1.0.0, folded into ThePatcher (beta.42); the logic
 * is unchanged, see projects/Akashic-ChromaPlayerApiPreloadFix/README.md.
 *
 * Stops ChromatiCraft's ENTITYPUSHOUT patch from throwing while Mixin pre-reads
 * EntityPlayerSP before PlayerAPI has rewritten it. The throw made Mixin drop every
 * EntityPlayerSP mixin (Et Futurum's elytra take-off, CoreTweaks' extendsprint).
 *
 * ChromatiCraft's transformer stays registered as it is (DragonAPI refuses to start
 * without it); a {@link Hide} transformer right before it takes the EntityPlayerSP entry
 * out of its pending-patch map for that one read and a {@link Restore} transformer right
 * after it puts the entry back, so the patch is applied on the real class load, after
 * PlayerAPI.
 *
 * {@link #arm()} runs from AkashicThePatcherLoadingPlugin.injectData and acts on the client only. ThePatcher
 * sorts at 100300, after ChromatiCraft (1001), so its transformer is already registered,
 * and coremods are all injected before Mixin's DEFAULT phase starts the pre-reads.
 * If the standalone 1.0.0 jar is also installed and armed, this copy stays off.
 * Kill-switch: -Dakashic.chromaPlayerApiPreload.disable=true (same as the standalone jar).
 */
public final class ChromaPlayerApiPreloadFix {
    public static final String DISABLE_PROPERTY = "akashic.chromaPlayerApiPreload.disable";
    static final String CHROMA_TRANSFORMER = "Reika.ChromatiCraft.Auxiliary.ChromaASMHandler$ASMExecutor";
    static final String STANDALONE_PACKAGE = "com.akashic.chromaplayerapifix.";
    static final String TARGET = "net.minecraft.client.entity.EntityPlayerSP";
    static final String PLAYER_API_METHOD = "localPushOutOfBlocks";
    static final String PLAYER_API_TRANSFORMER = "api/player/forge/PlayerAPITransformer.class";

    /** ChromatiCraft's pending patches by class name (ASMExecutor.classes); set once armed. */
    static Map<Object, Object> chromaPatches;
    /** The EntityPlayerSP patch while it is hidden from ChromatiCraft, else null. */
    static Object hiddenKey;
    static Object hiddenPatch;
    static boolean reported;

    private ChromaPlayerApiPreloadFix() {}

    @SuppressWarnings("unchecked")
    public static void arm() {
        if (!RuntimeSide.isClient()) {
            return;
        }
        if (Boolean.getBoolean(DISABLE_PROPERTY)) {
            BundleLog.info("ChromaPlayerApiPreload fix disabled by -D" + DISABLE_PROPERTY + "=true");
            return;
        }
        try {
            final Field field = LaunchClassLoader.class.getDeclaredField("transformers");
            field.setAccessible(true);
            final List<IClassTransformer> transformers = (List<IClassTransformer>) field.get(Launch.classLoader);
            for (IClassTransformer transformer : transformers) {
                if (transformer.getClass().getName().startsWith(STANDALONE_PACKAGE)) {
                    BundleLog.info("ChromaPlayerApiPreload fix: the standalone Akashic-ChromaPlayerApiPreloadFix jar is "
                        + "armed; ThePatcher's copy stays off (remove the standalone jar, ThePatcher includes it).");
                    return;
                }
            }
            for (int i = 0; i < transformers.size(); i++) {
                final IClassTransformer transformer = transformers.get(i);
                if (CHROMA_TRANSFORMER.equals(transformer.getClass().getName())) {
                    final Field classes = transformer.getClass().getDeclaredField("classes");
                    classes.setAccessible(true);
                    chromaPatches = (Map<Object, Object>) classes.get(null);
                    transformers.add(i + 1, new Restore());
                    transformers.add(i, new Hide());
                    BundleLog.info("ChromaPlayerApiPreload fix armed around " + CHROMA_TRANSFORMER + " (index " + i + ")");
                    return;
                }
            }
            BundleLog.info("ChromaPlayerApiPreload fix: ChromatiCraft transformer not registered; nothing to do");
        } catch (Throwable t) {
            BundleLog.warn("ChromaPlayerApiPreload fix could not arm; ChromatiCraft is left untouched: " + t);
        }
    }

    static boolean playerApiInstalled() {
        // Same test ChromatiCraft makes (ReikaASMHelper.checkForClass), without loading the class.
        return ChromaPlayerApiPreloadFix.class.getClassLoader().getResource(PLAYER_API_TRANSFORMER) != null;
    }

    static boolean hasPlayerApiBody(byte[] classBytes) {
        final boolean[] found = new boolean[1];
        new ClassReader(classBytes).accept(new ClassVisitor(Opcodes.ASM5) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                if (PLAYER_API_METHOD.equals(name) && "(DDD)Z".equals(desc)) {
                    found[0] = true;
                }
                return null;
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return found[0];
    }

    /*
     * Hide and Restore must stay nested classes, like ChromatiCraft's ASMExecutor. Mixingasm puts transformers it
     * does not trust on Mixin's exclusion list by canonical name ("Outer.Inner") while Mixin matches binary names
     * ("Outer$Inner"), so nested transformers keep running during Mixin's pre-reads, as ChromatiCraft's does. If
     * that ever changes, all three are excluded together.
     */

    /** Runs right before ChromatiCraft: hides the EntityPlayerSP patch from a pre-read without PlayerAPI's body. */
    static final class Hide implements IClassTransformer {
        @Override
        public byte[] transform(String name, String transformedName, byte[] basicClass) {
            if (basicClass == null || !TARGET.equals(transformedName) || !playerApiInstalled() || hasPlayerApiBody(basicClass)) {
                return basicClass;
            }
            synchronized (chromaPatches) {
                final Object patch = chromaPatches.remove(name);
                if (patch != null) {
                    hiddenKey = name;
                    hiddenPatch = patch;
                    if (!reported) {
                        reported = true;
                        BundleLog.info("ChromaPlayerApiPreload fix held back ChromatiCraft ENTITYPUSHOUT from EntityPlayerSP "
                            + "bytes without PlayerAPI's " + PLAYER_API_METHOD
                            + " (Mixin pre-read); it stays pending for the real class load");
                    }
                }
            }
            return basicClass;
        }
    }

    /** Runs right after ChromatiCraft: puts a hidden patch back so the real class load still gets it. */
    static final class Restore implements IClassTransformer {
        @Override
        public byte[] transform(String name, String transformedName, byte[] basicClass) {
            if (hiddenPatch != null) {
                synchronized (chromaPatches) {
                    if (hiddenPatch != null) {
                        chromaPatches.put(hiddenKey, hiddenPatch);
                        hiddenKey = null;
                        hiddenPatch = null;
                    }
                }
            }
            return basicClass;
        }
    }
}
