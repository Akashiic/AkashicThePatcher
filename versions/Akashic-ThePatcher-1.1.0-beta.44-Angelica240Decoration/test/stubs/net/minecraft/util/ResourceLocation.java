package net.minecraft.util;

/** Compile-time stub. Never shipped. */
public class ResourceLocation {
    private final String path;

    public ResourceLocation(String path) { this.path = path; }

    @Override public boolean equals(Object o) { return o instanceof ResourceLocation && ((ResourceLocation) o).path.equals(path); }

    @Override public int hashCode() { return path.hashCode(); }
}
