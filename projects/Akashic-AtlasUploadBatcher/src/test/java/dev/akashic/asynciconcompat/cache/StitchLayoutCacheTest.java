package dev.akashic.asynciconcompat.cache;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import net.minecraft.client.renderer.texture.Stitcher;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

import org.junit.Test;

public class StitchLayoutCacheTest {

    @Test
    public void fingerprintIsIndependentOfHolderIterationOrder() throws Exception {
        Stitcher.Holder a = holder("akashic:a", 16, 32, 0);
        Stitcher.Holder b = holder("akashic:b", 64, 16, 0);

        String forward = StitchLayoutCache.fingerprint(
            Arrays.asList(a, b), 0, 4096, 4096, true, 0);
        String reversed = StitchLayoutCache.fingerprint(
            Arrays.asList(b, a), 0, 4096, 4096, true, 0);

        assertEquals(forward, reversed);
        assertEquals(64, forward.length());
    }

    @Test
    public void savesLoadsAndRejectsCorruption() throws Exception {
        File gameDirectory = Files.createTempDirectory("akashic-stitch-cache-test").toFile();

        Stitcher.Holder a = holder("akashic:a", 16, 32, 0);
        Stitcher.Holder b = holder("akashic:b", 64, 16, 0);
        List<Stitcher.Holder> holders = Arrays.asList(a, b);

        String fingerprint = StitchLayoutCache.fingerprint(
            holders, 0, 4096, 4096, true, 0);

        List<Stitcher.Slot> roots = new ArrayList<>();
        Stitcher.Slot slotA = new Stitcher.Slot(0, 0, a.getWidth(), a.getHeight());
        Stitcher.Slot slotB = new Stitcher.Slot(64, 0, b.getWidth(), b.getHeight());
        assertTrue(slotA.addSlot(a));
        assertTrue(slotB.addSlot(b));
        roots.add(slotA);
        roots.add(slotB);

        assertTrue(StitchLayoutCache.save(
            gameDirectory,
            fingerprint,
            128,
            64,
            roots,
            holders));

        StitchLayoutCache.Layout layout = StitchLayoutCache.load(
            gameDirectory,
            fingerprint,
            holders,
            4096,
            4096);

        assertNotNull(layout);
        assertEquals(128, layout.atlasWidth);
        assertEquals(64, layout.atlasHeight);
        assertEquals(2, layout.entries.size());
        assertEquals("akashic:a", layout.entries.get(0).name);
        assertEquals("akashic:b", layout.entries.get(1).name);

        File cacheFile = StitchLayoutCache.cacheFile(gameDirectory, fingerprint);
        assertTrue(cacheFile.isFile());

        try (RandomAccessFile file = new RandomAccessFile(cacheFile, "rw")) {
            long last = file.length() - 1L;
            file.seek(last);
            int value = file.readUnsignedByte();
            file.seek(last);
            file.writeByte(value ^ 0x5a);
        }

        assertNull(StitchLayoutCache.load(
            gameDirectory,
            fingerprint,
            holders,
            4096,
            4096));
        assertFalse(cacheFile.exists());
    }

    @Test
    public void changedSpriteDimensionsInvalidateFingerprint() {
        Stitcher.Holder original = holder("akashic:a", 16, 16, 0);
        Stitcher.Holder changed = holder("akashic:a", 32, 16, 0);

        String before = StitchLayoutCache.fingerprint(
            Collections.singletonList(original), 0, 4096, 4096, true, 0);
        String after = StitchLayoutCache.fingerprint(
            Collections.singletonList(changed), 0, 4096, 4096, true, 0);

        assertFalse(before.equals(after));
    }

    private static Stitcher.Holder holder(String name, int width, int height, int mipmap) {
        TextureAtlasSprite sprite = new TestSprite(name);
        sprite.setIconWidth(width);
        sprite.setIconHeight(height);
        return new Stitcher.Holder(sprite, mipmap);
    }

    private static final class TestSprite extends TextureAtlasSprite {
        TestSprite(String name) {
            super(name);
        }
    }
}