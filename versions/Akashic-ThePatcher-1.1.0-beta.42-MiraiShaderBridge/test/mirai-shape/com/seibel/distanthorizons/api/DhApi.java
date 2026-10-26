package com.seibel.distanthorizons.api;

import com.seibel.distanthorizons.api.interfaces.render.IDhApiRenderProxy;

/** Compile-time shape of Distant Horizons' DhApi (only what beta.42 uses). Never shipped. */
public final class DhApi {
    private DhApi() {}

    public static final class Delayed {
        public static IDhApiRenderProxy renderProxy = null;

        private Delayed() {}
    }
}
