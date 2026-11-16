package org.spongepowered.asm.mixin;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** Test stub of Mixin's @Mixin (CLASS retention, as the real one). Never shipped. */
@Retention(RetentionPolicy.CLASS)
public @interface Mixin {
    Class<?>[] value() default {};

    String[] targets() default {};

    boolean remap() default true;
}
