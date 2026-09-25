package dev.akashic.asynciconcompat.mixins;

import java.util.concurrent.atomic.AtomicIntegerArray;

import org.embeddedt.embeddium.impl.gl.device.CommandList;
import org.embeddedt.embeddium.impl.gl.device.MultiDrawBatch;
import org.embeddedt.embeddium.impl.gl.tessellation.GlPrimitiveType;
import org.embeddedt.embeddium.impl.gl.tessellation.GlTessellation;
import org.embeddedt.embeddium.impl.render.chunk.ChunkRenderMatrices;
import org.embeddedt.embeddium.impl.render.chunk.DefaultChunkRenderer;
import org.embeddedt.embeddium.impl.render.chunk.data.SectionRenderDataStorage;
import org.embeddedt.embeddium.impl.render.chunk.lists.ChunkRenderList;
import org.embeddedt.embeddium.impl.render.chunk.lists.ChunkRenderListIterable;
import org.embeddedt.embeddium.impl.render.chunk.region.RenderRegion;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.embeddedt.embeddium.impl.render.viewport.CameraTransform;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.gtnewhorizons.angelica.glsm.backend.BackendManager;
import com.gtnewhorizons.angelica.glsm.backend.RenderBackend;

@Mixin(value = DefaultChunkRenderer.class, remap = false)
public abstract class MixinDefaultChunkRendererDrawSubmissionProbe {
    @Shadow
    private TerrainRenderPass currentRenderPass;

    @Unique private static final AtomicIntegerArray akashic$renderSamples = new AtomicIntegerArray(4);
    @Unique private static final AtomicIntegerArray akashic$executeSamples = new AtomicIntegerArray(4);

    @Unique private static final int GL_CURRENT_PROGRAM = 35725;
    @Unique private static final int GL_ACTIVE_TEXTURE = 34016;
    @Unique private static final int GL_TEXTURE_BINDING_2D = 32873;
    @Unique private static final int GL_DRAW_FRAMEBUFFER_BINDING = 36006;
    @Unique private static final int GL_DEPTH_TEST = 2929;
    @Unique private static final int GL_CULL_FACE = 2884;
    @Unique private static final int GL_BLEND = 3042;

    @Inject(
        method = "render(Lorg/embeddedt/embeddium/impl/render/chunk/ChunkRenderMatrices;Lorg/embeddedt/embeddium/impl/gl/device/CommandList;Lorg/embeddedt/embeddium/impl/render/chunk/lists/ChunkRenderListIterable;Lorg/embeddedt/embeddium/impl/render/chunk/terrain/TerrainRenderPass;Lorg/embeddedt/embeddium/impl/render/viewport/CameraTransform;Lorg/embeddedt/embeddium/impl/render/viewport/CameraTransform;)V",
        at = @At("HEAD"),
        remap = false)
    private void akashic$probeRenderLists(
        ChunkRenderMatrices matrices,
        CommandList commandList,
        ChunkRenderListIterable renderLists,
        TerrainRenderPass pass,
        CameraTransform occlusionCamera,
        CameraTransform camera,
        CallbackInfo ci) {

        final int slot = akashic$slot(pass);
        final int sample = akashic$renderSamples.incrementAndGet(slot);
        if (sample > 12) return;

        try {
            final boolean hasPass = renderLists != null && renderLists.hasPass(pass);
            final int regions = renderLists == null ? -1 : renderLists.getNumRegions();
            int storages = 0;
            int geometrySections = 0;

            if (renderLists != null) {
                for (int i = 0; i < regions; i++) {
                    final ChunkRenderList list = renderLists.getRegion(i);
                    if (list == null) continue;
                    geometrySections += list.getSectionsWithGeometryCount();
                    final RenderRegion region = list.getRegion();
                    if (region == null) continue;
                    final SectionRenderDataStorage storage = region.getStorage(pass);
                    if (storage != null) storages++;
                }
            }

            final Matrix4fc projection = matrices == null ? null : matrices.projection();
            final Matrix4fc modelView = matrices == null ? null : matrices.modelView();

            System.out.println(
                "[Akashic-ThePatcher/DrawSubmission] render pass=" + akashic$name(pass)
                    + " sample=" + sample
                    + " hasPass=" + hasPass
                    + " regions=" + regions
                    + " storages=" + storages
                    + " geometrySections=" + geometrySections
                    + " camera=" + akashic$camera(camera)
                    + " occCamera=" + akashic$camera(occlusionCamera)
                    + " projection=" + akashic$matrix(projection)
                    + " modelView=" + akashic$matrix(modelView));
        } catch (Throwable failure) {
            System.out.println(
                "[Akashic-ThePatcher/DrawSubmission] render-probe-failed pass="
                    + akashic$name(pass) + " sample=" + sample
                    + " type=" + failure.getClass().getName()
                    + " message=" + String.valueOf(failure.getMessage()));
        }
    }

    @Inject(
        method = "executeBatch(Lorg/embeddedt/embeddium/impl/gl/device/CommandList;Lorg/embeddedt/embeddium/impl/gl/device/MultiDrawBatch;Lorg/embeddedt/embeddium/impl/gl/tessellation/GlTessellation;Lorg/embeddedt/embeddium/impl/gl/tessellation/GlPrimitiveType;)V",
        at = @At("HEAD"),
        remap = false)
    private void akashic$probeExecuteBatch(
        CommandList commandList,
        MultiDrawBatch batch,
        GlTessellation tessellation,
        GlPrimitiveType primitive,
        CallbackInfo ci) {

        final TerrainRenderPass pass = this.currentRenderPass;
        final int slot = akashic$slot(pass);
        final int sample = akashic$executeSamples.incrementAndGet(slot);
        if (sample > 24) return;

        try {
            final RenderBackend backend = BackendManager.RENDER_BACKEND;
            final int program = backend.getInteger(GL_CURRENT_PROGRAM);
            final int activeTexture = backend.getInteger(GL_ACTIVE_TEXTURE);
            final int texture2d = backend.getInteger(GL_TEXTURE_BINDING_2D);
            final int framebuffer = backend.getInteger(GL_DRAW_FRAMEBUFFER_BINDING);
            final boolean depth = backend.getBoolean(GL_DEPTH_TEST);
            final boolean cull = backend.getBoolean(GL_CULL_FACE);
            final boolean blend = backend.getBoolean(GL_BLEND);

            System.out.println(
                "[Akashic-ThePatcher/DrawSubmission] execute pass=" + akashic$name(pass)
                    + " sample=" + sample
                    + " batchClass=" + (batch == null ? "null" : batch.getClass().getName())
                    + " draws=" + (batch == null ? -1 : batch.size())
                    + " indexBufferSize=" + (batch == null ? -1 : batch.getIndexBufferSize())
                    + " empty=" + (batch == null || batch.isEmpty())
                    + " tessellation=" + (tessellation == null ? "null" : tessellation.getClass().getName())
                    + " primitive=" + String.valueOf(primitive)
                    + " program=" + program
                    + " framebuffer=" + framebuffer
                    + " activeTexture=" + activeTexture
                    + " texture2D=" + texture2d
                    + " depth=" + depth
                    + " cull=" + cull
                    + " blend=" + blend);
        } catch (Throwable failure) {
            System.out.println(
                "[Akashic-ThePatcher/DrawSubmission] execute-probe-failed pass="
                    + akashic$name(pass) + " sample=" + sample
                    + " type=" + failure.getClass().getName()
                    + " message=" + String.valueOf(failure.getMessage()));
        }
    }

    @Unique
    private static int akashic$slot(TerrainRenderPass pass) {
        final String name = akashic$name(pass);
        if ("solid".equalsIgnoreCase(name)) return 0;
        if ("cutout_mipped".equalsIgnoreCase(name)) return 1;
        if ("translucent".equalsIgnoreCase(name)) return 2;
        return 3;
    }

    @Unique
    private static String akashic$name(TerrainRenderPass pass) {
        if (pass == null) return "null";
        try {
            return pass.name();
        } catch (Throwable ignored) {
            return String.valueOf(pass);
        }
    }

    @Unique
    private static String akashic$camera(CameraTransform camera) {
        if (camera == null) return "null";
        return "[" + camera.x + "," + camera.y + "," + camera.z
            + "|i=" + camera.intX + "," + camera.intY + "," + camera.intZ
            + "|f=" + camera.fracX + "," + camera.fracY + "," + camera.fracZ + "]";
    }

    @Unique
    private static String akashic$matrix(Matrix4fc matrix) {
        if (matrix == null) return "null";
        return "["
            + matrix.m00() + "," + matrix.m11() + "," + matrix.m22() + ","
            + matrix.m23() + "," + matrix.m32() + "," + matrix.m33()
            + "|det=" + matrix.determinant() + "]";
    }
}
