package com.akashic.elnendlessids;

import cpw.mods.fml.relauncher.IFMLLoadingPlugin;

import java.util.Map;

/**
 * Coremod entry point. Registers {@link ElnEndlessIdsTransformer}, which rewrites the two places in Electrical Age
 * (1.24.6 to 1.24.8) that assume vanilla's 12-bit block ids. See README.md.
 */
@IFMLLoadingPlugin.MCVersion("1.7.10")
@IFMLLoadingPlugin.Name("Akashic-ElnEndlessIdsFix")
@IFMLLoadingPlugin.SortingIndex(1001)
public final class ElnEndlessIdsPlugin implements IFMLLoadingPlugin {
    @Override
    public String[] getASMTransformerClass() {
        return new String[] {"com.akashic.elnendlessids.ElnEndlessIdsTransformer"};
    }

    @Override
    public String getModContainerClass() {
        return null;
    }

    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {
    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }
}
