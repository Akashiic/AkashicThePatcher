package makamys.mixingasm.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Compile-time copy of Mixingasm's (UniMixins) marker annotation; the real one ships with UniMixins. Never shipped. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface MixinSafeTransformer {
}
