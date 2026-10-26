package Reika.DragonAPI.Libraries.IO;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.client.resources.AbstractResourcePack;
import net.minecraft.client.resources.IResourcePack;

import Reika.DragonAPI.IO.ReikaImageLoader;

/**
 * Test reconstruction of the shape of DragonAPI V33b's ReikaTextureHelper (not DragonAPI's code): bindTexture walks
 * the selected packs through ReikaImageLoader, initializeColorOverrides casts every selected pack. The selected packs
 * come from {@link #selected} instead of Minecraft's ResourcePackRepository. Never shipped.
 */
public final class ReikaTextureHelper {
    public static final List<IResourcePack> selected = new ArrayList<>();
    public static String colorOverrides;

    private ReikaTextureHelper() {}

    /** Returns the pack the texture came from, or "default". */
    public static String bindTexture(String path) {
        ArrayList<IResourcePack> li = getCurrentResourcePacks();
        Collections.reverse(li);
        for (IResourcePack res : li) {
            BufferedImage img = ReikaImageLoader.getImageFromResourcePack(path, res, null);
            if (img != null) {
                return String.valueOf(res);
            }
        }
        return "default";
    }

    public static ArrayList<IResourcePack> getCurrentResourcePacks() {
        ArrayList<IResourcePack> packs = new ArrayList<>();
        for (int i = 0; i < selected.size(); i++) {
            packs.add(selected.get(i));
        }
        return packs;
    }

    public static InputStream getStreamFromTexturePack(String path, AbstractResourcePack pack) throws IOException {
        return pack.open(path);
    }

    public static void reloadColorOverrides() {
        colorOverrides = null;
        initializeColorOverrides();
    }

    private static void initializeColorOverrides() {
        ArrayList<IResourcePack> li = getCurrentResourcePacks();
        for (int k = 0; k < li.size(); k++) {
            AbstractResourcePack pack = (AbstractResourcePack) li.get(k);
            String path = "Reika/DragonAPI/dyecolor.txt";
            try (InputStream in = getStreamFromTexturePack(path, pack)) {
                if (in != null) {
                    try (BufferedReader p = new BufferedReader(new InputStreamReader(in, "UTF-8"))) {
                        colorOverrides = p.readLine() + " from " + pack;
                    }
                }
            }
            catch (IOException e) {
                colorOverrides = "error";
            }
        }
    }
}
