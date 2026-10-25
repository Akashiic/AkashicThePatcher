package com.akashic.thepatcher.module.necrotempus;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.util.ResourceLocation;

/**
 * Runtime side of NecroTempusAngelicaFontTransformer: the getTexture(C)I added
 * to NecroTempus' Angelica font providers calls textureId(...).
 *
 * Compiled against SRG-named stubs (the names the client runs with):
 * func_71410_x = Minecraft.getMinecraft, func_110434_K = getTextureManager,
 * func_110581_b = TextureManager.getTexture, func_110552_b =
 * ITextureObject.getGlTextureId. NecroTempus 2.1.0 calls the first three with
 * these names; Angelica 2.2.37 calls the fourth.
 */
public final class NecroTempusAngelicaFontHooks {

    /**
     * Returned for a glyph whose texture is not uploaded yet. Angelica treats
     * only 0 as "untextured"; NecroTempus' pushDrawCmd redirect takes over the
     * draw commands that carry this value, so it is never bound. NecroTempus
     * binds (and so loads) the texture itself when it renders the glyph.
     */
    public static final int PENDING_TEXTURE = -1;

    private NecroTempusAngelicaFontHooks() {
    }

    public static int textureId(ResourceLocation location) {
        if (location == null) {
            return 0;
        }
        final ITextureObject texture = Minecraft.func_71410_x().func_110434_K().func_110581_b(location);
        final int id = texture != null ? texture.func_110552_b() : 0;
        return id > 0 ? id : PENDING_TEXTURE;
    }
}
