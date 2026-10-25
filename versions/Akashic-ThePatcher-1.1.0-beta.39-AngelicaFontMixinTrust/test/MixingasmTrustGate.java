import makamys.mixingasm.api.IMixinSafeTransformer;
import makamys.mixingasm.api.MixinSafeTransformer;

/**
 * Applies Mixingasm's own trust rule (UniMixins 0.3.1, Mixingasm line 80:
 * "trans instanceof IMixinSafeTransformer || trans.getClass().isAnnotationPresent(MixinSafeTransformer.class)",
 * besides the config patterns) to the bundled transformer, with the real UniMixins jar on the classpath.
 *
 *   java MixingasmTrustGate <class name> <expect: trusted|untrusted>
 */
public final class MixingasmTrustGate {
    public static void main(String[] args) throws Exception {
        final Object transformer = Class.forName(args[0]).newInstance();
        final boolean trusted = transformer instanceof IMixinSafeTransformer
            || transformer.getClass().isAnnotationPresent(MixinSafeTransformer.class);
        if (trusted != "trusted".equals(args[1])) {
            throw new AssertionError(args[0] + " trusted=" + trusted + ", expected " + args[1]);
        }
        System.out.println("MIXINGASM_TRUST PASS " + args[0] + " trusted=" + trusted);
    }
}
