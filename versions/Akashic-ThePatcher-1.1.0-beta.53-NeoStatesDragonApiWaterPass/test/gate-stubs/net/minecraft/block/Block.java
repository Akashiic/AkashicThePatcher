package net.minecraft.block;

/** Test stub of Minecraft's Block: func_149701_w is getRenderBlockPass (SRG, as mod classes see it). Never shipped. */
public class Block {
    public final int pass;
    public final boolean holder;

    public Block(int pass, boolean holder) {
        this.pass = pass;
        this.holder = holder;
    }

    public int func_149701_w() {
        return pass;
    }

    public boolean canRenderInPass(int pass) {
        return pass == this.pass;
    }
}
