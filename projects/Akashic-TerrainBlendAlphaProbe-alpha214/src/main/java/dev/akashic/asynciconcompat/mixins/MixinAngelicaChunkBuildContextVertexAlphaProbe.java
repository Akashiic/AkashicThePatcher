package dev.akashic.asynciconcompat.mixins;

import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildBuffers;
import org.embeddedt.embeddium.impl.render.chunk.terrain.material.Material;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.akashic.asynciconcompat.graphics.TerrainVertexAlphaProbe;

@Pseudo
@Mixin(
    targets = "com.gtnewhorizons.angelica.rendering.celeritas.AngelicaChunkBuildContext",
    remap = false)
public abstract class MixinAngelicaChunkBuildContextVertexAlphaProbe {

    @Inject(
        method = "copyRawBuffer([II[I[ILorg/embeddedt/embeddium/impl/render/chunk/compile/ChunkBuildBuffers;"
            + "Lorg/embeddedt/embeddium/impl/render/chunk/terrain/material/Material;ZZ)V",
        at = @At("HEAD"),
        remap = false)
    private void akashic$probeVertexAlpha(
        int[] rawBuffer,
        int vertexCount,
        int[] vertexStates,
        int[] shaderOverrideBlockIds,
        ChunkBuildBuffers buffers,
        Material material,
        boolean isShaderPackOverride,
        boolean blockAllowsSmoothLighting,
        CallbackInfo ci) {

        TerrainVertexAlphaProbe.observe(rawBuffer, vertexCount, material);
    }
}
