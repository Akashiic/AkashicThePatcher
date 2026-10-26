import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import javax.imageio.ImageIO;

import net.minecraft.client.resources.AbstractResourcePack;
import net.minecraft.client.resources.IResourcePack;

/**
 * DragonAPI x a resource pack that is not an AbstractResourcePack (the Waystones "Modernity Textures" pack).
 *
 *   java DragonApiGate <shapes dir> fixed|broken
 *
 * broken: the shapes throw the crash-report ClassCastException (beta.40, kill-switch, server side).
 * fixed:  the transformer edited both methods, the foreign pack is skipped, a real pack still supplies its
 *         texture and its dyecolor.txt, and with no readable pack DragonAPI falls back to the mod texture.
 * Run with -Xverify:all: the edited bytecode goes through the JVM verifier.
 */
public final class DragonApiGate {
    static final String LOADER = "Reika.DragonAPI.IO.ReikaImageLoader";
    static final String HELPER = "Reika.DragonAPI.Libraries.IO.ReikaTextureHelper";

    /** Like WaystonesAlternateResourcePack: implements IResourcePack only. */
    static final class ForeignPack implements IResourcePack {
        @Override public String toString() { return "Waystones Modernity Textures"; }
    }

    /** A folder/zip pack that has the texture and the dye colours. */
    static final class FilePack extends AbstractResourcePack {
        final byte[] png;
        FilePack() throws Exception {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB), "png", out);
            png = out.toByteArray();
        }
        @Override protected InputStream getInputStreamByName(String name) {
            if (name.equals("Reika/ChromatiCraft/Textures/TileEntity/lootchest.png")) return new ByteArrayInputStream(png);
            if (name.equals("Reika/DragonAPI/dyecolor.txt")) return new ByteArrayInputStream("black:0x191919".getBytes(StandardCharsets.UTF_8));
            return null;
        }
        @Override public String toString() { return "MC-New-Textures"; }
    }

    public static void main(String[] args) throws Exception {
        final boolean fixed = args[1].equals("fixed");
        final List<String> owned = Arrays.asList(LOADER, HELPER, LOADER + "$ImageEditor");
        final TransformingLoader loader = new TransformingLoader(DragonApiGate.class.getClassLoader(), Paths.get(args[0]),
            owned, "com.akashic.thepatcher.module.dragonapi.DragonApiResourcePackTransformer");
        final Class<?> helper = Class.forName(HELPER, true, loader);
        Class.forName(LOADER, true, loader);
        @SuppressWarnings("unchecked")
        final List<IResourcePack> selected = (List<IResourcePack>) helper.getField("selected").get(null);
        final Method bind = helper.getMethod("bindTexture", String.class);
        final Method colors = helper.getMethod("reloadColorOverrides");
        final String texture = "Reika/ChromatiCraft/Textures/TileEntity/lootchest.png";
        final ForeignPack foreign = new ForeignPack();
        final FilePack file = new FilePack();

        // The crash report's pack list: [MC-New-Textures, Waystones Modernity Textures].
        selected.clear();
        selected.add(file);
        selected.add(foreign);
        final String bound = call(bind, texture);
        final String colorResult = call(colors) == null ? String.valueOf(helper.getField("colorOverrides").get(null)) : "CCE";
        selected.clear();
        selected.add(foreign);
        final String fallback = call(bind, texture);
        selected.clear();
        selected.add(foreign);
        selected.add(file);
        final String foreignFirst = call(bind, texture);

        System.out.println("A crash-report packs bindTexture -> " + bound);
        System.out.println("A crash-report packs colorOverrides -> " + colorResult);
        System.out.println("A foreign pack only bindTexture -> " + fallback);
        System.out.println("A foreign pack first bindTexture -> " + foreignFirst);
        System.out.println("A transformed classes = " + loader.transformed);
        if (fixed) {
            check(loader.transformed == 2, "both DragonAPI classes must be transformed");
            check(bound.equals("MC-New-Textures"), "real pack texture lost: " + bound);
            check(colorResult.equals("black:0x191919 from MC-New-Textures"), "dye colours lost: " + colorResult);
            check(fallback.equals("default"), "no fallback to the mod texture: " + fallback);
            check(foreignFirst.equals("MC-New-Textures"), "pack order: " + foreignFirst);
        } else {
            check(loader.transformed == 0, "nothing may be transformed");
            check(bound.equals("CCE") && colorResult.equals("CCE") && fallback.equals("CCE"), "crash not reproduced");
        }
        System.out.println("DRAGONAPI_GATE PASS expect=" + args[1]);
    }

    /** Returns the method's result as a String, "CCE" for the crash-report ClassCastException. */
    static String call(Method method, Object... args) throws Exception {
        try {
            final Object result = method.invoke(null, args);
            return result == null ? null : result.toString();
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof ClassCastException
                && e.getCause().getMessage().contains("net.minecraft.client.resources.AbstractResourcePack")) {
                return "CCE";
            }
            throw e;
        }
    }

    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
