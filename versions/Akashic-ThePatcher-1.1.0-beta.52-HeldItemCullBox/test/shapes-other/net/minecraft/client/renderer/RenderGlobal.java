package net.minecraft.client.renderer;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;

/** Another shape: one frustum test only (as if another coremod rewrote the loop). Must be left alone. */
public class RenderGlobal {
    public List<Entity> entities = new ArrayList<Entity>();
    public List<Entity> rendered = new ArrayList<Entity>();

    public void renderEntities(EntityLivingBase view, ICamera camera, float partialTicks) {
        rendered.clear();
        for (int i = 0; i < entities.size(); ++i) {
            Entity entity = entities.get(i);
            if (entity.ignoreFrustumCheck || camera.isBoundingBoxInFrustum(entity.boundingBox)) {
                rendered.add(entity);
            }
        }
    }
}
