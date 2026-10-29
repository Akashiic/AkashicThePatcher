package net.minecraft.client.renderer.texture;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.util.ResourceLocation;

/** Compile-time stub with SRG names. Never shipped. */
public class TextureManager {
    public final Map<ResourceLocation, ITextureObject> textures = new HashMap<ResourceLocation, ITextureObject>();

    public ITextureObject func_110581_b(ResourceLocation location) { return textures.get(location); }
}
