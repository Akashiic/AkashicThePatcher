package net.minecraft.client.renderer;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;

/** Gate shape of 1.7.10's renderEntities culling: the frustum test of each entity, then of its leash holder. */
public class RenderGlobal {
    public List<Entity> entities = new ArrayList<Entity>();
    public List<Entity> rendered = new ArrayList<Entity>();

    public void renderEntities(EntityLivingBase view, ICamera camera, float partialTicks) {
        rendered.clear();
        for (int i = 0; i < entities.size(); ++i) {
            Entity entity = entities.get(i);
            boolean flag = entity.ignoreFrustumCheck || camera.isBoundingBoxInFrustum(entity.boundingBox) || entity == view;
            if (!flag && entity instanceof EntityLiving) {
                EntityLiving living = (EntityLiving) entity;
                if (living.leashHolder != null) {
                    Entity holder = living.leashHolder;
                    flag = camera.isBoundingBoxInFrustum(holder.boundingBox);
                }
            }
            if (flag) {
                rendered.add(entity);
            }
        }
    }
}
