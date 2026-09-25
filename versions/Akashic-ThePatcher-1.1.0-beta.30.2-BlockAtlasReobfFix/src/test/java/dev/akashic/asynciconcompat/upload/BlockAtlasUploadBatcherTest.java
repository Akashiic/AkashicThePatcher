package dev.akashic.asynciconcompat.upload;

import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

public class BlockAtlasUploadBatcherTest {

    @Test
    public void copyRectUsesOnlyUsefulPrefix() {
        int[] source = {
            1, 2,
            3, 4,
            99, 98, 97, 96
        };
        int[] atlas = new int[6 * 5];

        BlockAtlasUploadBatcher.copyRect(
            source,
            2,
            2,
            atlas,
            6,
            5,
            3,
            1);

        Assert.assertEquals(1, atlas[1 * 6 + 3]);
        Assert.assertEquals(2, atlas[1 * 6 + 4]);
        Assert.assertEquals(3, atlas[2 * 6 + 3]);
        Assert.assertEquals(4, atlas[2 * 6 + 4]);
        Assert.assertEquals(0, atlas[3 * 6 + 3]);
    }

    @Test
    public void mergesContiguousGridWithoutChangingArea() {
        List<BlockAtlasUploadBatcher.Rect> input =
            new ArrayList<BlockAtlasUploadBatcher.Rect>();

        input.add(new BlockAtlasUploadBatcher.Rect(0, 0, 16, 16));
        input.add(new BlockAtlasUploadBatcher.Rect(16, 0, 16, 16));
        input.add(new BlockAtlasUploadBatcher.Rect(0, 16, 16, 16));
        input.add(new BlockAtlasUploadBatcher.Rect(16, 16, 16, 16));

        List<BlockAtlasUploadBatcher.Rect> merged =
            BlockAtlasUploadBatcher.mergeRectsForTest(input);

        Assert.assertEquals(1, merged.size());
        BlockAtlasUploadBatcher.Rect r = merged.get(0);
        Assert.assertEquals(0, r.x);
        Assert.assertEquals(0, r.y);
        Assert.assertEquals(32, r.width);
        Assert.assertEquals(32, r.height);
        Assert.assertEquals(1024L, r.area());
    }

    @Test
    public void doesNotMergeAcrossGap() {
        List<BlockAtlasUploadBatcher.Rect> input =
            new ArrayList<BlockAtlasUploadBatcher.Rect>();

        input.add(new BlockAtlasUploadBatcher.Rect(0, 0, 16, 16));
        input.add(new BlockAtlasUploadBatcher.Rect(17, 0, 16, 16));

        List<BlockAtlasUploadBatcher.Rect> merged =
            BlockAtlasUploadBatcher.mergeRectsForTest(input);

        Assert.assertEquals(2, merged.size());
    }

    @Test
    public void mergesVerticalExactSpan() {
        List<BlockAtlasUploadBatcher.Rect> input =
            new ArrayList<BlockAtlasUploadBatcher.Rect>();

        input.add(new BlockAtlasUploadBatcher.Rect(32, 0, 8, 8));
        input.add(new BlockAtlasUploadBatcher.Rect(32, 8, 8, 8));
        input.add(new BlockAtlasUploadBatcher.Rect(32, 16, 8, 8));

        List<BlockAtlasUploadBatcher.Rect> merged =
            BlockAtlasUploadBatcher.mergeRectsForTest(input);

        Assert.assertEquals(1, merged.size());
        Assert.assertEquals(8, merged.get(0).width);
        Assert.assertEquals(24, merged.get(0).height);
        Assert.assertEquals(192L, merged.get(0).area());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsShortSource() {
        BlockAtlasUploadBatcher.copyRect(
            new int[] {1, 2, 3},
            2,
            2,
            new int[16],
            4,
            4,
            0,
            0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsOutOfBoundsDestination() {
        BlockAtlasUploadBatcher.copyRect(
            new int[] {1, 2, 3, 4},
            2,
            2,
            new int[16],
            4,
            4,
            3,
            3);
    }
}
