package com.akashic.thepatcher.module.angelica;

import java.nio.file.Path;

/** Called by Angelica's ShaderPack constructor instead of Path::toString (see AngelicaShaderPackFolderTransformer). */
public final class AngelicaShaderPackFolderHooks {

    private AngelicaShaderPackFolderHooks() {}

    /**
     * The file name of a shader pack sub-directory without the trailing '/' that Java 8's
     * zip file system keeps on directory names ("world0/" instead of "world0").
     * Identical to Path.toString() for every other path and on Java 9+.
     */
    public static String folderName(Path path) {
        final String name = path.toString();
        int end = name.length();
        while (end > 1 && name.charAt(end - 1) == '/') {
            end--;
        }
        return end == name.length() ? name : name.substring(0, end);
    }
}
