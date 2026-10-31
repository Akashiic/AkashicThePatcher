package flaxbeard.immersivepetroleum.client.projector;

import net.minecraft.client.Minecraft;

/**
 * Test shape of Immersive Petroleum's ProjectorClient (the pack's 1.7.10 backport, 0.1.0-alpha.10 catalyst WIP):
 * the same VIEWS array and the same selectedView body, which javac compiles to the same instructions and stack map
 * frames as the real class (GETSTATIC VIEWS, the currentItem chain, one AALOAD). Never shipped.
 */
public final class ProjectorClient {
    private static final ProjectorView[] VIEWS = new ProjectorView[9];

    private ProjectorClient() {}

    public static ProjectorView selectedView() {
        Minecraft mc = Minecraft.func_71410_x();
        return mc.field_71439_g == null ? null : VIEWS[mc.field_71439_g.field_71071_by.field_70461_c];
    }

    public static void put(int slot, ProjectorView view) {
        VIEWS[slot] = view;
    }
}
