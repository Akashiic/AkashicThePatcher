package org.lwjgl.opengl;

import java.nio.IntBuffer;

/** Records texture uploads into one simulated level-0 texture. */
public final class GL11 {
    public static final int GL_TEXTURE_2D = 3553;
    public static int bound;
    public static int subImageCalls;
    public static int[] texture;
    public static int textureWidth;

    public static void glBindTexture(int target, int texture) { bound = texture; }

    public static void glTexSubImage2D(int target, int level, int x, int y, int width, int height, int format, int type,
                                       IntBuffer pixels) {
        subImageCalls++;
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                texture[(y + row) * textureWidth + x + col] = pixels.get(pixels.position() + row * width + col);
            }
        }
    }

    private GL11() {}
}
