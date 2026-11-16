package org.spongepowered.asm.mixin.injection;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** Test stub of Mixin's @At (RUNTIME retention, as the real one). Never shipped. */
@Retention(RetentionPolicy.RUNTIME)
public @interface At {
    String value();

    String target() default "";

    boolean remap() default true;
}
