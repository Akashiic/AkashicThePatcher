package net.minecraft.client;

import net.minecraft.client.renderer.texture.TextureManager;

/** Compile-time stub with the SRG names the 1.7.10 client runs with. Never shipped. */
public class Minecraft {
    private static Minecraft instance;
    private TextureManager textureManager;

    public static Minecraft func_71410_x() { return instance; }

    public TextureManager func_110434_K() { return textureManager; }

    /** Test-only. */
    public static void stubInstall(TextureManager manager) {
        instance = new Minecraft();
        instance.textureManager = manager;
    }
}
