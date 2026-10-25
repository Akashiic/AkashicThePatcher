/**
 * Loads and runs the bundled transformer with NO Mixingasm/UniMixins on the
 * classpath: the RUNTIME annotation must not make the class fail to load.
 */
public final class NoMixingasmGate {
    public static void main(String[] args) throws Exception {
        final Class<?> type = Class.forName(args[0]);
        final Object transformer = type.newInstance();
        final byte[] input = { 1, 2, 3 };
        final Object out = type.getMethod("transform", String.class, String.class, byte[].class)
            .invoke(transformer, "a.B", "a.B", input);
        if (out != input) {
            throw new AssertionError("unrelated class must be returned as is");
        }
        System.out.println("NO_MIXINGASM PASS annotations=" + type.getAnnotations().length);
    }
}
