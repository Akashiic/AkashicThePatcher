package com.seibel.distanthorizons.forge17;

/** Compile-time shape of Mirai's forge17 RenderHelper; -Dstub.renderHelper.log=true prints each call. Never shipped. */
public class RenderHelper {
    public static int deferredCalls;

    public static void renderDeferredLods() {
        deferredCalls++;
        if (Boolean.getBoolean("stub.renderHelper.log")) {
            System.out.println("A RenderHelper.renderDeferredLods call " + deferredCalls);
        }
    }
}
