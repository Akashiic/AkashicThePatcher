package dev.akashic.asynciconcompat.upload;

import static org.junit.Assert.assertArrayEquals;

import org.junit.Test;

public class ItemsAtlasUploadBatcherTest {

    @Test
    public void copiesSingleRectangleWithCorrectStride() {
        int[] source = {
            1, 2,
            3, 4
        };
        int[] atlas = new int[8 * 6];

        ItemsAtlasUploadBatcher.copyRect(
            source,
            2,
            2,
            atlas,
            8,
            6,
            3,
            2);

        int[] expected = new int[8 * 6];
        expected[2 * 8 + 3] = 1;
        expected[2 * 8 + 4] = 2;
        expected[3 * 8 + 3] = 3;
        expected[3 * 8 + 4] = 4;

        assertArrayEquals(expected, atlas);
    }

    @Test
    public void composesMultipleNonOverlappingSprites() {
        int[] atlas = new int[6 * 5];

        ItemsAtlasUploadBatcher.copyRect(
            new int[] {10, 11, 12, 13},
            2,
            2,
            atlas,
            6,
            5,
            0,
            0);

        ItemsAtlasUploadBatcher.copyRect(
            new int[] {20, 21, 22, 23, 24, 25},
            3,
            2,
            atlas,
            6,
            5,
            3,
            2);

        int[] expected = new int[6 * 5];
        expected[0] = 10;
        expected[1] = 11;
        expected[6] = 12;
        expected[7] = 13;

        expected[2 * 6 + 3] = 20;
        expected[2 * 6 + 4] = 21;
        expected[2 * 6 + 5] = 22;
        expected[3 * 6 + 3] = 23;
        expected[3 * 6 + 4] = 24;
        expected[3 * 6 + 5] = 25;

        assertArrayEquals(expected, atlas);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsOutOfBoundsRectangle() {
        ItemsAtlasUploadBatcher.copyRect(
            new int[] {1, 2, 3, 4},
            2,
            2,
            new int[4 * 4],
            4,
            4,
            3,
            3);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsShortSourceArray() {
        ItemsAtlasUploadBatcher.copyRect(
            new int[] {1, 2, 3},
            2,
            2,
            new int[4 * 4],
            4,
            4,
            0,
            0);
    }
}