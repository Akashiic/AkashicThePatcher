package Reika.DragonAPI.IO;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

import javax.imageio.ImageIO;

import net.minecraft.client.resources.AbstractResourcePack;
import net.minecraft.client.resources.IResourcePack;

import Reika.DragonAPI.Libraries.IO.ReikaTextureHelper;

/**
 * Test reconstruction of the shape of DragonAPI V33b's ReikaImageLoader.getImageFromResourcePack (not DragonAPI's
 * code): an optional log line, then an unchecked cast of the pack, then a try-with-resources read. Never shipped.
 */
public final class ReikaImageLoader {
    public static boolean logLoading = Boolean.getBoolean("stub.dragonapi.log");

    private ReikaImageLoader() {}

    public interface ImageEditor {
        int getRGB(int raw);
    }

    public static BufferedImage getImageFromResourcePack(String path, IResourcePack res, ImageEditor editor) {
        if (logLoading)
            System.out.println("Loading image at " + path + " from resourcepack " + res);
        AbstractResourcePack pack = (AbstractResourcePack) res;
        try (InputStream in = ReikaTextureHelper.getStreamFromTexturePack(path, pack)) {
            if (in == null) {
                if (logLoading)
                    System.out.println("Texture pack image at " + path + " not found in " + res + ".");
                return null;
            }
            return ImageIO.read(in);
        }
        catch (IOException e) {
            return null;
        }
    }
}
