package org.lwjgl;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;

public final class BufferUtils {
    public static IntBuffer createIntBuffer(int size) {
        return ByteBuffer.allocateDirect(size * 4).order(ByteOrder.nativeOrder()).asIntBuffer();
    }

    private BufferUtils() {}
}
