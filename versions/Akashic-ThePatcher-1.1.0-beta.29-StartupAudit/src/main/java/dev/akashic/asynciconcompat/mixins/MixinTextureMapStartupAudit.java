package dev.akashic.asynciconcompat.mixins;

import dev.akashic.asynciconcompat.startup.StartupAtlasAudit;
import net.minecraft.client.renderer.texture.TextureMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = TextureMap.class, remap = false)
public abstract class MixinTextureMapStartupAudit {
    @Inject(method = "func_110571_b", at = @At("HEAD"), remap = false)
    private void akashic$startupAuditBegin(CallbackInfo ci) {
        StartupAtlasAudit.begin(this);
    }

    @Inject(method = "func_110571_b", at = @At("RETURN"), remap = false)
    private void akashic$startupAuditFinish(CallbackInfo ci) {
        StartupAtlasAudit.finish(this);
    }
}
