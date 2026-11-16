package com.llamalad7.mixinextras.injector.wrapoperation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import org.spongepowered.asm.mixin.injection.At;

/** Test stub of MixinExtras' @WrapOperation (RUNTIME retention, as the real one). Never shipped. */
@Retention(RetentionPolicy.RUNTIME)
public @interface WrapOperation {
    String[] method();

    At[] at();

    int require() default -1;
}
