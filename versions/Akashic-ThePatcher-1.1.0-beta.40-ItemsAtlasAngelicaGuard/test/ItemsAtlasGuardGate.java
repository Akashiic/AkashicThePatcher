import java.lang.reflect.Field;

import org.lwjgl.opengl.GL11;

import dev.akashic.asynciconcompat.upload.ItemsAtlasUploadBatcher;

/**
 * ItemsAtlasUploadBatcher against the SRG stubs, with GL11 simulating the items atlas texture.
 *
 *   java ItemsAtlasGuardGate <expect>     expect = fixed | broken | angelica
 *
 * Each scenario starts from an atlas that is already filled (0x7F000000 | index), as Angelica 2.2.24+'s
 * AtlasAssembler leaves it, then runs begin -> stage(sprites) -> flushIfActive like TextureMap.loadTextureAtlas.
 *  A "assembler": one staged sprite (the first one always reaches GL), nothing cancelled - Angelica's case.
 *  B "vanilla":   three staged sprites, two cancelled - every sprite went through uploadTextureMipmap.
 * fixed:    A leaves the atlas untouched, B uploads the batch (cancelled sprites end up in the texture).
 * broken:   A overwrites the atlas with the mostly-empty staging copy (beta.39, the invisible items).
 * angelica: begin never arms (AtlasAssembler on the class path); stage returns false, no GL upload.
 */
public final class ItemsAtlasGuardGate {
    static final int SIZE = 2048;

    public static void main(String[] args) throws Exception {
        final String expect = args[0];

        // A: Angelica-style - the atlas is already complete, one staged call reaches GL.
        reset();
        ItemsAtlasUploadBatcher.begin("textures/items", 7, 0, SIZE, SIZE);
        check(!ItemsAtlasUploadBatcher.stage(sprite(16, 0x01), 16, 16, 0, 0, false, false), "A: first stage must pass");
        ItemsAtlasUploadBatcher.flushIfActive("textures/items");
        ItemsAtlasUploadBatcher.abortIfActive("textures/items");
        final int intactA = intact();
        System.out.println("A assembler subImageCalls=" + GL11.subImageCalls + " intactTexels=" + intactA);
        if (expect.equals("broken")) {
            check(GL11.subImageCalls > 0 && intactA == 0, "A: expected the full-atlas overwrite");
        } else {
            check(GL11.subImageCalls == 0 && intactA == SIZE * SIZE, "A: atlas must stay intact");
        }
        check(!ItemsAtlasUploadBatcher.stage(sprite(16, 0x02), 16, 16, 16, 0, false, false), "A: batch must be closed");

        // B: vanilla-style - every sprite is staged; the cancelled ones must reach the texture.
        reset();
        ItemsAtlasUploadBatcher.begin("textures/items", 7, 0, SIZE, SIZE);
        final boolean first = ItemsAtlasUploadBatcher.stage(sprite(16, 0x11), 16, 16, 0, 0, false, false);
        final boolean second = ItemsAtlasUploadBatcher.stage(sprite(16, 0x22), 16, 16, 16, 0, false, false);
        final boolean third = ItemsAtlasUploadBatcher.stage(sprite(32, 0x33), 32, 32, 64, 64, false, false);
        if (!first) GL11.texture[0] = 0x11; // the first call reaches vanilla's upload (only texel 0 is checked)
        ItemsAtlasUploadBatcher.flushIfActive("textures/items");
        ItemsAtlasUploadBatcher.abortIfActive("textures/items");
        System.out.println("B vanilla cancelled=" + second + "," + third + " subImageCalls=" + GL11.subImageCalls);
        if (expect.equals("angelica")) {
            check(!first && !second && !third && GL11.subImageCalls == 0, "B: batch must not arm with AtlasAssembler");
        } else {
            check(!first && second && third, "B: second and third uploads must be cancelled");
            check(GL11.subImageCalls > 0, "B: batch must be uploaded");
            check(GL11.texture[0] == 0x11 && GL11.texture[16] == 0x22 && GL11.texture[64 * SIZE + 64] == 0x33
                && GL11.texture[95 * SIZE + 95] == 0x33, "B: staged sprites missing from the texture");
        }
        System.out.println("ITEMS_ATLAS_GATE PASS expect=" + expect);
    }

    static void reset() throws Exception {
        final Field consumed = ItemsAtlasUploadBatcher.class.getDeclaredField("startupBatchConsumed");
        consumed.setAccessible(true);
        consumed.setBoolean(null, false);
        GL11.subImageCalls = 0;
        GL11.textureWidth = SIZE;
        GL11.texture = new int[SIZE * SIZE];
        for (int i = 0; i < GL11.texture.length; i++) GL11.texture[i] = 0x7F000000 | i;
    }

    static int intact() {
        int n = 0;
        for (int i = 0; i < GL11.texture.length; i++) if (GL11.texture[i] == (0x7F000000 | i)) n++;
        return n;
    }

    static int[][] sprite(int size, int value) {
        final int[] pixels = new int[size * size];
        java.util.Arrays.fill(pixels, value);
        return new int[][] { pixels };
    }

    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private ItemsAtlasGuardGate() {}
}
