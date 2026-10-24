from pathlib import Path
import re, shutil

p = Path(r"C:\Users\User\Desktop\Codex\Akashic-Hodgepodge-AsyncCompat\src\main\java\dev\akashic\asynciconcompat\mixins\MixinTextureMapHybridAsync.java")
bak = p.with_suffix(p.suffix + ".bak-profiler-alpha2.4")
shutil.copy2(p, bak)
t = p.read_text(encoding="utf-8")

def once(pattern, repl, label, flags=0):
    global t
    t2, n = re.subn(pattern, repl, t, count=1, flags=flags)
    if n != 1:
        shutil.copy2(bak, p)
        raise RuntimeError(f"{label}: expected 1 replacement, got {n}")
    t = t2

once(r"import java\.util\.ArrayList;", "import java.awt.image.BufferedImage;\nimport java.io.InputStream;\nimport java.util.ArrayList;", "imports-java")
once(r"import net\.minecraft\.client\.renderer\.texture\.IIconRegister;", "import net.minecraft.client.renderer.texture.IIconRegister;\nimport net.minecraft.client.renderer.texture.Stitcher;\nimport net.minecraft.client.renderer.texture.TextureUtil;", "imports-texture")
once(r"import net\.minecraft\.item\.Item;", "import net.minecraft.client.resources.IResourceManager;\nimport net.minecraft.item.Item;", "imports-resource")

field_block = r'''
    @Unique
    private long akashic$startTime;

    @Unique
    private long akashic$atlasStartNanos;

    @Unique
    private long akashic$iconRegisterStartNanos;

    @Unique
    private long akashic$iconRegisterNanos;

    @Unique
    private long akashic$imageDecodeNanos;

    @Unique
    private long akashic$stitchNanos;

    @Unique
    private long akashic$allocateNanos;

    @Unique
    private long akashic$uploadNanos;

    @Unique
    private long akashic$preHookNanos;

    @Unique
    private long akashic$postHookNanos;

    @Unique
    private int akashic$imageDecodeCalls;

    @Unique
    private int akashic$uploadCalls;
'''.strip("\n")
once(
    r"    @Unique\s+private long akashic\$startTime;",
    field_block,
    "profiler-fields",
    re.MULTILINE,
)

methods = r'''
    @Inject(method = "loadTextureAtlas", at = @At("HEAD"))
    private void akashic$startAtlasProfile(IResourceManager resourceManager, CallbackInfo ci) {
        akashic$atlasStartNanos = System.nanoTime();
        akashic$iconRegisterNanos = 0L;
        akashic$imageDecodeNanos = 0L;
        akashic$stitchNanos = 0L;
        akashic$allocateNanos = 0L;
        akashic$uploadNanos = 0L;
        akashic$preHookNanos = 0L;
        akashic$postHookNanos = 0L;
        akashic$imageDecodeCalls = 0;
        akashic$uploadCalls = 0;
    }

    @Inject(method = "loadTextureAtlas", at = @At("RETURN"))
    private void akashic$finishAtlasProfile(IResourceManager resourceManager, CallbackInfo ci) {
        final long totalNanos = System.nanoTime() - akashic$atlasStartNanos;
        final long measuredNanos = akashic$iconRegisterNanos
            + akashic$imageDecodeNanos
            + akashic$stitchNanos
            + akashic$allocateNanos
            + akashic$uploadNanos
            + akashic$preHookNanos
            + akashic$postHookNanos;
        final long residualNanos = Math.max(0L, totalNanos - measuredNanos);

        akashic$LOGGER.info(
            "AtlasProfile path={} total={}ms icons={}ms decode={}ms/{} stitch={}ms alloc={}ms upload={}ms/{} preHook={}ms postHook={}ms residual={}ms",
            basePath,
            akashic$millis(totalNanos),
            akashic$millis(akashic$iconRegisterNanos),
            akashic$millis(akashic$imageDecodeNanos),
            akashic$imageDecodeCalls,
            akashic$millis(akashic$stitchNanos),
            akashic$millis(akashic$allocateNanos),
            akashic$millis(akashic$uploadNanos),
            akashic$uploadCalls,
            akashic$millis(akashic$preHookNanos),
            akashic$millis(akashic$postHookNanos),
            akashic$millis(residualNanos));
    }

    @WrapOperation(
        method = "loadTextureAtlas",
        at = @At(
            value = "INVOKE",
            target = "Ljavax/imageio/ImageIO;read(Ljava/io/InputStream;)Ljava/awt/image/BufferedImage;"))
    private BufferedImage akashic$profileImageDecode(
        InputStream input,
        Operation<BufferedImage> original) {

        final long started = System.nanoTime();
        try {
            return original.call(input);
        } finally {
            akashic$imageDecodeNanos += System.nanoTime() - started;
            akashic$imageDecodeCalls++;
        }
    }

    @WrapOperation(
        method = "loadTextureAtlas",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/texture/Stitcher;doStitch()V"))
    private void akashic$profileStitch(
        Stitcher stitcher,
        Operation<Void> original) {

        final long started = System.nanoTime();
        try {
            original.call(stitcher);
        } finally {
            akashic$stitchNanos += System.nanoTime() - started;
        }
    }

    @WrapOperation(
        method = "loadTextureAtlas",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/texture/TextureUtil;allocateTextureImpl(IIIIF)V"))
    private void akashic$profileAllocation(
        int textureId,
        int mipmapLevels,
        int width,
        int height,
        float anisotropic,
        Operation<Void> original) {

        final long started = System.nanoTime();
        try {
            original.call(textureId, mipmapLevels, width, height, anisotropic);
        } finally {
            akashic$allocateNanos += System.nanoTime() - started;
        }
    }

    @WrapOperation(
        method = "loadTextureAtlas",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/texture/TextureUtil;uploadTextureMipmap([[IIIIIZZ)V"))
    private void akashic$profileUpload(
        int[][] data,
        int width,
        int height,
        int originX,
        int originY,
        boolean blur,
        boolean clamp,
        Operation<Void> original) {

        final long started = System.nanoTime();
        try {
            original.call(data, width, height, originX, originY, blur, clamp);
        } finally {
            akashic$uploadNanos += System.nanoTime() - started;
            akashic$uploadCalls++;
        }
    }

    @WrapOperation(
        method = "loadTextureAtlas",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraftforge/client/ForgeHooksClient;onTextureStitchedPre(Lnet/minecraft/client/renderer/texture/TextureMap;)V"))
    private void akashic$profilePreHook(
        TextureMap map,
        Operation<Void> original) {

        final long started = System.nanoTime();
        try {
            original.call(map);
        } finally {
            akashic$preHookNanos += System.nanoTime() - started;
        }
    }

    @WrapOperation(
        method = "loadTextureAtlas",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraftforge/client/ForgeHooksClient;onTextureStitchedPost(Lnet/minecraft/client/renderer/texture/TextureMap;)V"))
    private void akashic$profilePostHook(
        TextureMap map,
        Operation<Void> original) {

        final long started = System.nanoTime();
        try {
            original.call(map);
        } finally {
            akashic$postHookNanos += System.nanoTime() - started;
        }
    }

    @Unique
    private static long akashic$millis(long nanos) {
        return nanos / 1_000_000L;
    }

'''.strip("\n") + "\n\n"

once(
    r'(?m)^    @Inject\(method = "registerIcons", at = @At\("HEAD"\)\)',
    methods + '    @Inject(method = "registerIcons", at = @At("HEAD"))',
    "profiler-methods",
)

once(
    r'(?m)^(    private void akashic\$startHybridIconLoading\(CallbackInfo ci\) \{)$',
    r'\1\n        akashic$iconRegisterStartNanos = System.nanoTime();',
    "icon-start",
)

once(
    r'(?m)^(    private void akashic\$finishHybridIconLoading\(CallbackInfo ci\) \{\n        akashic\$await\("item"\);)$',
    r'\1\n        akashic$iconRegisterNanos += System.nanoTime() - akashic$iconRegisterStartNanos;',
    "icon-finish",
)

p.write_text(t, encoding="utf-8", newline="\n")
print("ATLAS_PROFILER_SOURCE_WRITTEN")