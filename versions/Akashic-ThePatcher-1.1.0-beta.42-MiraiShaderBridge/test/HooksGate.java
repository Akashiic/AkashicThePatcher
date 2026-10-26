import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.interfaces.render.IDhApiRenderProxy;
import com.seibel.distanthorizons.forge17.RenderHelper;

/**
 * MiraiShaderBridgeHooks.afterTranslucentLightmap against the Mirai shape: Mirai's deferred LOD pass runs only in
 * sortAndRender pass 1 and only while DH's renderProxy defers transparent rendering (Angelica's DH support active).
 */
public final class HooksGate {
    static boolean defer;

    public static void main(String[] args) throws Exception {
        final java.lang.reflect.Method hook = Class.forName("com.akashic.thepatcher.module.mirai.MiraiShaderBridgeHooks")
            .getMethod("afterTranslucentLightmap", int.class);
        final Object[][] cases = {
            // renderProxy present, defer, pass, expected calls after the case
            {false, false, 1, 0}, {true, false, 0, 0}, {true, false, 1, 0}, {true, true, 0, 0}, {true, true, 1, 1},
            {true, true, 1, 2}, {true, false, 1, 2}, {false, true, 1, 2},
        };
        final IDhApiRenderProxy proxy = new IDhApiRenderProxy() {
            @Override public void setDeferTransparentRendering(boolean value) { defer = value; }
            @Override public boolean getDeferTransparentRendering() { return defer; }
        };
        for (Object[] c : cases) {
            DhApi.Delayed.renderProxy = (Boolean) c[0] ? proxy : null;
            defer = (Boolean) c[1];
            hook.invoke(null, c[2]);
            System.out.println("A proxy=" + c[0] + " defer=" + c[1] + " pass=" + c[2] + " deferredCalls=" + RenderHelper.deferredCalls);
            if (RenderHelper.deferredCalls != (Integer) c[3]) throw new AssertionError("expected " + c[3]);
        }
        System.out.println("HOOKS_GATE PASS");
    }
}
