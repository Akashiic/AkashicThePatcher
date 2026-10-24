package dev.akashic.asynciconcompat.mixins;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildBuffers;
import org.embeddedt.embeddium.impl.render.chunk.terrain.material.Material;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(
    targets = "com.gtnewhorizons.angelica.rendering.celeritas.AngelicaChunkBuildContext",
    remap = false)
public abstract class MixinAngelicaChunkBuildContextVertexAlpha {

    @Unique
    private static final Logger akashic$logger =
        LogManager.getLogger("AkashicAsyncCompat/VertexAlpha");

    @Unique
    private static final AtomicInteger akashic$calls = new AtomicInteger();
    @Unique
    private static final AtomicLong akashic$opaqueVertices = new AtomicLong();

    @Unique
    private static final AtomicLong akashic$opaqueAlpha255 = new AtomicLong();

    @Unique
    private static final AtomicLong akashic$opaqueAlphaLt255 = new AtomicLong();

    @Unique
    private static final AtomicLong akashic$opaqueAlphaZero = new AtomicLong();

    @Unique
    private static final AtomicLong akashic$sortedVertices = new AtomicLong();

    @Unique
    private static final AtomicLong akashic$sortedAlpha255 = new AtomicLong();

    @Unique
    private static final AtomicLong akashic$sortedAlphaLt255 = new AtomicLong();

    @Unique
    private static final AtomicLong akashic$sortedAlphaZero = new AtomicLong();
    @Inject(
        method = "copyRawBuffer([II[I[ILorg/embeddedt/embeddium/impl/render/chunk/compile/ChunkBuildBuffers;"
            + "Lorg/embeddedt/embeddium/impl/render/chunk/terrain/material/Material;ZZ)V",
        at = @At("HEAD"),
        remap = false)
    private void akashic$probeVertexAlpha(
        int[] rawBuffer,
        int vertexCount,
        int[] quadFlags,
        int[] quadLightFlags,
        ChunkBuildBuffers buffers,
        Material material,
        boolean block,
        boolean fluid,
        CallbackInfo ci) {

        final int call = akashic$calls.incrementAndGet();
        if (call > 256 || rawBuffer == null || vertexCount <= 0 || material == null
            || material.pass == null) {
            return;
        }

        final int availableVertices = rawBuffer.length / 8;
        final int sampleVertices = Math.min(Math.min(vertexCount, availableVertices), 256);
        int minAlpha = 255;
        int maxAlpha = 0;
        int alpha255 = 0;
        int alphaLt255 = 0;
        int alphaZero = 0;

        for (int vertex = 0; vertex < sampleVertices; vertex++) {
            final int colorIndex = vertex * 8 + 5;
            final int alpha = (rawBuffer[colorIndex] >>> 24) & 0xFF;

            if (alpha < minAlpha) minAlpha = alpha;
            if (alpha > maxAlpha) maxAlpha = alpha;

            if (alpha == 255) {
                alpha255++;
            } else {
                alphaLt255++;
                if (alpha == 0) alphaZero++;
            }
        }

        final boolean sorted = material.pass.isSorted();
        akashic$accumulate(sorted, sampleVertices, alpha255, alphaLt255, alphaZero);
        if (call <= 24 || call == 32 || call == 64 || call == 128 || call == 256) {
            akashic$logger.info(
                "VertexAlpha call={} pass={} sorted={} verts={}/{} "
                    + "alpha[min={},max={},zero={},lt255={},eq255={}] "
                    + "aggregateOpaque[v={},zero={},lt255={},eq255={}] "
                    + "aggregateSorted[v={},zero={},lt255={},eq255={}]",
                call,
                material.pass.name(),
                sorted,
                sampleVertices,
                vertexCount,
                minAlpha,
                maxAlpha,
                alphaZero,
                alphaLt255,
                alpha255,
                akashic$opaqueVertices.get(),
                akashic$opaqueAlphaZero.get(),
                akashic$opaqueAlphaLt255.get(),
                akashic$opaqueAlpha255.get(),
                akashic$sortedVertices.get(),
                akashic$sortedAlphaZero.get(),
                akashic$sortedAlphaLt255.get(),
                akashic$sortedAlpha255.get());
        }
    }
    @Unique
    private static void akashic$accumulate(
        boolean sorted,
        int vertices,
        int alpha255,
        int alphaLt255,
        int alphaZero) {

        if (sorted) {
            akashic$sortedVertices.addAndGet(vertices);
            akashic$sortedAlpha255.addAndGet(alpha255);
            akashic$sortedAlphaLt255.addAndGet(alphaLt255);
            akashic$sortedAlphaZero.addAndGet(alphaZero);
        } else {
            akashic$opaqueVertices.addAndGet(vertices);
            akashic$opaqueAlpha255.addAndGet(alpha255);
            akashic$opaqueAlphaLt255.addAndGet(alphaLt255);
            akashic$opaqueAlphaZero.addAndGet(alphaZero);
        }
    }
}
