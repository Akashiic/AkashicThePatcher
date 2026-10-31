package net.minecraft.block;

/** Test double for the hook test (tools/hooktest): a block with an id and an opacity. */
public class Block {
    public final int id;
    public final boolean opaque;

    public Block(int id, boolean opaque) {
        this.id = id;
        this.opaque = opaque;
    }

    public static int func_149682_b(Block block) {
        return block == null ? -1 : block.id;
    }

    public boolean func_149662_c() {
        return opaque;
    }
}
