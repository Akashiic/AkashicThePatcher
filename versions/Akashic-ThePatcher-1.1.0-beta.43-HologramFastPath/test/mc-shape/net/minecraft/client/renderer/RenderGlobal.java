package net.minecraft.client.renderer;

import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.profiler.Profiler;

/** Gate shape of renderEntities' profiler sections (the entity loop, then "blockentities"). Never shipped. */
public class RenderGlobal {
    public final Profiler theProfiler = new Profiler();

    public void renderEntities(EntityLivingBase viewer, ICamera camera, float partialTicks) {
        theProfiler.endStartSection("entities");
        theProfiler.endStartSection("blockentities");
        theProfiler.endStartSection("done");
    }
}
