package net.minecraft.client.resources;

import java.io.IOException;
import java.io.InputStream;

/** Test stub: a file/zip pack, the only kind DragonAPI can read by raw path. Never shipped. */
public abstract class AbstractResourcePack implements IResourcePack {
    protected abstract InputStream getInputStreamByName(String name) throws IOException;

    /** Test-only access for the DragonAPI shape (the real DragonAPI uses reflection). */
    public final InputStream open(String name) throws IOException {
        return getInputStreamByName(name);
    }
}
