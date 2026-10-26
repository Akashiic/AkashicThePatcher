package net.coderbot.iris.shaderpack;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;

/**
 * Shape of Angelica 2.2.37's ShaderPack dimension-folder scan (ShaderPack.java lines 173-180, LGPL-3.0): the same
 * stream with the same method references, so the transformed bytecode can be executed. Never shipped.
 */
public class ShaderPack {
    public final Set<String> foundFolders = new TreeSet<String>();

    public ShaderPack(Path root) throws IOException {
        Files.list(root)
            .filter(Files::isDirectory)
            .map(Path::getFileName)
            .map(Path::toString)
            .filter(name -> name.matches("world-?\\d+"))
            .forEach(foundFolders::add);
    }
}
