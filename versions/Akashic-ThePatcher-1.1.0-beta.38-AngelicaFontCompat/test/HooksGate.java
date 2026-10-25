import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.util.ResourceLocation;

import com.akashic.thepatcher.module.necrotempus.NecroTempusAngelicaFontHooks;

/** NecroTempusAngelicaFontHooks.textureId against the SRG-named stubs. */
public final class HooksGate {
    public static void main(String[] args) {
        final TextureManager manager = new TextureManager();
        Minecraft.stubInstall(manager);
        final ResourceLocation loaded = new ResourceLocation("necrotempus:glyphs/a.png");
        final ResourceLocation unallocated = new ResourceLocation("necrotempus:glyphs/b.png");
        manager.textures.put(loaded, texture(42));
        manager.textures.put(unallocated, texture(0));
        expect(NecroTempusAngelicaFontHooks.textureId(null), 0, "null location");
        expect(NecroTempusAngelicaFontHooks.textureId(loaded), 42, "loaded texture");
        expect(NecroTempusAngelicaFontHooks.textureId(unallocated), -1, "texture without GL id");
        expect(NecroTempusAngelicaFontHooks.textureId(new ResourceLocation("necrotempus:glyphs/c.png")), -1, "not loaded");
        System.out.println("HOOKS PASS");
    }

    static ITextureObject texture(final int id) {
        return new ITextureObject() {
            @Override public int func_110552_b() { return id; }
        };
    }

    static void expect(int actual, int expected, String what) {
        if (actual != expected) {
            throw new AssertionError(what + ": " + actual + " != " + expected);
        }
    }
}
