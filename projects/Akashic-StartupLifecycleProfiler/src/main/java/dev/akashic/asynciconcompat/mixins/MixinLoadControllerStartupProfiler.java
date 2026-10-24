package dev.akashic.asynciconcompat.mixins;

import cpw.mods.fml.common.LoadController;
import cpw.mods.fml.common.ModContainer;
import cpw.mods.fml.common.event.FMLEvent;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.akashic.asynciconcompat.startup.StartupLifecycleProfiler;

@Mixin(value = LoadController.class, remap = false)
public abstract class MixinLoadControllerStartupProfiler {

    @Inject(
        method = "propogateStateMessage(Lcpw/mods/fml/common/event/FMLEvent;)V",
        at = @At("HEAD"))
    private void akashic$beginStartupPhase(
        FMLEvent event,
        CallbackInfo ci) {

        StartupLifecycleProfiler.beginPhase(event);
    }

    @Inject(
        method = "propogateStateMessage(Lcpw/mods/fml/common/event/FMLEvent;)V",
        at = @At("RETURN"))
    private void akashic$endStartupPhase(
        FMLEvent event,
        CallbackInfo ci) {

        StartupLifecycleProfiler.endPhase(event);
    }

    @Inject(
        method = "sendEventToModContainer(Lcpw/mods/fml/common/event/FMLEvent;Lcpw/mods/fml/common/ModContainer;)V",
        at = @At("HEAD"))
    private void akashic$beginModDispatch(
        FMLEvent event,
        ModContainer mod,
        CallbackInfo ci) {

        StartupLifecycleProfiler.beginDispatch(event, mod);
    }

    @Inject(
        method = "sendEventToModContainer(Lcpw/mods/fml/common/event/FMLEvent;Lcpw/mods/fml/common/ModContainer;)V",
        at = @At("RETURN"))
    private void akashic$endModDispatch(
        FMLEvent event,
        ModContainer mod,
        CallbackInfo ci) {

        StartupLifecycleProfiler.endDispatch(event, mod);
    }
}