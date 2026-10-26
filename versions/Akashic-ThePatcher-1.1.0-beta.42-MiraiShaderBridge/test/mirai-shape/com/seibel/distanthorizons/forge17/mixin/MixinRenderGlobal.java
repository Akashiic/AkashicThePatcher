package com.seibel.distanthorizons.forge17.mixin;

import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.entity.EntityLivingBase;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Shape of Mirai 0.1.19's MixinRenderGlobal (forge17/src/main/java/.../mixin/MixinRenderGlobal.java, LGPL-3.0):
 * the two sortAndRender injectors, with upstream's renderDeferredLodsDuringTranslucentSetup still commented out.
 * Bodies reduced to the pass checks. Never shipped.
 */
@Mixin(RenderGlobal.class)
public class MixinRenderGlobal {
    @Inject(method = "sortAndRender", at = @At("HEAD"))
    void renderLods(EntityLivingBase entity, int renderPass, double partialTicks, CallbackInfoReturnable<Integer> cir) {
        if (renderPass == 0) {
            System.out.println("A renderLods");
        }
    }

    @Inject(method = "sortAndRender", at = @At("TAIL"))
    void renderLodsFade(EntityLivingBase entity, int renderPass, double partialTicks, CallbackInfoReturnable<Integer> cir) {
        if (renderPass == 1) {
            System.out.println("A renderFade");
        }
    }
}
