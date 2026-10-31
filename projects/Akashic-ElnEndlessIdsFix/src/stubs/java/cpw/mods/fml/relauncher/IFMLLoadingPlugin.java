package cpw.mods.fml.relauncher;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Map;

/** Compile-time stub of Forge 10.13.4.1614's IFMLLoadingPlugin. Not packaged. */
public interface IFMLLoadingPlugin {
    String[] getASMTransformerClass();

    String getModContainerClass();

    String getSetupClass();

    void injectData(Map<String, Object> data);

    String getAccessTransformerClass();

    @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.TYPE)
    @interface MCVersion { String value() default ""; }

    @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.TYPE)
    @interface Name { String value() default ""; }

    @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.TYPE)
    @interface SortingIndex { int value() default 0; }

    @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.TYPE)
    @interface TransformerExclusions { String[] value() default ""; }
}
