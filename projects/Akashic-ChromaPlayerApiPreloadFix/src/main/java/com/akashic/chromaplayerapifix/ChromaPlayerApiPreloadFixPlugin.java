package com.akashic.chromaplayerapifix;

import cpw.mods.fml.relauncher.IFMLLoadingPlugin;
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

/**
 * Stops ChromatiCraft's ENTITYPUSHOUT patch from throwing while Mixin pre-reads EntityPlayerSP before PlayerAPI
 * has rewritten it. The throw made Mixin drop every EntityPlayerSP mixin (Et Futurum's elytra take-off,
 * CoreTweaks' extendsprint).
 *
 * ChromatiCraft's transformer stays registered as it is (DragonAPI refuses to start without it); a {@link Hide}
 * transformer right before it takes the EntityPlayerSP entry out of its pending-patch map for that one read and a
 * {@link Restore} transformer right after it puts the entry back, so the patch is applied on the real class load,
 * after PlayerAPI - exactly what happens today after the failed pre-read.
 *
 * Sorted after ChromatiCraft (1001), so its transformer is already registered when injectData runs, and before
 * Mixin's DEFAULT phase, which only starts after every coremod has been injected.
 */
@IFMLLoadingPlugin.Name("AkashicChromaPlayerApiPreloadFix")
@IFMLLoadingPlugin.MCVersion("1.7.10")
@IFMLLoadingPlugin.SortingIndex(1002)
@IFMLLoadingPlugin.TransformerExclusions("com.akashic.chromaplayerapifix")
public final class ChromaPlayerApiPreloadFixPlugin implements IFMLLoadingPlugin {
    static final String CHROMA_TRANSFORMER = "Reika.ChromatiCraft.Auxiliary.ChromaASMHandler$ASMExecutor";
    static final String TARGET = "net.minecraft.client.entity.EntityPlayerSP";
    static final String PLAYER_API_METHOD = "localPushOutOfBlocks";
    static final String PLAYER_API_TRANSFORMER = "api/player/forge/PlayerAPITransformer.class";

    /** ChromatiCraft's pending patches by class name (ASMExecutor.classes); set once armed. */
    static Map<Object, Object> chromaPatches;
    /** The EntityPlayerSP patch while it is hidden from ChromatiCraft, else null. */
    static Object hiddenKey;
    static Object hiddenPatch;
    static boolean reported;

    @Override
    public String[] getASMTransformerClass() {
        return new String[0];
    }

    @Override
    public String getModContainerClass() {
        return null;
    }

    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void injectData(Map<String, Object> data) {
        if (Boolean.getBoolean("akashic.chromaPlayerApiPreload.disable")) {
            Log.info("disabled by -Dakashic.chromaPlayerApiPreload.disable=true");
            return;
        }
        try {
            Field field = LaunchClassLoader.class.getDeclaredField("transformers");
            field.setAccessible(true);
            List<IClassTransformer> transformers = (List<IClassTransformer>) field.get(Launch.classLoader);
            for (int i = 0; i < transformers.size(); i++) {
                IClassTransformer transformer = transformers.get(i);
                if (CHROMA_TRANSFORMER.equals(transformer.getClass().getName())) {
                    Field classes = transformer.getClass().getDeclaredField("classes");
                    classes.setAccessible(true);
                    chromaPatches = (Map<Object, Object>) classes.get(null);
                    transformers.add(i + 1, new Restore());
                    transformers.add(i, new Hide());
                    Log.info("armed around " + CHROMA_TRANSFORMER + " (index " + i + ")");
                    return;
                }
            }
            Log.info("ChromatiCraft transformer not registered; nothing to do");
        } catch (Throwable t) {
            Log.warn("could not arm; ChromatiCraft is left untouched: " + t);
        }
    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }

    static boolean playerApiInstalled() {
        // Same test ChromatiCraft makes (ReikaASMHelper.checkForClass), without loading the class.
        return ChromaPlayerApiPreloadFixPlugin.class.getClassLoader().getResource(PLAYER_API_TRANSFORMER) != null;
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
                Object patch = chromaPatches.remove(name);
                if (patch != null) {
                    hiddenKey = name;
                    hiddenPatch = patch;
                    if (!reported) {
                        reported = true;
                        Log.info("held back ChromatiCraft ENTITYPUSHOUT from EntityPlayerSP bytes without PlayerAPI's "
                                + PLAYER_API_METHOD + " (Mixin pre-read); it stays pending for the real class load");
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
