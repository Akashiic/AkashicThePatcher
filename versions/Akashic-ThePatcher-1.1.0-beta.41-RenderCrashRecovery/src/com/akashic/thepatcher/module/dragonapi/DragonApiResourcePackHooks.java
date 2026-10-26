package com.akashic.thepatcher.module.dragonapi;

import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.client.resources.AbstractResourcePack;

import com.akashic.thepatcher.util.BundleLog;

/**
 * Runtime side of DragonApiResourcePackTransformer (beta.41): called from the
 * edited DragonAPI methods. DragonAPI reads pack files by raw path through
 * AbstractResourcePack.getInputStreamByName, which a plain IResourcePack does
 * not have, so such a pack is treated as not containing DragonAPI's files.
 * Only class names are used here (no Minecraft members), so the class works
 * unchanged under the obfuscated runtime names.
 */
public final class DragonApiResourcePackHooks {

    private static final Set<String> LOGGED = ConcurrentHashMap.newKeySet();

    private DragonApiResourcePackHooks() {}

    /** True for a non-null pack DragonAPI cannot read (not an AbstractResourcePack). */
    public static boolean isUnsupportedPack(Object pack) {
        if (pack == null || pack instanceof AbstractResourcePack) {
            return false;
        }
        final String type = pack.getClass().getName();
        if (LOGGED.add(type)) {
            BundleLog.warn("DragonAPI resource pack guard: resource pack " + type
                + " is not an AbstractResourcePack; DragonAPI skips it and uses the next pack"
                + " or the mod's own texture (this used to be a ClassCastException).");
        }
        return true;
    }

    /** A copy of the selected packs without the ones DragonAPI cannot read. */
    public static <T> ArrayList<T> withoutUnsupportedPacks(ArrayList<T> packs) {
        if (packs == null) {
            return null;
        }
        final ArrayList<T> readable = new ArrayList<>(packs.size());
        for (T pack : packs) {
            if (!isUnsupportedPack(pack)) {
                readable.add(pack);
            }
        }
        return readable;
    }
}
