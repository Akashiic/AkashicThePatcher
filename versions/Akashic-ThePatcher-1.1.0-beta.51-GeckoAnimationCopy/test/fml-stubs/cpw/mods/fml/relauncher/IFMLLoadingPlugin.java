package cpw.mods.fml.relauncher;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Map;

/** Test stub of the FML coremod interface. Never shipped. */
public interface IFMLLoadingPlugin {
    String[] getASMTransformerClass();

    String getModContainerClass();

    String getSetupClass();

    void injectData(Map<String, Object> data);

    String getAccessTransformerClass();

    @Retention(RetentionPolicy.RUNTIME) @interface Name { String value(); }

    @Retention(RetentionPolicy.RUNTIME) @interface MCVersion { String value(); }

    @Retention(RetentionPolicy.RUNTIME) @interface SortingIndex { int value(); }

    @Retention(RetentionPolicy.RUNTIME) @interface TransformerExclusions { String[] value(); }
}
