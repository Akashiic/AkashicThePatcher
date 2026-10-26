package com.seibel.distanthorizons.api.interfaces.render;

/** Compile-time shape of Distant Horizons' IDhApiRenderProxy (only what beta.42 uses). Never shipped. */
public interface IDhApiRenderProxy {
    void setDeferTransparentRendering(boolean deferTransparentRendering);

    boolean getDeferTransparentRendering();
}
