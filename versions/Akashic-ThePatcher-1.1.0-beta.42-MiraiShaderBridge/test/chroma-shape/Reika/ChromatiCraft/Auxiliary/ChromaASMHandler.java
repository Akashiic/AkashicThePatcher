package Reika.ChromatiCraft.Auxiliary;

import java.util.HashMap;

import net.minecraft.launchwrapper.IClassTransformer;

/**
 * Shape of ChromatiCraft V33a's transformer: ASMExecutor keeps its pending patches in the static map "classes"
 * and its ENTITYPUSHOUT patch throws on EntityPlayerSP bytes without PlayerAPI's localPushOutOfBlocks (what
 * Mixin's pre-read sees), as DragonAPI's NoSuchASMMethodException does. Never shipped.
 */
public class ChromaASMHandler {
    public static class ASMExecutor implements IClassTransformer {
        private static final HashMap<String, Object> classes = new HashMap<String, Object>();
        public static int applied;

        static {
            classes.put("net.minecraft.client.entity.EntityPlayerSP", "ENTITYPUSHOUT");
            classes.put("net.minecraft.world.World", "COLLISIONBOXES");
        }

        public static boolean pending(String name) {
            return classes.containsKey(name);
        }

        @Override
        public byte[] transform(String name, String transformedName, byte[] bytes) {
            final Object patch = classes.get(name);
            if (patch == null || bytes == null) {
                return bytes;
            }
            if ("ENTITYPUSHOUT".equals(patch) && !new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1).contains("localPushOutOfBlocks")) {
                throw new IllegalStateException("NoSuchASMMethodException: Method localPushOutOfBlocks (DDD)Z not found in class " + name);
            }
            classes.remove(name);
            applied++;
            return bytes;
        }
    }
}
