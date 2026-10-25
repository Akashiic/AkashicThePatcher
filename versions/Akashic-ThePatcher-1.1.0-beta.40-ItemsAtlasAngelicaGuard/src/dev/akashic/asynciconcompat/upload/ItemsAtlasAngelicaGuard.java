package dev.akashic.asynciconcompat.upload;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * beta.40: keeps ItemsAtlasUploadBatcher from overwriting the items atlas with texels it never withheld.
 *
 * The batcher stages every sprite upload in a CPU copy of the whole atlas and, at flush, uploads that copy
 * over the full atlas. That is only correct when every sprite went through TextureUtil.uploadTextureMipmap.
 * Angelica 2.2.24+ (AtlasAssembler, "Parallelize atlas loading") uploads the sprites itself and skips that
 * call, so the copy is almost empty: the flush then wiped the items atlas (invisible items in NEI and in hand).
 *
 * Two rules, both called from bytecode inserted into ItemsAtlasUploadBatcher:
 *  - begin: do not arm when Angelica's AtlasAssembler is on the class path (it already batches the upload);
 *  - flush: skip the GL upload when no staged upload was cancelled (GL already has every staged texel).
 * Kill-switch: -Dakashic.itemsAtlasBatch.guard=false restores beta.39's behaviour.
 * -Dakashic.itemsAtlasBatch=false still disables the batcher entirely.
 */
public final class ItemsAtlasAngelicaGuard {
    private static final Logger LOG = LogManager.getLogger("AkashicAsyncCompat/ItemsAtlasBatch");
    static final String ATLAS_ASSEMBLER = "com/gtnewhorizons/angelica/textures/atlas/AtlasAssembler.class";

    private ItemsAtlasAngelicaGuard() {}

    static boolean enabled() {
        return Boolean.parseBoolean(System.getProperty("akashic.itemsAtlasBatch.guard", "true"));
    }

    /** @return true when begin() must not arm the batch. */
    public static boolean bypassBatch(String atlasPath) {
        if (!enabled()) return false;
        final ClassLoader loader = ItemsAtlasAngelicaGuard.class.getClassLoader();
        if (loader == null || loader.getResource(ATLAS_ASSEMBLER) == null) return false;
        LOG.info("ItemsAtlasBatch bypass path={} reason=angelica-atlas-assembler", atlasPath);
        return true;
    }

    /** @return true when flush() must drop the batch without uploading it. */
    public static boolean skipFlush(int cancelledUploads, String atlasPath, int stagedCalls, String reason) {
        if (!enabled() || cancelledUploads != 0) return false;
        LOG.info("ItemsAtlasBatch skip-flush path={} reason={} stagedCalls={} cancelled=0 (nothing withheld from GL)",
            atlasPath, reason, stagedCalls);
        return true;
    }
}
