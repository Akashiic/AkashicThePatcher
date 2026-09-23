package com.akashic.compatlab.client;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Arrays;

import com.akashic.thepatcher.module.mps.MpsAngelicaCompatTransformer;

public final class MpsAngelicaCompatFingerprintNegativeTest {
    private MpsAngelicaCompatFingerprintNegativeTest() {}

    public static void main(String[] args) throws Exception {
        System.setProperty("akashic.mpsAngelica218.forceForTests", "true");
        final byte[] input = ownClassBytes();
        final MpsAngelicaCompatTransformer transformer =
            new MpsAngelicaCompatTransformer();
        final byte[] output = transformer.transform(
            "net.machinemuse.numina.render.RenderState",
            "net.machinemuse.numina.render.RenderState",
            input);
        if (!Arrays.equals(input, output)) {
            throw new AssertionError("fingerprint mismatch changed bytes");
        }
        if (!"NUMINA_FINGERPRINT_MISMATCH".equals(
            MpsAngelicaCompatTransformer.status())) {
            throw new AssertionError(
                "unexpected status=" + MpsAngelicaCompatTransformer.status());
        }
        System.out.println(
            "MPS_ANGELICA_FINGERPRINT_NEGATIVE PASS bytesUnchanged=true");
    }

    private static byte[] ownClassBytes() throws Exception {
        final String name =
            "com/akashic/compatlab/client/MpsAngelicaCompatFingerprintNegativeTest.class";
        final InputStream stream =
            MpsAngelicaCompatFingerprintNegativeTest.class
                .getClassLoader().getResourceAsStream(name);
        if (stream == null) throw new AssertionError("self resource missing");
        try {
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            final byte[] buffer = new byte[4096];
            int read;
            while ((read = stream.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        } finally {
            stream.close();
        }
    }
}
